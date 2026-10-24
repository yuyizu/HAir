package dev.hair.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecoveryTest {
    private val today = LocalDate.of(2026, 10, 24)

    private fun day(daysAgo: Int) = today.minusDays(daysAgo.toLong())

    private fun vitals(daysAgo: Int, hrv: Double? = null, rhr: Long? = null) =
        DailyVitals(day(daysAgo), restingHr = rhr, hrvMs = hrv)

    private fun night(daysAgo: Int, asleepMin: Long) = Night(day(daysAgo), asleepMin, asleepMin + 20, 80, 90, 200, 20)

    // 近 10 天的“平时水平”：HRV 约 40 ms、静息心率 60、睡 420 分钟（偏差都小于各项的波动下限）
    private val baselineVitals = (1..10).map { vitals(it, hrv = 40.0 + (it % 3 - 1), rhr = 60L + it % 2) }
    private val baselineNights = (1..10).map { night(it, 420L + (it % 3 - 1) * 5) }

    private fun with(hrv: Double?, rhr: Long?, asleep: Long?) = recovery(
        baselineNights + listOfNotNull(asleep?.let { night(0, it) }),
        baselineVitals + vitals(0, hrv, rhr),
        today,
    )

    @Test
    fun `和平时水平一样时，天气是轻云，指数接近 0`() {
        val r = with(hrv = 40.0, rhr = 60, asleep = 420)!!

        assertEquals(Weather.FAIR, r.weather)
        assertEquals(0.0, r.index, 0.15)
        assertEquals(3, r.factors.size)
    }

    @Test
    fun `HRV 更高、静息心率更低、睡得更久，都明显好于平时时是晴天`() {
        val r = with(hrv = 50.0, rhr = 55, asleep = 500)!!

        assertEquals(Weather.SUNNY, r.weather)
        assertEquals(1.0, r.index, 1e-9)
    }

    @Test
    fun `三项都明显差于平时是阴天，分数封底为 -1`() {
        val r = with(hrv = 28.0, rhr = 68, asleep = 330)!!

        assertEquals(Weather.OVERCAST, r.weather)
        assertEquals(-1.0, r.index, 1e-9)
    }

    @Test
    fun `静息心率是越低越好，高于平时拉低指数`() {
        val higher = with(hrv = 40.0, rhr = 66, asleep = 420)!!
        val factor = higher.factors.single { it.metric == RecoveryMetric.RESTING_HR }

        assertTrue(factor.score < 0)
        assertEquals(60.5, factor.baseline, 0.6)
    }

    /** 完全没有静息心率的数据：昨天的也没有，否则它会被当作“当前值”。 */
    private val withoutRhr = baselineVitals.map { it.copy(restingHr = null) }

    @Test
    fun `缺一项时按剩余项的权重归一`() {
        // HRV 满分 +1，睡眠满分差 -1，静息心率缺：(0.4 * 1 - 0.3 * 1) / 0.7
        val r = recovery(baselineNights + night(0, 330), withoutRhr + vitals(0, hrv = 50.0), today)!!

        assertEquals(setOf(RecoveryMetric.HRV, RecoveryMetric.SLEEP), r.factors.map { it.metric }.toSet())
        assertEquals((0.4 - 0.3) / 0.7, r.index, 1e-9)
    }

    @Test
    fun `只剩一项可用时不给结论`() {
        assertNull(recovery(emptyList(), withoutRhr + vitals(0, hrv = 50.0), today))
    }

    @Test
    fun `基线不足 4 天的指标不参与`() {
        val shortHrv = (1..3).map { vitals(it, hrv = 40.0) } + vitals(0, hrv = 50.0)
        val rhrAndSleep = (1..10).map { vitals(it, rhr = 60) } + vitals(0, rhr = 60)

        val r = recovery(baselineNights + night(0, 420), shortHrv.mergedWith(rhrAndSleep), today)!!

        assertEquals(setOf(RecoveryMetric.RESTING_HR, RecoveryMetric.SLEEP), r.factors.map { it.metric }.toSet())
    }

    @Test
    fun `只有最近 28 天内的日子算基线`() {
        val old = (29..40).map { night(it, 600) }
        val recent = (1..3).map { night(it, 420) } + night(0, 420)
        val rhr = (1..10).map { vitals(it, rhr = 60) } + vitals(0, rhr = 60)

        // 睡眠窗口内只有 3 天基线，不够；静息心率够，但只剩 1 项
        assertNull(recovery(old + recent, rhr, today))
    }

    @Test
    fun `当前值取今天或昨天里最新的，更早的不当作当前`() {
        val stale = baselineVitals.drop(2) + vitals(2, hrv = 50.0, rhr = 55)
        val staleNights = baselineNights.drop(2) + night(2, 500)

        assertNull(recovery(staleNights, stale, today))
    }

    @Test
    fun `今天还没有数据时，用昨天的当前值，基线不含昨天自己`() {
        val vitalsYesterday = baselineVitals.drop(1) + vitals(1, hrv = 50.0, rhr = 55)
        val nightsYesterday = baselineNights.drop(1) + night(1, 500)

        val r = recovery(nightsYesterday, vitalsYesterday, today)!!

        assertEquals(day(1), r.factors.first().date)
        assertEquals(Weather.SUNNY, r.weather)
    }

    @Test
    fun `皮温偏离不小于自己的波动幅度时标为明显`() {
        val calm = DailyVitals(day(0), skinTempDeltaC = 0.3, skinTempSpreadC = 0.6)
        val warm = DailyVitals(day(0), skinTempDeltaC = 0.7, skinTempSpreadC = 0.6)
        val cold = DailyVitals(day(0), skinTempDeltaC = -0.6, skinTempSpreadC = 0.6)

        assertFalse(skinTempSignal(listOf(calm), today)!!.notable)
        assertTrue(skinTempSignal(listOf(warm), today)!!.notable)
        assertTrue(skinTempSignal(listOf(cold), today)!!.notable)
    }

    @Test
    fun `没有波动幅度时偏离不标明显；两天前的皮温数据不取`() {
        val noSpread = DailyVitals(day(0), skinTempDeltaC = 2.0)
        val old = DailyVitals(day(2), skinTempDeltaC = 2.0, skinTempSpreadC = 0.5)

        assertFalse(skinTempSignal(listOf(noSpread), today)!!.notable)
        assertNull(skinTempSignal(listOf(old), today))
    }

    private fun List<DailyVitals>.mergedWith(other: List<DailyVitals>): List<DailyVitals> =
        (this + other).groupBy { it.date }.map { (_, days) ->
            DailyVitals(
                days.first().date,
                restingHr = days.firstNotNullOfOrNull { it.restingHr },
                hrvMs = days.firstNotNullOfOrNull { it.hrvMs },
            )
        }.sortedBy { it.date }


    @Test
    fun `数值明显低于基线是偏低，明显高于基线是偏高，接近基线在常态内`() {
        val r = with(hrv = 25.0, rhr = 70, asleep = 420)!!
        val byMetric = r.factors.associateBy { it.metric }

        assertEquals(Deviation.LOW, byMetric.getValue(RecoveryMetric.HRV).deviation)
        assertEquals(Deviation.HIGH, byMetric.getValue(RecoveryMetric.RESTING_HR).deviation)
        assertEquals(Deviation.IN_RANGE, byMetric.getValue(RecoveryMetric.SLEEP).deviation)
    }
}
