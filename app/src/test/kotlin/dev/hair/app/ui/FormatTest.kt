package dev.hair.app.ui

import dev.hair.app.data.FitnessAge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatTest {
    @Test
    fun `整小时的睡眠目标只写小时`() {
        assertEquals("8 小时", formatSleepGoal(480))
    }

    @Test
    fun `非整小时的睡眠目标带上分钟`() {
        assertEquals("7 小时 45 分", formatSleepGoal(465))
    }

    @Test
    fun `健康年龄的精确值和上下限各有写法`() {
        assertEquals("34 岁", formatFitnessAge(FitnessAge.Exact(34)))
        assertEquals("≤ 25 岁", formatFitnessAge(FitnessAge.AtMost(25)))
        assertEquals("≥ 75 岁", formatFitnessAge(FitnessAge.AtLeast(75)))
    }

    @Test
    fun `精确的健康年龄与实际年龄比较，给出相差年数`() {
        assertEquals("比实际年龄小 4 岁", compareToActualAge(FitnessAge.Exact(34), 38))
        assertEquals("比实际年龄大 3 岁", compareToActualAge(FitnessAge.Exact(41), 38))
        assertEquals("与实际年龄相当", compareToActualAge(FitnessAge.Exact(38), 38))
    }

    @Test
    fun `只有上下限的健康年龄不比较`() {
        assertNull(compareToActualAge(FitnessAge.AtMost(25), 38))
        assertNull(compareToActualAge(FitnessAge.AtLeast(75), 38))
    }
}
