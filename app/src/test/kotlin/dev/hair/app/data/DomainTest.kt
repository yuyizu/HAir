package dev.hair.app.data

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 样例 JSON 按 Discovery 文档的字段定义手写（int64 为字符串、枚举为大写字符串）。 */
class DomainTest {
    private val sleepJson = """
        {"dataPoints":[{"sleep":{
          "interval":{"startTime":"2026-10-19T14:30:00Z","endTime":"2026-10-19T22:10:00Z",
                      "civilEndTime":{"date":{"year":2026,"month":10,"day":20},"time":{"hours":6,"minutes":10}}},
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
    fun `缺少醒来日期的睡眠记录不映射`() {
        val point = DataPoint(sleep = Sleep(summary = SleepSummary(minutesAsleep = "300")))
        assertNull(point.toNight())
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
}
