package dev.hair.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CompanionTest {
    private val today = LocalDate.of(2026, 10, 24)
    private val goals = Goals(steps = 8_000, sleepMin = 480)

    private fun day(daysAgo: Int) = today.minusDays(daysAgo.toLong())

    private fun night(daysAgo: Int, asleep: Long) = Night(day(daysAgo), asleep, asleep + 20, 80, 90, 200, 20)

    private fun steps(daysAgo: Int, count: Long) = DailyVitals(day(daysAgo), steps = count)

    @Test
    fun `成长值是近 30 天睡眠达标天数加步数达标天数`() {
        val nights = listOf(night(1, 480), night(2, 500), night(3, 479))
        val vitals = listOf(steps(0, 8_000), steps(1, 9_000), steps(2, 7_999), steps(3, 12_000))

        val c = companion(nights, vitals, goals, today)

        assertEquals(2, c.sleepDays)
        assertEquals(3, c.stepDays)
        assertEquals(5, c.growth)
    }

    @Test
    fun `窗口外的日子不算，今天还没走完的步数照实算`() {
        val nights = listOf(night(30, 600), night(29, 600))
        val vitals = listOf(steps(30, 20_000), steps(0, 3_000))

        val c = companion(nights, vitals, goals, today)

        assertEquals(1, c.sleepDays)
        assertEquals(0, c.stepDays)
    }

    @Test
    fun `目标变高后同样的数据成长会回落`() {
        val vitals = listOf(steps(0, 8_000), steps(1, 8_000))

        assertEquals(2, companion(emptyList(), vitals, goals, today).growth)
        assertEquals(0, companion(emptyList(), vitals, Goals(steps = 10_000, sleepMin = 480), today).growth)
    }

    @Test
    fun `阶段按成长值分段：6 发芽、16 成苗、32 开花`() {
        fun stageAt(growth: Int): CompanionStage {
            val stepDays = minOf(growth, 30)
            val sleepDays = growth - stepDays
            val vitals = List(stepDays) { steps(it, 9_000) }
            val nights = List(sleepDays) { night(it, 500) }
            return companion(nights, vitals, goals, today).stage
        }

        val expected = listOf(
            0 to CompanionStage.SEED, 5 to CompanionStage.SEED,
            6 to CompanionStage.SPROUT, 15 to CompanionStage.SPROUT,
            16 to CompanionStage.SAPLING, 31 to CompanionStage.SAPLING,
            32 to CompanionStage.BLOOM, 60 to CompanionStage.BLOOM,
        )
        expected.forEach { (growth, stage) -> assertEquals("成长值 $growth", stage, stageAt(growth)) }
    }

    @Test
    fun `年度图：从旧到新，超目标封顶，缺数据为 0`() {
        val nights = listOf(night(0, 720), night(2, 240))
        val vitals = listOf(DailyVitals(day(0), steps = 20_000, restingHr = 61), DailyVitals(day(2), steps = 4_000))

        val days = artDays(nights, vitals, goals, today, days = 3)

        assertEquals(listOf(day(2), day(1), day(0)), days.map { it.date })
        assertEquals(0.5f, days[0].sleepFraction, 1e-6f)
        assertEquals(0.5f, days[0].stepFraction, 1e-6f)
        assertEquals(0f, days[1].sleepFraction, 0f)
        assertEquals(null, days[1].restingHr)
        assertEquals(1.2f, days[2].sleepFraction, 1e-6f)
        assertEquals(1.2f, days[2].stepFraction, 1e-6f)
        assertEquals(61L, days[2].restingHr)
    }

    @Test
    fun `距下一阶段还差多少成长值，开花后没有下一阶段`() {
        assertEquals(CompanionStage.SPROUT to 6, Companion(CompanionStage.SEED, 0, 0, 0).toNextStage())
        assertEquals(CompanionStage.SPROUT to 1, Companion(CompanionStage.SEED, 5, 0, 0).toNextStage())
        assertEquals(CompanionStage.SAPLING to 1, Companion(CompanionStage.SPROUT, 15, 0, 0).toNextStage())
        assertEquals(CompanionStage.BLOOM to 16, Companion(CompanionStage.SAPLING, 16, 0, 0).toNextStage())
        assertEquals(null, Companion(CompanionStage.BLOOM, 32, 0, 0).toNextStage())
    }
}
