package dev.hair.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GoalsTest {
    @Test
    fun `默认目标是 8000 步和 8 小时睡眠`() {
        assertEquals(Goals(steps = 8_000, sleepMin = 480), Goals())
    }

    @Test
    fun `步数目标每次调整 500，睡眠目标每次调整 15 分钟`() {
        assertEquals(8_500L, Goals().adjustSteps(1).steps)
        assertEquals(7_500L, Goals().adjustSteps(-1).steps)
        assertEquals(495L, Goals().adjustSleep(1).sleepMin)
        assertEquals(465L, Goals().adjustSleep(-1).sleepMin)
    }

    @Test
    fun `步数目标钳在 1000 到 50000 之间`() {
        assertEquals(50_000L, Goals(steps = 50_000).adjustSteps(1).steps)
        assertEquals(1_000L, Goals(steps = 1_000).adjustSteps(-1).steps)
    }

    @Test
    fun `睡眠目标钳在 4 到 12 小时之间`() {
        assertEquals(720L, Goals(sleepMin = 720).adjustSleep(1).sleepMin)
        assertEquals(240L, Goals(sleepMin = 240).adjustSleep(-1).sleepMin)
    }

    @Test
    fun `调整一项不会改动另一项`() {
        val goals = Goals(steps = 10_000, sleepMin = 420)
        assertEquals(420L, goals.adjustSteps(1).sleepMin)
        assertEquals(10_000L, goals.adjustSleep(1).steps)
    }

    @Test
    fun `构造时拒绝范围外的目标`() {
        assertThrows(IllegalArgumentException::class.java) { Goals(steps = 0) }
        assertThrows(IllegalArgumentException::class.java) { Goals(steps = 50_001) }
        assertThrows(IllegalArgumentException::class.java) { Goals(sleepMin = 239) }
        assertThrows(IllegalArgumentException::class.java) { Goals(sleepMin = 721) }
    }
}
