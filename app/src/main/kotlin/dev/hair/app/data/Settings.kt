package dev.hair.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File

val BIRTH_YEARS = 1920..2010

/** 用户在本机设置的内容。[sex] 和 [birthYear] 只用于健康年龄，未设置时为 null。 */
@Serializable
data class UserSettings(
    val goals: Goals = Goals(),
    val sex: Sex? = null,
    val birthYear: Int? = null,
    val scene: SceneSettings = SceneSettings(),
) {
    init {
        require(birthYear == null || birthYear in BIRTH_YEARS) { "出生年份 $birthYear 超出 $BIRTH_YEARS" }
    }

    /** 按出生年份粗算，可能与实际差 1 岁。 */
    fun ageIn(year: Int): Int? = birthYear?.let { year - it }
}

/**
 * 把 [UserSettings] 存成单个 JSON 文件。设置不是健康数据，不随退出登录清除。
 */
class SettingsStore(private val file: File) {

    /** 文件不存在、损坏或内容超出范围时回到默认设置；下一次 [save] 会覆盖坏文件。 */
    fun load(): UserSettings {
        if (!file.exists()) return UserSettings()
        return try {
            HealthJson.decodeFromString<UserSettings>(file.readText())
        } catch (e: IllegalArgumentException) {
            // SerializationException 是它的子类；init 里的 require 失败也会走到这里
            UserSettings()
        }
    }

    fun save(settings: UserSettings) = file.writeTextAtomically(HealthJson.encodeToString(settings))
}
