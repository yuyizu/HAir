package dev.hair.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SeasonTest {
    private fun north(month: Int, day: Int) = seasonOf(LocalDate.of(2026, month, day))

    @Test
    fun `北半球按气象季节划分，边界日归新的一季`() {
        assertEquals(Season.WINTER, north(2, 28))
        assertEquals(Season.SPRING, north(3, 1))
        assertEquals(Season.SPRING, north(5, 31))
        assertEquals(Season.SUMMER, north(6, 1))
        assertEquals(Season.SUMMER, north(8, 31))
        assertEquals(Season.AUTUMN, north(9, 1))
        assertEquals(Season.AUTUMN, north(11, 30))
        assertEquals(Season.WINTER, north(12, 1))
        assertEquals(Season.WINTER, north(1, 15))
    }

    @Test
    fun `南半球同一天是相反的季节`() {
        assertEquals(Season.SUMMER, seasonOf(LocalDate.of(2026, 1, 15), northern = false))
        assertEquals(Season.WINTER, seasonOf(LocalDate.of(2026, 7, 15), northern = false))
        assertEquals(Season.AUTUMN, seasonOf(LocalDate.of(2026, 4, 1), northern = false))
        assertEquals(Season.SPRING, seasonOf(LocalDate.of(2026, 10, 24), northern = false))
    }
}
