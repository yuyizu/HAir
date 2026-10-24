package dev.hair.app.data

import kotlinx.serialization.Serializable

private val STEPS_RANGE = 1_000L..50_000L
private val SLEEP_RANGE_MIN = 240L..720L
private const val STEPS_INCREMENT = 500L
private const val SLEEP_INCREMENT_MIN = 15L

/** 用户设定的每日目标，今日页的步数进度条和睡眠水位以它为满值。范围见 [STEPS_RANGE]、[SLEEP_RANGE_MIN]，构造时校验。 */
@Serializable
data class Goals(
    val steps: Long = 8_000,
    val sleepMin: Long = 480,
) {
    init {
        require(steps in STEPS_RANGE) { "步数目标 $steps 超出 $STEPS_RANGE" }
        require(sleepMin in SLEEP_RANGE_MIN) { "睡眠目标 $sleepMin 分钟超出 $SLEEP_RANGE_MIN" }
    }

    /** [direction] 取 +1 或 -1；到达边界后保持不变。 */
    fun adjustSteps(direction: Int): Goals = copy(steps = (steps + direction * STEPS_INCREMENT).coerceIn(STEPS_RANGE))

    fun adjustSleep(direction: Int): Goals = copy(sleepMin = (sleepMin + direction * SLEEP_INCREMENT_MIN).coerceIn(SLEEP_RANGE_MIN))
}
