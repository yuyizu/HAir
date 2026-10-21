package dev.hair.app.data

import kotlin.math.roundToInt

enum class Sex { MALE, FEMALE }

/** 健康年龄。参考表只覆盖 20–79 岁，超出范围的 VO₂max 只给上下限，不外推。 */
sealed interface FitnessAge {
    data class Exact(val years: Int) : FitnessAge
    data class AtMost(val years: Int) : FitnessAge
    data class AtLeast(val years: Int) : FitnessAge
}

/**
 * FRIEND 参考表（用户提供的截图）：各十岁年龄段的 VO₂max，单位 mL/kg/min。
 * 把每个数值放在该段中点（20–29 岁 → 25），段与段之间按直线连接。
 * 每条曲线随年龄单调下降，才能由 VO₂max 反推唯一的年龄。
 */
private val FRIEND_CURVE: Map<Sex, List<Pair<Int, Double>>> = mapOf(
    Sex.MALE to listOf(25 to 48.0, 35 to 42.4, 45 to 37.8, 55 to 32.6, 65 to 28.2, 75 to 24.4),
    Sex.FEMALE to listOf(25 to 37.6, 35 to 30.2, 45 to 26.7, 55 to 23.4, 65 to 20.0, 75 to 18.3),
)

/** 该 [sex] 的参考曲线上，VO₂max 等于 [vo2Max] 的年龄。 */
fun fitnessAge(vo2Max: Double, sex: Sex): FitnessAge {
    val curve = FRIEND_CURVE.getValue(sex)
    val (youngestAge, youngestVo2) = curve.first()
    val (oldestAge, oldestVo2) = curve.last()
    if (vo2Max > youngestVo2) return FitnessAge.AtMost(youngestAge)
    if (vo2Max < oldestVo2) return FitnessAge.AtLeast(oldestAge)
    val (younger, older) = curve.zipWithNext().first { (_, older) -> vo2Max >= older.second }
    val fraction = (younger.second - vo2Max) / (younger.second - older.second)
    return FitnessAge.Exact((younger.first + fraction * (older.first - younger.first)).roundToInt())
}
