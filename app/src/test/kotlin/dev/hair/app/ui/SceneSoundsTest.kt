package dev.hair.app.ui

import dev.hair.app.data.Season
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SceneSoundsTest {
    @Test
    fun `每种声音都有内容，峰值适中，头尾归零不爆音，不超过零点六秒`() {
        SceneSound.entries.forEach { sound ->
            val pcm = renderSound(sound)
            val peak = pcm.maxOf { abs(it.toInt()) } / Short.MAX_VALUE.toFloat()
            assertEquals("$sound 峰值", PEAK, peak, 0.01f)
            assertEquals("$sound 起点", 0, pcm.first().toInt())
            assertEquals("$sound 终点", 0, pcm.last().toInt())
            assertTrue("$sound 太长：${pcm.size.toFloat() / SAMPLE_RATE} 秒", pcm.size <= 0.6f * SAMPLE_RATE)
            assertTrue("$sound 太短", pcm.size >= 0.1f * SAMPLE_RATE)
        }
    }

    @Test
    fun `同一种声音每次合成的结果一样`() {
        SceneSound.entries.forEach { assertTrue("$it", renderSound(it).contentEquals(renderSound(it))) }
    }

    @Test
    fun `各种声音听起来不一样`() {
        val all = SceneSound.entries.map { renderSound(it).toList() }
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun `四季的天空、枝条、水面景物各配不同的声音`() {
        Season.entries.forEach { season ->
            assertTrue("$season 的天空和枝条应该不同声", sceneryCue(season, SceneryPart.SKY).sound != sceneryCue(season, SceneryPart.BRANCH).sound)
        }
        // 四季的天空各有各的声音，不会春夏秋冬都是同一个
        assertTrue(Season.entries.map { sceneryCue(it, SceneryPart.SKY).sound }.distinct().size >= 3)
    }

    @Test
    fun `点右上角是枝条，水面上的景物只在对应的季节和位置，其余是天空`() {
        val width = 411f
        val surface = 300f
        val bar = 28f
        assertEquals(SceneryPart.BRANCH, sceneryPartAt(Season.SPRING, 380f, bar + 100f, width, surface, bar))
        assertEquals(SceneryPart.SKY, sceneryPartAt(Season.SPRING, 380f, bar + 20f, width, surface, bar))
        assertEquals(SceneryPart.SKY, sceneryPartAt(Season.SPRING, 60f, bar + 100f, width, surface, bar))
        assertEquals(SceneryPart.FEATURE, sceneryPartAt(Season.SUMMER, 200f, surface - 10f, width, surface, bar))
        assertEquals(SceneryPart.SKY, sceneryPartAt(Season.SUMMER, 200f, surface - 80f, width, surface, bar))
        assertEquals(SceneryPart.FEATURE, sceneryPartAt(Season.AUTUMN, 290f, surface - 60f, width, surface, bar))
        assertEquals(SceneryPart.FEATURE, sceneryPartAt(Season.WINTER, 10f, surface - 5f, width, surface, bar))
        assertEquals(SceneryPart.SKY, sceneryPartAt(Season.WINTER, 200f, surface - 5f, width, surface, bar))
        assertEquals(SceneryPart.SKY, sceneryPartAt(Season.SPRING, 200f, surface - 5f, width, surface, bar))
    }
}
