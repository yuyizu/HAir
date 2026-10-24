@file:UseSerializers(LocalDateSerializer::class)

package dev.hair.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File
import java.time.LocalDate

private const val MAX_HABIT_LENGTH = 12
private const val MIN_DAYS_PER_GROUP = 3
private val DEFAULT_HABITS = listOf("下午喝咖啡", "晚饭吃得晚", "运动", "喝酒")

/** [date] 当天傍晚到睡前做没做 [habit]；影响的是第二天醒来的睡眠和晨间指标。 */
@Serializable
data class HabitMark(val date: LocalDate, val habit: String, val done: Boolean)

/** 用户自己记录的习惯和每天的“做了 / 没做”。没记录的天不属于任何一组，不算“没做”。 */
@Serializable
data class HabitLog(val habits: List<String> = DEFAULT_HABITS, val marks: List<HabitMark> = emptyList()) {
    init {
        require(habits.toSet().size == habits.size) { "习惯名重复：$habits" }
        require(habits.all { it.isNotBlank() && it.length <= MAX_HABIT_LENGTH }) { "习惯名必须非空且不超过 $MAX_HABIT_LENGTH 字：$habits" }
    }

    fun markOf(date: LocalDate, habit: String): Boolean? = marks.lastOrNull { it.date == date && it.habit == habit }?.done

    /** [done] 为 null 表示清除这一天的记录。 */
    fun withMark(date: LocalDate, habit: String, done: Boolean?): HabitLog {
        require(habit in habits) { "未知习惯：$habit" }
        val others = marks.filterNot { it.date == date && it.habit == habit }
        return copy(marks = if (done == null) others else others + HabitMark(date, habit, done))
    }

    fun withHabit(name: String): HabitLog = name.trim().let { if (it in habits) this else copy(habits = habits + it) }

    /** 同时删掉这个习惯的全部记录。 */
    fun withoutHabit(name: String): HabitLog = copy(habits = habits - name, marks = marks.filterNot { it.habit == name })
}

/** 可对比的结果；[pick] 从一晚睡眠和对应日期的日汇总里取值，缺数据返回 null。 */
enum class Outcome(val label: String, val unit: String, val pick: (Night?, DailyVitals?) -> Double?) {
    ASLEEP("睡眠时长", "分钟", { n, _ -> n?.asleepMin?.toDouble() }),
    DEEP("深睡", "分钟", { n, _ -> n?.deepMin?.toDouble() }),
    RESTING_HR("静息心率", "bpm", { _, v -> v?.restingHr?.toDouble() }),
    HRV("心率变异性", "ms", { _, v -> v?.hrvMs }),
}

data class GroupStat(val days: Int, val mean: Double?)

data class OutcomeComparison(val outcome: Outcome, val done: GroupStat, val skipped: GroupStat) {
    /** 两组都至少有 [MIN_DAYS_PER_GROUP] 天才值得比较。 */
    val enough: Boolean get() = done.days >= MIN_DAYS_PER_GROUP && skipped.days >= MIN_DAYS_PER_GROUP

    /** 做了的天减去没做的天；任一组没有数据时为 null。 */
    val difference: Double? get() = done.mean?.let { d -> skipped.mean?.let { d - it } }
}

data class HabitComparison(val habit: String, val doneDays: Int, val skippedDays: Int, val outcomes: List<OutcomeComparison>)

/**
 * 对比做了和没做 [habit] 的日子的第二天：标记在 D 日，结果取 D+1 日醒来的睡眠和当天的日汇总。
 * 这是个人记录里的关联，样本少，也不能说明因果。
 */
fun compareHabit(habit: String, log: HabitLog, nights: List<Night>, vitals: List<DailyVitals>): HabitComparison {
    val marks = log.marks.filter { it.habit == habit }
    val nightByDate = nights.associateBy { it.date }
    val vitalsByDate = vitals.associateBy { it.date }
    fun stat(done: Boolean, outcome: Outcome): GroupStat {
        val values = marks.filter { it.done == done }.mapNotNull {
            val next = it.date.plusDays(1)
            outcome.pick(nightByDate[next], vitalsByDate[next])
        }
        return GroupStat(values.size, values.takeIf { it.isNotEmpty() }?.average())
    }
    return HabitComparison(
        habit,
        doneDays = marks.count { it.done },
        skippedDays = marks.count { !it.done },
        outcomes = Outcome.entries.map { OutcomeComparison(it, stat(true, it), stat(false, it)) },
    )
}

/** 把 [HabitLog] 存成单个 JSON 文件；用户自己写的记录，不随退出登录清除。 */
class HabitStore(private val file: File) {

    /** 文件不存在、损坏或内容不合法时回到默认；下一次 [save] 会覆盖坏文件。 */
    fun load(): HabitLog {
        if (!file.exists()) return HabitLog()
        return try {
            HealthJson.decodeFromString<HabitLog>(file.readText())
        } catch (e: IllegalArgumentException) {
            // SerializationException 是它的子类；init 里的 require 失败也会走到这里
            HabitLog()
        }
    }

    fun save(log: HabitLog) = file.writeTextAtomically(HealthJson.encodeToString(log))
}
