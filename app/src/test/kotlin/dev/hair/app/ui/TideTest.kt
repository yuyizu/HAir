package dev.hair.app.ui

import dev.hair.app.data.Night
import dev.hair.app.data.SleepStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TideTest {
    private fun night(asleep: Long, deep: Long, rem: Long, light: Long, awake: Long) =
        Night(LocalDate.of(2026, 10, 24), asleep, asleep + awake, deep, rem, light, awake)

    @Test
    fun `没有数据时返回 null，超过目标按 1 处理`() {
        assertNull(goalProgress(null, 8_000))
        assertEquals(0.25f, goalProgress(2_000, 8_000)!!, 0.0001f)
        assertEquals(0f, goalProgress(0, 8_000)!!, 0f)
        assertEquals(1f, goalProgress(12_000, 8_000)!!, 0f)
    }

    @Test
    fun `水位是睡眠相对目标的完成度`() {
        // 昨夜实测：睡了 6 小时 40 分，目标 8 小时
        val t = tide(night(400, deep = 73, rem = 99, light = 228, awake = 23), goalMin = 480)

        assertEquals(400f / 480f, t.level, 0.0001f)
    }

    @Test
    fun `睡够目标后水位不再上升`() {
        assertEquals(1f, tide(night(540, 90, 120, 330, 10), goalMin = 480).level, 0f)
    }

    @Test
    fun `各层自水面向下按 清醒、浅睡、REM、深睡 排列，占比按分钟数`() {
        val t = tide(night(400, deep = 73, rem = 99, light = 228, awake = 23), goalMin = 480)

        assertEquals(listOf(SleepStage.AWAKE, SleepStage.LIGHT, SleepStage.REM, SleepStage.DEEP), t.layers.map { it.stage })
        assertEquals(23f / 423f, t.layers[0].fraction, 0.0001f)
        assertEquals(228f / 423f, t.layers[1].fraction, 0.0001f)
        assertEquals(73L, t.layers[3].minutes)
        assertEquals(1f, t.layers.sumOf { it.fraction.toDouble() }.toFloat(), 0.0001f)
    }

    @Test
    fun `某个阶段为 0 分钟时不出现这一层`() {
        val t = tide(night(300, deep = 60, rem = 0, light = 240, awake = 0), goalMin = 480)

        assertEquals(listOf(SleepStage.LIGHT, SleepStage.DEEP), t.layers.map { it.stage })
        assertTrue(t.layers.all { it.minutes > 0 })
    }

    @Test
    fun `没有分期数据时没有分层，只有水位`() {
        val t = tide(night(360, deep = 0, rem = 0, light = 0, awake = 0), goalMin = 480)

        assertTrue(t.layers.isEmpty())
        assertEquals(0.75f, t.level, 0.0001f)
    }
}
