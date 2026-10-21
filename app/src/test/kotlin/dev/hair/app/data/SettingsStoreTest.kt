package dev.hair.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SettingsStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val file get() = File(tmp.root, "settings.json")

    @Test
    fun `没有文件时返回默认设置`() {
        assertEquals(UserSettings(), SettingsStore(file).load())
    }

    @Test
    fun `保存后读回的设置与原值相等，重建 store 也一样`() {
        val settings = UserSettings(Goals(steps = 12_500, sleepMin = 465), Sex.FEMALE, 1988)
        SettingsStore(file).save(settings)
        assertEquals(settings, SettingsStore(file).load())
    }

    @Test
    fun `性别和出生年份未设置时也能往返`() {
        val settings = UserSettings(Goals(steps = 9_000))
        SettingsStore(file).save(settings)
        assertEquals(settings, SettingsStore(file).load())
    }

    @Test
    fun `再次保存会替换旧设置，且不残留临时文件`() {
        val store = SettingsStore(file)
        store.save(UserSettings(sex = Sex.MALE))
        store.save(UserSettings(sex = Sex.FEMALE))
        assertEquals(UserSettings(sex = Sex.FEMALE), store.load())
        assertFalse(File(file.path + ".tmp").exists())
    }

    @Test
    fun `文件损坏时回到默认设置，下一次保存能覆盖它`() {
        file.writeText("{not json")
        val store = SettingsStore(file)
        assertEquals(UserSettings(), store.load())
        store.save(UserSettings(sex = Sex.MALE))
        assertEquals(UserSettings(sex = Sex.MALE), store.load())
    }

    @Test
    fun `文件里的目标或出生年份超出范围时回到默认设置`() {
        file.writeText("""{"goals":{"steps":0,"sleepMin":480}}""")
        assertEquals(UserSettings(), SettingsStore(file).load())
        file.writeText("""{"birthYear":1800}""")
        assertEquals(UserSettings(), SettingsStore(file).load())
    }
}
