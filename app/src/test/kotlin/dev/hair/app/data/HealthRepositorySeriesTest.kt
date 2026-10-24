package dev.hair.app.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/** 详情页的时间序列：检查发给 Google Health API 的路径、请求体，以及响应到 [Series] 的映射。 */
class HealthRepositorySeriesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val today = LocalDate.of(2026, 10, 20)
    private val shanghai = ZoneId.of("Asia/Shanghai")

    private class Sent(val path: String, val body: String, val filter: String?)

    private val sent = mutableListOf<Sent>()

    /** 每个请求的响应由 [respond] 按路径决定。 */
    private fun repository(status: Int = 200, respond: (path: String) -> String): HealthRepository {
        val client = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            val request = chain.request()
            val buffer = Buffer().also { request.body?.writeTo(it) }
            val path = request.url.encodedPath
            sent += Sent(path, buffer.readUtf8(), request.url.queryParameter("filter"))
            Response.Builder()
                .request(request).protocol(Protocol.HTTP_1_1).code(status).message("test")
                .body(respond(path).toResponseBody("application/json".toMediaType()))
                .build()
        }).build()
        return HealthRepository(HealthApi({ "token" }, client), SnapshotStore(File(tmp.root, "snapshot.json")))
    }

    private fun repository(responseBody: String, status: Int = 200) = repository(status) { responseBody }

    @Test
    fun `日档请求当天本地零点到次日零点的小时汇总`() = runBlocking {
        val repo = repository(
            """{"rollupDataPoints":[{"startTime":"2026-10-20T01:00:00Z","endTime":"2026-10-20T02:00:00Z","steps":{"countSum":"250"}}]}""",
        )

        val s = repo.series(Metric.STEPS, DetailRange.DAY, today, shanghai)

        val call = sent.single()
        assertEquals("/v4/users/me/dataTypes/steps/dataPoints:rollUp", call.path)
        assertEquals(
            RollUpRequest("3600s", TimeInterval("2026-10-19T16:00:00Z", "2026-10-20T16:00:00Z")),
            HealthJson.decodeFromString<RollUpRequest>(call.body),
        )
        assertEquals(250.0, s.values[9], 0.0)
    }

    @Test
    fun `周档按天汇总，区间含今天，右端是明天`() = runBlocking {
        val repo = repository(
            """{"rollupDataPoints":[{"civilStartTime":{"date":{"year":2026,"month":10,"day":20}},"steps":{"countSum":"8123"}}]}""",
        )

        val s = repo.series(Metric.STEPS, DetailRange.WEEK, today, shanghai)

        val call = sent.single()
        assertEquals("/v4/users/me/dataTypes/steps/dataPoints:dailyRollUp", call.path)
        val request = HealthJson.decodeFromString<DailyRollUpRequest>(call.body)
        assertEquals(Date(2026, 10, 14), request.range.start.date)
        assertEquals(Date(2026, 10, 21), request.range.end.date)
        assertEquals(1, request.windowSizeDays)
        assertEquals(8123.0, s.values.last(), 0.0)
    }

    @Test
    fun `活动能量走 active-energy-burned 数据类型`() = runBlocking {
        val repo = repository("""{"rollupDataPoints":[{"startTime":"2026-10-19T16:00:00Z","activeEnergyBurned":{"kcalSum":12.5}}]}""")

        val s = repo.series(Metric.ACTIVE_ENERGY, DetailRange.DAY, today, shanghai)

        assertEquals("/v4/users/me/dataTypes/active-energy-burned/dataPoints:rollUp", sent.single().path)
        assertEquals(12.5, s.values[0], 0.0)
    }

    private fun night(vararg stages: StageSegment) = Night(LocalDate.of(2026, 10, 24), 400, 423, 73, 99, 228, 23, stages.toList())

    private fun sec(iso: String) = java.time.Instant.parse(iso).epochSecond

    @Test
    fun `夜间细节：心率按 5 分钟汇总，血氧取分期窗口内的采样并升序`() = runBlocking {
        val repo = repository { path ->
            if (path.endsWith(":rollUp")) {
                """{"rollupDataPoints":[
                    {"startTime":"2026-10-23T13:25:00Z","heartRate":{"beatsPerMinuteMin":50,"beatsPerMinuteAvg":54,"beatsPerMinuteMax":60}},
                    {"startTime":"2026-10-23T13:20:00Z","heartRate":{"beatsPerMinuteMin":52,"beatsPerMinuteAvg":58.5,"beatsPerMinuteMax":71}},
                    {"startTime":"2026-10-23T13:30:00Z"}]}"""
            } else {
                """{"dataPoints":[
                    {"oxygenSaturation":{"sampleTime":{"physicalTime":"2026-10-23T19:15:04Z"},"percentage":95.5}},
                    {"oxygenSaturation":{"sampleTime":{"physicalTime":"2026-10-23T19:14:04Z"},"percentage":96}}]}"""
            }
        }
        val stages = arrayOf(
            StageSegment(SleepStage.AWAKE, sec("2026-10-23T13:21:00Z"), sec("2026-10-23T13:34:00Z")),
            StageSegment(SleepStage.LIGHT, sec("2026-10-23T13:34:00Z"), sec("2026-10-23T20:24:00Z")),
        )

        val detail = repo.nightDetail(night(*stages))!!

        // 没有心率的窗口被丢弃，其余按时间升序
        assertEquals(listOf(58.5, 54.0), detail.heart.map { it.avgBpm })
        assertEquals(listOf(96.0, 95.5), detail.spo2.map { it.percent })
        val roll = sent.single { it.path.endsWith(":rollUp") }
        assertEquals("/v4/users/me/dataTypes/heart-rate/dataPoints:rollUp", roll.path)
        assertEquals(
            RollUpRequest("300s", TimeInterval("2026-10-23T13:21:00Z", "2026-10-23T20:24:00Z")),
            HealthJson.decodeFromString<RollUpRequest>(roll.body),
        )
        assertEquals(
            "oxygen_saturation.sample_time.physical_time >= \"2026-10-23T13:21:00Z\" AND " +
                "oxygen_saturation.sample_time.physical_time < \"2026-10-23T20:24:00Z\"",
            sent.single { it.path.endsWith("/oxygen-saturation/dataPoints") }.filter,
        )
    }

    @Test
    fun `没有分期的睡眠没有夜间细节，也不发请求`() = runBlocking {
        val repo = repository("{}")

        assertEquals(null, repo.nightDetail(night()))
        assertEquals(0, sent.size)
    }

    @Test
    fun `接口失败时抛出异常而不是返回空序列`() {
        val repo = repository("""{"error":"boom"}""", status = 403)

        val e = assertThrows(HealthApiException::class.java) {
            runBlocking { repo.series(Metric.ACTIVE_ENERGY, DetailRange.MONTH, today, shanghai) }
        }

        assertEquals(403, e.code)
    }
}
