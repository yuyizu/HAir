package dev.hair.app.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** 翻页和汇总：检查发出的 URL、请求体，以及按页拼接的结果。 */
class HealthApiTest {
    private class Sent(val url: HttpUrl, val body: String)

    private val sent = mutableListOf<Sent>()

    /** 按顺序逐次返回 [pages]；请求次数超过页数会抛异常，等于断言没有多余的请求。 */
    private fun api(vararg pages: String): HealthApi {
        val queue = ArrayDeque(pages.toList())
        val client = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            val request = chain.request()
            sent += Sent(request.url, Buffer().also { request.body?.writeTo(it) }.readUtf8())
            Response.Builder()
                .request(request).protocol(Protocol.HTTP_1_1).code(200).message("test")
                .body(queue.removeFirst().toResponseBody("application/json".toMediaType()))
                .build()
        }).build()
        return HealthApi({ "token" }, client)
    }

    private fun page(vararg bpm: Int, next: String? = null): String {
        val points = bpm.joinToString(",") { """{"dailyRestingHeartRate":{"beatsPerMinute":"$it"}}""" }
        return """{"dataPoints":[$points]${next?.let { ""","nextPageToken":"$it"""" }.orEmpty()}}"""
    }

    private fun List<DataPoint>.bpm() = map { it.dailyRestingHeartRate?.beatsPerMinute }

    @Test
    fun `单页放不下时按 nextPageToken 继续取，并带上 pageToken`() = runBlocking {
        val points = api(page(61, 62, next = "t1"), page(63)).list("daily-resting-heart-rate", "f", limit = 5)

        assertEquals(listOf("61", "62", "63"), points.bpm())
        assertEquals(2, sent.size)
        assertEquals("5", sent[0].url.queryParameter("pageSize"))
        assertNull(sent[0].url.queryParameter("pageToken"))
        assertEquals("3", sent[1].url.queryParameter("pageSize"))
        assertEquals("t1", sent[1].url.queryParameter("pageToken"))
    }

    @Test
    fun `取够 limit 条就停，不再请求下一页`() = runBlocking {
        val points = api(page(61, 62, next = "t1")).list("daily-resting-heart-rate", "f", limit = 2)

        assertEquals(listOf("61", "62"), points.bpm())
        assertEquals(1, sent.size)
    }

    @Test
    fun `服务端不理会 pageSize 多给了数据，也只返回 limit 条`() = runBlocking {
        val points = api(page(61, 62, 63, 64, 65)).list("daily-resting-heart-rate", "f", limit = 3)

        assertEquals(listOf("61", "62", "63"), points.bpm())
    }

    @Test
    fun `汇总请求带数据类型、窗口秒数和左闭右开区间`() = runBlocking {
        val api = api(
            """{"rollupDataPoints":[{"startTime":"2026-10-23T13:20:00Z","endTime":"2026-10-23T13:25:00Z",
                "heartRate":{"beatsPerMinuteMin":52,"beatsPerMinuteAvg":55.5,"beatsPerMinuteMax":71}}]}""",
        )

        val buckets = api.rollUp(
            "heart-rate", Duration.ofMinutes(5),
            Instant.parse("2026-10-23T13:20:00Z"), Instant.parse("2026-10-23T14:20:00Z"),
        )

        val call = sent.single()
        assertEquals("/v4/users/me/dataTypes/heart-rate/dataPoints:rollUp", call.url.encodedPath)
        assertEquals(
            RollUpRequest("300s", TimeInterval("2026-10-23T13:20:00Z", "2026-10-23T14:20:00Z")),
            HealthJson.decodeFromString<RollUpRequest>(call.body),
        )
        assertEquals(
            listOf(HeartBucket(Instant.parse("2026-10-23T13:20:00Z").epochSecond, 52.0, 55.5, 71.0)),
            buckets.mapNotNull { it.toHeartBucket() },
        )
    }
}
