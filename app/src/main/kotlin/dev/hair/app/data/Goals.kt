package dev.hair.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File

private val STEPS_RANGE = 1_000L..50_000L
private val SLEEP_RANGE_MIN = 240L..720L
private const val STEPS_INCREMENT = 500L
private const val SLEEP_INCREMENT_MIN = 15L

/** 用户设定的每日目标，摘要页的圆环以它为满圈。范围见 [STEPS_RANGE]、[SLEEP_RANGE_MIN]，构造时校验。 */
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

/**
 * 把 [Goals] 存成单个 JSON 文件。目标是用户设置而不是健康数据，不随退出登录清除。
 */
class GoalStore(private val file: File) {

    /** 文件不存在、损坏或目标超出范围时回到默认目标；下一次 [save] 会覆盖坏文件。 */
    fun load(): Goals {
        if (!file.exists()) return Goals()
        return try {
            HealthJson.decodeFromString<Goals>(file.readText())
        } catch (e: IllegalArgumentException) {
            // SerializationException 是它的子类；init 里的 require 失败也会走到这里
            Goals()
        }
    }

    fun save(goals: Goals) = file.writeTextAtomically(HealthJson.encodeToString(goals))
}
