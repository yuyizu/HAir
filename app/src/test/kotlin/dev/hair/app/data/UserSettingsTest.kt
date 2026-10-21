package dev.hair.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class UserSettingsTest {
    @Test
    fun `默认设置是默认目标，性别和出生年份未设置`() {
        val settings = UserSettings()
        assertEquals(Goals(), settings.goals)
        assertNull(settings.sex)
        assertNull(settings.birthYear)
    }

    @Test
    fun `出生年份只接受 1920 到 2010`() {
        UserSettings(birthYear = 1920)
        UserSettings(birthYear = 2010)
        assertThrows(IllegalArgumentException::class.java) { UserSettings(birthYear = 1919) }
        assertThrows(IllegalArgumentException::class.java) { UserSettings(birthYear = 2011) }
    }

    @Test
    fun `实际年龄是给定年份减出生年份，没设出生年份时为 null`() {
        assertEquals(36, UserSettings(birthYear = 1990).ageIn(2026))
        assertNull(UserSettings().ageIn(2026))
    }
}
