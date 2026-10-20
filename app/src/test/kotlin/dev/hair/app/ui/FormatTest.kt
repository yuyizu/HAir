package dev.hair.app.ui

import org.junit.Assert.assertEquals
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
}
