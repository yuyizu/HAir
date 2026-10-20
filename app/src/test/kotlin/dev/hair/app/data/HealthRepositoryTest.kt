package dev.hair.app.data

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate

/** 用拦截器伪造 Google Health API 的响应，驱动真实的 HealthApi / HealthRepository。 */
class HealthRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val today = LocalDate.of(2026, 10, 20)

    private fun sleepJson(endDay: Int) = """{"dataPoints":[{"sleep":{
        "interval":{"civilEndTime":{"date":{"year":2026,"month":10,"day":$endDay}}},
        "metadata":{"mainSleep":true},
        "summary":{"minutesAsleep":"420","minutesInSleepPeriod":"460","minutesAwake":"40",
                   "stagesSummary":[{"type":"DEEP","minutes":"80"}]}}}]}"""

    private val rhrJson = """{"dataPoints":[{"dailyRestingHeartRate":
        {"date":{"year":2026,"month":10,"day":19},"beatsPerMinute":"58"}}]}"""

    private val stepsJson = """{"rollupDataPoints":[{"steps":{"countSum":"1234"}}]}"""

    private fun client(sleep: String, status: Int = 200) = OkHttpClient.Builder()
        .addInterceptor(Interceptor { chain ->
            val path = chain.request().url.encodedPath
            val body = when {
                status != 200 -> """{"error":"boom"}"""
                "/dataTypes/sleep/" in path -> sleep
                "/dataTypes/daily-resting-heart-rate/" in path -> rhrJson
                "/dataTypes/steps/" in path -> stepsJson
                else -> "{}"
            }
            Response.Builder()
                .request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(status).message("test")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        })
        .build()

    private fun repository(client: OkHttpClient, store: SnapshotStore) =
        HealthRepository(HealthApi(accessToken = { "token" }, client = client), store)

    private fun newStore() = SnapshotStore(File(tmp.root, "snapshot.json"))

    @Test
    fun `refresh 汇总各数据类型并写入缓存`() = runBlocking {
        val store = newStore()
        val snapshot = repository(client(sleepJson(19)), store).refresh(today)

        val o = snapshot.overview
        assertEquals(LocalDate.of(2026, 10, 19), o.lastNight?.date)
        assertEquals(420L, o.lastNight?.asleepMin)
        assertEquals(58L, o.restingHr)
        assertEquals(1234L, o.stepsToday)
        assertNull(o.hrvMs)
        assertNull(o.latestHr)
        assertNull(o.cardioFitness)
        assertEquals(snapshot, store.load())
    }

    @Test
    fun `三天前的睡眠不算昨晚，但仍在睡眠列表里`() = runBlocking {
        val snapshot = repository(client(sleepJson(10)), newStore()).refresh(today)
        assertNull(snapshot.overview.lastNight)
        assertEquals(listOf(LocalDate.of(2026, 10, 10)), snapshot.nights.map { it.date })
    }

    @Test
    fun `刷新失败时抛出异常且保留旧缓存`() {
        val store = newStore()
        val good = runBlocking { repository(client(sleepJson(19)), store).refresh(today) }

        val failing = repository(client(sleepJson(19), status = 500), store)
        val e = assertThrows(HealthApiException::class.java) { runBlocking { failing.refresh(today) } }

        assertEquals(500, e.code)
        assertEquals(good, store.load())
    }
}
