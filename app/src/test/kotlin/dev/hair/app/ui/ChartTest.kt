package dev.hair.app.ui

import dev.hair.app.data.DetailRange
import dev.hair.app.data.Series
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ChartTest {
    private val today = LocalDate.of(2026, 10, 20)

    private fun series(range: DetailRange) = Series(
        range,
        if (range == DetailRange.DAY) today else today.minusDays(range.days - 1L),
        List(if (range == DetailRange.DAY) 24 else range.days) { 0.0 },
    )

    @Test
    fun `纵轴上限取 1、2、5 的整十倍`() {
        assertEquals(10_000.0, axisMax(8_123.0), 0.0)
        assertEquals(5_000.0, axisMax(4_100.0), 0.0)
        assertEquals(2_000.0, axisMax(1_500.0), 0.0)
        assertEquals(1_000.0, axisMax(1_000.0), 0.0)
        assertEquals(500.0, axisMax(312.5), 0.0)
    }

    @Test
    fun `纵轴上限不小于数据最大值`() {
        listOf(1.0, 9.9, 10.1, 77.0, 999.0, 1_001.0, 24_680.0).forEach {
            assertTrue("axisMax($it)", axisMax(it) >= it)
        }
    }

    @Test
    fun `没有数据或数据很小时纵轴至少是 10`() {
        assertEquals(10.0, axisMax(0.0), 0.0)
        assertEquals(10.0, axisMax(3.0), 0.0)
    }

    @Test
    fun `日档横轴每 6 小时一个标签`() {
        assertEquals(listOf(0 to "0时", 6 to "6时", 12 to "12时", 18 to "18时"), axisLabels(series(DetailRange.DAY)))
    }

    @Test
    fun `周档每天一个标签，月档从今天起每 7 天一个`() {
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6), axisLabels(series(DetailRange.WEEK)).map { it.first })

        val month = axisLabels(series(DetailRange.MONTH))
        assertEquals(listOf(1, 8, 15, 22, 29), month.map { it.first })
        assertEquals("10/20", month.last().second)
        assertEquals("9/22", month.first().second)
    }

    @Test
    fun `选中柱的时间描述`() {
        assertEquals("09:00–10:00", bucketLabel(series(DetailRange.DAY), 9))
        assertEquals("23:00–24:00", bucketLabel(series(DetailRange.DAY), 23))
        assertTrue(bucketLabel(series(DetailRange.WEEK), 6).startsWith("10月20日"))
        assertTrue(bucketLabel(series(DetailRange.WEEK), 0).startsWith("10月14日"))
    }
}
