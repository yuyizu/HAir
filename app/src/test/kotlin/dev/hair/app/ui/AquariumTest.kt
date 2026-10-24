package dev.hair.app.ui

import dev.hair.app.data.Climate
import dev.hair.app.data.Habitat
import dev.hair.app.data.Season
import dev.hair.app.data.WaterType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AquariumTest {
    private val habitat = Habitat(WaterType.FRESH, Climate.TEMPERATE)
    private val width = 400f
    private val depth = 250f

    private fun aquarium(season: Season = Season.SPRING) =
        Aquarium(speciesFor(habitat, emptyList()), habitat, seasonLook(season), seed = 7)

    /** 把所有鱼和小鱼群挪到右下角，测试里再单独摆要点的那一个。 */
    private fun Aquarium.parkFish() {
        fish.forEach { it.x = 0.95f; it.y = 0.9f; it.tx = 0.95f; it.ty = 0.9f }
        school.x = 0.95f
        school.y = 0.85f
    }

    private fun Aquarium.run(seconds: Float) = repeat((seconds / 0.033f).toInt()) { step(0.033f) }

    @Test
    fun `游三分钟后鱼、虾、小鱼群都还在水里，气泡不超过上限`() {
        val a = aquarium()
        a.run(180f)
        a.fish.forEach { assertTrue("${it.species.name} x=${it.x}", it.x in 0.05f..0.95f) }
        a.fish.forEach { assertTrue("${it.species.name} y=${it.y}", it.y in 0.1f..0.94f) }
        a.shrimps.forEach { assertTrue(it.x in 0.34f..0.92f) }
        assertTrue(a.school.x in 0.05f..0.95f && a.school.y in 0.1f..0.94f)
        assertTrue(a.bubbles.size <= 30)
    }

    @Test
    fun `鱼会游动，不是定在原地`() {
        val a = aquarium()
        val before = a.fish.map { it.x to it.y }
        a.run(10f)
        a.fish.forEachIndexed { i, f -> assertTrue("${f.species.name} 没动", f.x != before[i].first || f.y != before[i].second) }
    }

    @Test
    fun `点中鱼返回它的名字，鱼受惊冲刺、冒出气泡、显示名字牌`() {
        val a = aquarium()
        a.parkFish()
        val koi = a.fish.first()
        koi.x = 0.5f
        koi.y = 0.4f

        val touch = a.touch(0.5f, 0.4f, width, depth, now = 3f)
        assertEquals("锦鲤", touch?.name)
        assertEquals(SceneSound.FISH, touch?.cue?.sound)
        assertTrue(koi.boost > 0f)
        assertTrue(a.bubbles.size >= 5)
        assertEquals("锦鲤", a.toast?.name)
        assertEquals(3f, a.toast?.bornAt)
    }

    @Test
    fun `受惊的鱼向远离点击的一侧游，一段时间后恢复平时的状态`() {
        val a = aquarium()
        a.parkFish()
        val koi = a.fish.first()
        koi.x = 0.5f
        koi.y = 0.4f
        a.touch(0.45f, 0.4f, width, depth, now = 0f)
        assertTrue("应该向右游", koi.tx > koi.x)

        a.run(2f)
        assertEquals(0f, koi.boost, 0f)
    }

    @Test
    fun `点在空水处不点中谁，附近的鱼游过去看看，远处的不动`() {
        val a = aquarium()
        a.parkFish()
        val near = a.fish.first().also { it.x = 0.2f; it.y = 0.4f }
        val far = a.fish.last()
        val farTarget = far.tx to far.ty

        assertNull(a.touch(0.5f, 0.4f, width, depth, now = 0f))
        assertEquals(0.5f, near.tx, 1e-6f)
        assertEquals(0.4f, near.ty, 1e-6f)
        assertTrue(near.curious > 0f)
        assertEquals(0f, far.curious, 0f)
        assertEquals(farTarget, far.tx to far.ty)
        assertNull(a.toast)
    }

    @Test
    fun `点中小鱼群让它炸开，点中虾让它弹开，各自显示名字`() {
        val a = aquarium()
        a.parkFish()
        a.school.x = 0.3f
        a.school.y = 0.3f
        val (sx, sy) = a.smallPosition(a.smalls.first(), 0f)
        assertEquals("小鱼群", a.touch(sx, sy, width, depth, now = 0f)?.name)
        assertEquals(1f, a.school.scatter, 0f)

        val shrimp = a.shrimps.first().also { it.x = 0.5f }
        assertEquals("米虾", a.touch(0.5f, shrimp.y, width, depth, now = 0f)?.name)
        assertTrue(shrimp.flick > 0f)
    }

    @Test
    fun `炸开的小鱼群会重新聚拢`() {
        val a = aquarium()
        a.school.scatter = 1f
        a.run(3f)
        assertEquals(0f, a.school.scatter, 0f)
    }

    @Test
    fun `冬天的鱼活动水层整体比春天深，也比春天游得慢`() {
        val winter = seasonLook(Season.WINTER)
        val spring = seasonLook(Season.SPRING)
        assertTrue(winter.fishSpeed < spring.fishSpeed)

        val a = aquarium(Season.WINTER)
        a.run(120f)
        a.fish.forEach { assertTrue("${it.species.name} y=${it.y}", it.y >= it.species.depth.start + winter.fishDepthShift - 1e-4f) }
    }

    @Test
    fun `同一个种子游出来的结果一样，所以重新进入页面时不会乱跳`() {
        val a = aquarium()
        val b = aquarium()
        a.run(20f)
        b.run(20f)
        assertEquals(a.fish.map { it.x }, b.fish.map { it.x })
        assertNotNull(a.fish.firstOrNull())
    }

    @Test
    fun `点水草让它摇晃并出声，鱼越大声音越低`() {
        val a = aquarium()
        a.parkFish()
        a.school.x = 0.1f
        a.school.y = 0.2f
        val plant = a.plants.first()

        val touch = a.touch(plant.x, 0.97f, width, depth, now = 2f)
        assertEquals("苦草", touch?.name)
        assertEquals(SceneSound.RUSTLE, touch?.cue?.sound)
        assertEquals(1f, plant.shake, 0f)
        assertEquals("苦草", a.toast?.name)

        a.run(2f)
        assertEquals(0f, plant.shake, 0f)

        val big = Aquarium(speciesFor(habitat, listOf("koi")), habitat, seasonLook(Season.SPRING), seed = 1)
        val small = Aquarium(speciesFor(Habitat(WaterType.FRESH, Climate.TROPICAL), listOf("neon")), Habitat(WaterType.FRESH, Climate.TROPICAL), seasonLook(Season.SPRING), seed = 1)
        big.fish.first().let { it.x = 0.5f; it.y = 0.4f }
        small.fish.first().let { it.x = 0.5f; it.y = 0.4f }
        big.school.x = 0.1f; big.school.y = 0.1f
        small.school.x = 0.1f; small.school.y = 0.1f
        val bigRate = big.touch(0.5f, 0.4f, width, depth, 0f)!!.cue.rate
        val smallRate = small.touch(0.5f, 0.4f, width, depth, 0f)!!.cue.rate
        assertTrue("大鱼 $bigRate 应低于小鱼 $smallRate", bigRate < smallRate)
    }
}
