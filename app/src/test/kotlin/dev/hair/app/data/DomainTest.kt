package dev.hair.app.data

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 样例 JSON 按 Discovery 文档的字段定义手写（int64 为字符串、枚举为大写字符串）。
 * 睡眠的 interval 取自 2026-10-24 的真机响应：只有起止时刻及其 UTC 偏移，没有 civilEndTime。
 */
class DomainTest {
    private val sleepJson = """
        {"dataPoints":[{"sleep":{
          "interval":{"startTime":"2026-10-19T14:30:00Z","startUtcOffset":"28800s",
                      "endTime":"2026-10-19T22:10:00Z","endUtcOffset":"28800s"},
          "type":"STAGES",
          "metadata":{"mainSleep":true,"nap":false},
          "summary":{"minutesAsleep":"420","minutesAwake":"40","minutesInSleepPeriod":"460",
                     "stagesSummary":[{"type":"DEEP","minutes":"80","count":"3"},{"type":"REM","minutes":"95"},
                                      {"type":"LIGHT","minutes":"245"},{"type":"AWAKE","minutes":"40"}]}}}],
         "nextPageToken":"abc"}
    """.trimIndent()

    @Test
    fun `睡眠数据点映射为 Night，int64 字符串被正确解析`() {
        val point = HealthJson.decodeFromString<ListResponse>(sleepJson).dataPoints.single()
        val night = point.toNight()!!
        assertEquals(LocalDate.of(2026, 10, 20), night.date)
        assertEquals(420L, night.asleepMin)
        assertEquals(460L, night.inBedMin)
        assertEquals(80L, night.deepMin)
        assertEquals(95L, night.remMin)
        assertEquals(245L, night.lightMin)
        assertEquals(40L, night.awakeMin)
    }

    @Test
    fun `分期按时间升序映射为时间段，不认识的类型被丢弃`() {
        val json = """{"dataPoints":[{"sleep":{
          "interval":{"startTime":"2026-10-23T13:21:00Z","startUtcOffset":"28800s",
                      "endTime":"2026-10-23T20:24:00Z","endUtcOffset":"28800s"},
          "type":"STAGES",
          "stages":[
            {"startTime":"2026-10-23T13:34:00Z","startUtcOffset":"28800s","endTime":"2026-10-23T13:47:00Z","endUtcOffset":"28800s","type":"LIGHT"},
            {"startTime":"2026-10-23T13:21:00Z","startUtcOffset":"28800s","endTime":"2026-10-23T13:34:00Z","endUtcOffset":"28800s","type":"AWAKE"},
            {"startTime":"2026-10-23T13:47:00Z","endTime":"2026-10-23T14:15:00Z","type":"ASLEEP"}],
          "metadata":{"mainSleep":true},
          "summary":{"minutesAsleep":"400","minutesAwake":"23","minutesInSleepPeriod":"423","stagesSummary":[]}}}]}"""

        val night = HealthJson.decodeFromString<ListResponse>(json).dataPoints.single().toNight()!!

        val t = { s: String -> java.time.Instant.parse(s).epochSecond }
        assertEquals(
            listOf(
                StageSegment(SleepStage.AWAKE, t("2026-10-23T13:21:00Z"), t("2026-10-23T13:34:00Z")),
                StageSegment(SleepStage.LIGHT, t("2026-10-23T13:34:00Z"), t("2026-10-23T13:47:00Z")),
            ),
            night.stages,
        )
        assertEquals(t("2026-10-23T13:21:00Z") to t("2026-10-23T13:47:00Z"), night.windowSec)
    }

    @Test
    fun `没有分期的睡眠没有起止窗口`() {
        val point = HealthJson.decodeFromString<ListResponse>(sleepJson).dataPoints.single()
        assertNull(point.toNight()!!.windowSec)
    }

