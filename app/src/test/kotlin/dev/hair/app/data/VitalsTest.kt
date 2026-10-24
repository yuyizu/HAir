package dev.hair.app.data

import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** 样例 JSON 取自 2026-10-24 的 Fitbit Air 真机响应（只删掉了与映射无关的 dataSource 等字段）。 */
class VitalsTest {
    private fun points(json: String) = HealthJson.decodeFromString<ListResponse>(json).dataPoints

    private val restingHr = points(
        """{"dataPoints":[{"dailyRestingHeartRate":{"date":{"year":2026,"month":10,"day":24},"beatsPerMinute":"84"}}]}""",
    )
    private val hrv = points(
        """{"dataPoints":[{"dailyHeartRateVariability":{"date":{"year":2026,"month":10,"day":24},
            "averageHeartRateVariabilityMilliseconds":21.049,"nonRemHeartRateBeatsPerMinute":"84","entropy":2.729}}]}""",
    )
    private val steps = HealthJson.decodeFromString<DailyRollUpResponse>(
        """{"rollupDataPoints":[{"civilStartTime":{"date":{"year":2026,"month":10,"day":24},"time":{}},"steps":{"countSum":"711"}}]}""",
    ).rollupDataPoints
    private val temperature = points(
        """{"dataPoints":[{"dailySleepTemperatureDerivations":{"date":{"year":2026,"month":10,"day":24},
            "nightlyTemperatureCelsius":31.980378250591,"baselineTemperatureCelsius":32.021033281139651,
            "relativeNightlyStddev30dCelsius":0.59654841602011521}}]}""",
    )
    private val spo2 = points(
        """{"dataPoints":[{"dailyOxygenSaturation":{"date":{"year":2026,"month":10,"day":23},
            "averagePercentage":96.9,"lowerBoundPercentage":94,"upperBoundPercentage":99.8}}]}""",
    )
    private val respiration = points(
        """{"dataPoints":[{"dailyRespiratoryRate":{"date":{"year":2026,"month":10,"day":24},"breathsPerMinute":15.2}}]}""",
    )

    @Test
    fun `各类日汇总按日期合并，日期升序`() {
        val vitals = buildVitals(restingHr, hrv, steps, temperature, spo2, respiration)

        assertEquals(listOf(LocalDate.of(2026, 10, 23), LocalDate.of(2026, 10, 24)), vitals.map { it.date })
        val last = vitals.last()
        assertEquals(84L, last.restingHr)
        assertEquals(21.049, last.hrvMs!!, 0.0)
        assertEquals(711L, last.steps)
        assertEquals(31.980378250591 - 32.021033281139651, last.skinTempDeltaC!!, 1e-12)
        assertEquals(0.59654841602011521, last.skinTempSpreadC!!, 0.0)
        assertEquals(15.2, last.respRate!!, 0.0)
        assertNull(last.spo2Avg)
        // 血氧的日期是 10-23，不会被并到 10-24
        assertEquals(96.9, vitals.first().spo2Avg!!, 0.0)
        assertEquals(94.0, vitals.first().spo2Min!!, 0.0)
    }

    @Test
    fun `接口返回的 NaN 视为缺失值：没有基线就没有皮温偏离`() {
        // 刚开始戴的几天没有个人基线，真机响应里这一项是字面量 NaN
        val noBaseline = points(
            """{"dataPoints":[{"dailySleepTemperatureDerivations":{"date":{"year":2026,"month":10,"day":22},
                "nightlyTemperatureCelsius":31.5,"baselineTemperatureCelsius":NaN,"relativeNightlyStddev30dCelsius":NaN}}]}""",
        )

        val day = buildVitals(emptyList(), emptyList(), emptyList(), noBaseline, emptyList(), emptyList()).single()

        assertNull(day.skinTempDeltaC)
        assertNull(day.skinTempSpreadC)
    }

    @Test
    fun `缺日期的数据点被丢弃，缺字段的日期仍保留其余字段`() {
        val noDate = points("""{"dataPoints":[{"dailyOxygenSaturation":{"averagePercentage":97}}]}""")
        val noBaseline = points(
            """{"dataPoints":[{"dailySleepTemperatureDerivations":{"date":{"year":2026,"month":10,"day":24},
                "nightlyTemperatureCelsius":32.0,"relativeNightlyStddev30dCelsius":0.5}}]}""",
        )

        val vitals = buildVitals(emptyList(), emptyList(), emptyList(), noBaseline, noDate, emptyList())

        val day = vitals.single()
        assertEquals(LocalDate.of(2026, 10, 24), day.date)
        assertNull(day.skinTempDeltaC)
        assertEquals(0.5, day.skinTempSpreadC!!, 0.0)
    }
}
