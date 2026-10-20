package dev.hair.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class SeriesTest {
    private val today = LocalDate.of(2026, 10, 20)

    private fun daily(day: Int, steps: String? = null, kcal: Double? = null, month: Int = 10) = DailyRollupDataPoint(
        civilStartTime = CivilDateTime(Date(2026, month, day)),
        steps = steps?.let(::StepsRollupValue),
        activeEnergyBurned = kcal?.let(::EnergyRollupValue),
    )

    private fun hourly(start: String, steps: String? = null, kcal: Double? = null) = RollupDataPoint(
        startTime = start,
        steps = steps?.let(::StepsRollupValue),
        activeEnergyBurned = kcal?.let(::EnergyRollupValue),
    )

    @Test
    fun `周档从六天前排到今天，缺的天补 0`() {
        val s = dailySeries(DetailRange.WEEK, today, listOf(daily(14, "100"), daily(20, "700")), Metric.STEPS)

        assertEquals(LocalDate.of(2026, 10, 14), s.start)
        assertEquals(listOf(100.0, 0.0, 0.0, 0.0, 0.0, 0.0, 700.0), s.values)
    }

    @Test
    fun `月档恰好 30 个桶，窗口外的日期被忽略`() {
        val s = dailySeries(DetailRange.MONTH, today, listOf(daily(20, "5"), daily(21, "7", month = 9), daily(20, "9", month = 9)), Metric.STEPS)

        assertEquals(30, s.values.size)
        assertEquals(LocalDate.of(2026, 9, 21), s.start)
        assertEquals(7.0, s.values.first(), 0.0)
        assertEquals(5.0, s.values.last(), 0.0)
        assertEquals(12.0, s.total, 0.0)
    }

    @Test
    fun `活动能量取 kcalSum，没有该字段的点算 0`() {
        val s = dailySeries(DetailRange.WEEK, today, listOf(daily(19, kcal = 312.5), daily(20, steps = "10")), Metric.ACTIVE_ENERGY)

        assertEquals(312.5, s.values[5], 0.0)
        assertEquals(0.0, s.values[6], 0.0)
    }

    @Test
    fun `日档按所在时区的钟点落桶`() {
        val shanghai = ZoneId.of("Asia/Shanghai")
        // 上海 10-20 00:00 = 10-19T16:00Z；09:00 = 01:00Z
        val s = hourlySeries(
            today,
            listOf(hourly("2026-10-19T16:00:00Z", "30"), hourly("2026-10-20T01:00:00Z", "400"), hourly("2026-10-20T01:00:00Z", "1")),
            shanghai,
            Metric.STEPS,
        )

        assertEquals(24, s.values.size)
        assertEquals(30.0, s.values[0], 0.0)
        assertEquals(401.0, s.values[9], 0.0)
        assertEquals(431.0, s.total, 0.0)
    }

    @Test
    fun `夏令时回拨当天重复的那一小时累加进同一桶，不丢数据`() {
        val ny = ZoneId.of("America/New_York")
        // 2026-11-01 回拨：05:00Z 是 01:00 EDT，06:00Z 是 01:00 EST
        val s = hourlySeries(
            LocalDate.of(2026, 11, 1),
            listOf(hourly("2026-11-01T05:00:00Z", "10"), hourly("2026-11-01T06:00:00Z", "20")),
            ny,
            Metric.STEPS,
        )

        assertEquals(30.0, s.values[1], 0.0)
        assertEquals(30.0, s.total, 0.0)
    }

    @Test
    fun `日均只在有记录的天上计算，空白天不拉低`() {
        val s = Series(DetailRange.WEEK, today.minusDays(6), listOf(0.0, 10.0, 30.0, 0.0, 20.0, 0.0, 0.0))

        assertEquals(60.0, s.total, 0.0)
        assertEquals(3, s.recordedCount)
        assertEquals(20.0, s.average, 0.0)
        assertEquals(2, s.peakIndex)
    }

    @Test
    fun `全为 0 时没有最高桶，日均为 0`() {
        val s = Series(DetailRange.DAY, today, List(24) { 0.0 })

        assertNull(s.peakIndex)
        assertEquals(0, s.recordedCount)
        assertEquals(0.0, s.average, 0.0)
    }
}