    @Test
    fun `醒来日期按记录自带的 UTC 偏移换算，不取 UTC 日期`() {
        val interval = SessionInterval(endTime = "2026-10-20T03:00:00Z", endUtcOffset = "-18000s")
        val point = DataPoint(sleep = Sleep(interval = interval, summary = SleepSummary(minutesAsleep = "300")))
        assertEquals(LocalDate.of(2026, 10, 19), point.toNight()!!.date)
    }

    @Test
    fun `缺少醒来时刻或 UTC 偏移的睡眠记录不映射`() {
        val summary = SleepSummary(minutesAsleep = "300")
        val noInterval = Sleep(summary = summary)
        val noOffset = Sleep(interval = SessionInterval(endTime = "2026-10-20T03:00:00Z"), summary = summary)
        val noEnd = Sleep(interval = SessionInterval(endUtcOffset = "28800s"), summary = summary)
        assertNull(DataPoint(sleep = noInterval).toNight())
        assertNull(DataPoint(sleep = noOffset).toNight())
        assertNull(DataPoint(sleep = noEnd).toNight())
    }

    @Test
    fun `小睡被排除，未标注的视为主睡眠`() {
        assertTrue(Sleep(metadata = SleepMetadata(mainSleep = true, nap = false)).isMainSleep())
        assertTrue(Sleep().isMainSleep())
        assertEquals(false, Sleep(metadata = SleepMetadata(nap = true)).isMainSleep())
    }

    @Test
    fun `步数汇总请求按左闭右开区间序列化`() {
        val body = HealthJson.encodeToString(
            DailyRollUpRequest(
                CivilRange(
                    CivilDateTime(Date(2026, 10, 20), TimeOfDay()),
                    CivilDateTime(Date(2026, 10, 21), TimeOfDay()),
                ),
            ),
        )
        assertEquals(
            """{"range":{"start":{"date":{"year":2026,"month":10,"day":20},"time":{"hours":0,"minutes":0,"seconds":0,"nanos":0}},"end":{"date":{"year":2026,"month":10,"day":21},"time":{"hours":0,"minutes":0,"seconds":0,"nanos":0}}},"windowSizeDays":1}""",
            body,
        )
    }

    @Test
    fun `忽略未知字段并解析汇总步数`() {
        val json = """{"rollupDataPoints":[{"civilStartTime":{"date":{"year":2026,"month":10,"day":20}},"steps":{"countSum":"8123"},"x":1}]}"""
        val resp = HealthJson.decodeFromString<DailyRollUpResponse>(json)
        assertEquals("8123", resp.rollupDataPoints.single().steps?.countSum)
    }

    @Test
    fun `每日 VO2max 映射为心肺适能，等级取自 cardioFitnessLevel`() {
        val json = """{"dataPoints":[{"dailyVo2Max":{"date":{"year":2026,"month":10,"day":19},
            "vo2Max":42.5,"estimated":true,"cardioFitnessLevel":"VERY_GOOD"}}]}"""
        val c = HealthJson.decodeFromString<ListResponse>(json).dataPoints.single().toCardioFitness()!!
        assertEquals(LocalDate.of(2026, 10, 19), c.date)
        assertEquals(42.5, c.vo2Max, 0.0)
        assertEquals(CardioLevel.VERY_GOOD, c.level)
    }

    @Test
    fun `UNSPECIFIED 等级视为未给出等级，缺 VO2max 或日期则不映射`() {
        val date = Date(2026, 10, 19)
        val unspecified = DataPoint(dailyVo2Max = DailyVo2Max(date, 40.0, "CARDIO_FITNESS_LEVEL_UNSPECIFIED"))
        assertNull(unspecified.toCardioFitness()!!.level)
        assertNull(DataPoint(dailyVo2Max = DailyVo2Max(date, null, "GOOD")).toCardioFitness())
        assertNull(DataPoint(dailyVo2Max = DailyVo2Max(null, 40.0, "GOOD")).toCardioFitness())
        assertNull(DataPoint().toCardioFitness())
    }
}
