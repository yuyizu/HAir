package dev.hair.app.ui

import dev.hair.app.data.Climate
import dev.hair.app.data.Habitat
import dev.hair.app.data.WaterType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeciesTest {
    private val allHabitats = WaterType.entries.flatMap { water -> Climate.entries.map { Habitat(water, it) } }

    @Test
    fun `六种环境各有四种鱼，id 不重复`() {
        allHabitats.forEach { assertEquals("$it", 4, speciesIn(it).size) }
        assertEquals(ALL_SPECIES.size, ALL_SPECIES.map { it.id }.toSet().size)
    }

    @Test
    fun `每种鱼的水层、体长、速度都在合理范围`() {
        ALL_SPECIES.forEach {
            assertTrue("${it.id} 水层", it.depth.start in 0.1f..0.9f && it.depth.endInclusive in 0.15f..0.94f && it.depth.start < it.depth.endInclusive)
            assertTrue("${it.id} 体长", it.lengthDp in 12f..50f)
            assertTrue("${it.id} 速度", it.speed in 0.02f..0.1f)
        }
    }

    @Test
    fun `没选过用前三种，选了就用选的，选的不在这个环境时回到默认`() {
        val fresh = Habitat(WaterType.FRESH, Climate.TEMPERATE)
        assertEquals(listOf("koi", "crucian", "goldfish"), speciesFor(fresh, emptyList()).map { it.id })
        assertEquals(listOf("koi", "loach"), speciesFor(fresh, listOf("loach", "koi")).map { it.id })
        assertEquals(listOf("koi", "crucian", "goldfish"), speciesFor(fresh, listOf("cod", "clownfish")).map { it.id })
    }

    @Test
    fun `选的里有别的环境的鱼时，只留这个环境里有的`() {
        val sea = Habitat(WaterType.SEA, Climate.COLD)
        assertEquals(listOf("cod"), speciesFor(sea, listOf("koi", "cod")).map { it.id })
    }

    @Test
    fun `勾选和取消勾选按环境顺序排列，只剩一种时取消不生效`() {
        val fresh = Habitat(WaterType.FRESH, Climate.TEMPERATE)
        // 默认是前三种：取消金鱼、勾上泥鳅
        val withoutGoldfish = toggledSpecies(fresh, emptyList(), "goldfish")
        assertEquals(listOf("koi", "crucian"), withoutGoldfish)
        assertEquals(listOf("koi", "crucian", "loach"), toggledSpecies(fresh, withoutGoldfish, "loach"))
        // 取消到只剩一种后，再取消它无效
        val onlyKoi = toggledSpecies(fresh, withoutGoldfish, "crucian")
        assertEquals(listOf("koi"), onlyKoi)
        assertEquals(listOf("koi"), toggledSpecies(fresh, onlyKoi, "koi"))
    }
}
