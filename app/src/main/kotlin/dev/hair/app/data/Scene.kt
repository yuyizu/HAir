package dev.hair.app.data

import kotlinx.serialization.Serializable
import java.time.LocalDate
import kotlin.math.abs

enum class WaterType { FRESH, SEA }

enum class Climate { TROPICAL, TEMPERATE, COLD }

/** 水里养什么，由水质和气候带决定。 */
data class Habitat(val water: WaterType, val climate: Climate)

private const val TROPIC_LATITUDE = 23.5
private const val COLD_LATITUDE = 50.0

/** 按纬度粗分气候带：南北回归线之间是热带，50° 以上算寒带，其余温带。 */
fun climateAt(latitude: Int): Climate = when {
    abs(latitude) < TROPIC_LATITUDE -> Climate.TROPICAL
    abs(latitude) < COLD_LATITUDE -> Climate.TEMPERATE
    else -> Climate.COLD
}

/**
 * 水景的个人设置，只保存在这台手机上。
 * [latitude] 是用户授权后读到的粗略纬度（取整到 1°），没授权或没读到时为 null，不保存经度。
 */
@Serializable
data class SceneSettings(
    /** null 表示跟随手机日期。 */
    val season: Season? = null,
    val water: WaterType = WaterType.FRESH,
    /** null 表示按 [latitude] 判断，没有纬度时按温带。 */
    val climate: Climate? = null,
    val latitude: Int? = null,
    /** 选了哪些鱼（物种 id）；空表示用这个环境的默认几种。 */
    val species: List<String> = emptyList(),
    /** 点水里的动物、水草和风景时是否出声。 */
    val sound: Boolean = true,
) {
    init {
        require(latitude == null || latitude in -90..90) { "纬度 $latitude 超出 -90..90" }
    }

    val habitat: Habitat get() = Habitat(water, climate ?: latitude?.let(::climateAt) ?: Climate.TEMPERATE)

    /** 没指定季节时按日期；南半球（纬度为负）的季节与北半球相反。 */
    fun seasonOn(date: LocalDate): Season = season ?: seasonOf(date, northern = (latitude ?: 0) >= 0)
}
