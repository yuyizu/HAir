package dev.hair.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class SceneSettingsTest {
    private val october = LocalDate.of(2026, 10, 24)

    @Test
    fun `没有指定季节时跟随日期，指定后以指定为准`() {
        assertEquals(Season.AUTUMN, SceneSettings().seasonOn(october))
        assertEquals(Season.WINTER, SceneSettings(season = Season.WINTER).seasonOn(october))
    }

    @Test
    fun `南半球的纬度让同一天变成相反的季节，手动指定优先`() {
        assertEquals(Season.SPRING, SceneSettings(latitude = -34).seasonOn(october))
        assertEquals(Season.AUTUMN, SceneSettings(latitude = 31).seasonOn(october))
        assertEquals(Season.SUMMER, SceneSettings(latitude = -34, season = Season.SUMMER).seasonOn(october))
    }

    @Test
    fun `气候带按纬度分，南北对称，边界归外侧`() {
        assertEquals(Climate.TROPICAL, climateAt(0))
        assertEquals(Climate.TROPICAL, climateAt(23))
        assertEquals(Climate.TEMPERATE, climateAt(24))
        assertEquals(Climate.TEMPERATE, climateAt(-49))
        assertEquals(Climate.COLD, climateAt(50))
        assertEquals(Climate.COLD, climateAt(-68))
    }

    @Test
    fun `环境：手动选的气候优先于纬度，两者都没有时是温带淡水`() {
        assertEquals(Habitat(WaterType.FRESH, Climate.TEMPERATE), SceneSettings().habitat)
        assertEquals(Habitat(WaterType.SEA, Climate.TROPICAL), SceneSettings(water = WaterType.SEA, latitude = 10).habitat)
        assertEquals(Habitat(WaterType.FRESH, Climate.COLD), SceneSettings(latitude = 10, climate = Climate.COLD).habitat)
    }

    @Test
    fun `纬度必须在 -90 到 90 之间`() {
        SceneSettings(latitude = 90)
        SceneSettings(latitude = -90)
        assertThrows(IllegalArgumentException::class.java) { SceneSettings(latitude = 91) }
        assertThrows(IllegalArgumentException::class.java) { SceneSettings(latitude = -91) }
    }

    @Test
    fun `旧的设置文件没有水景字段，读出来是默认值；写入后能原样读回`() {
        val old = HealthJson.decodeFromString<UserSettings>("""{"goals":{"steps":9000,"sleepMin":480},"sex":"MALE"}""")
        assertEquals(SceneSettings(), old.scene)
        assertEquals(9_000L, old.goals.steps)

        val chosen = old.copy(scene = SceneSettings(Season.SPRING, WaterType.SEA, Climate.COLD, latitude = -34, species = listOf("cod")))
        val back = HealthJson.decodeFromString<UserSettings>(HealthJson.encodeToString(UserSettings.serializer(), chosen))
        assertEquals(chosen.scene, back.scene)
        assertNull(old.scene.latitude)
    }
}
