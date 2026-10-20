package dev.hair.app.data

import java.time.LocalDate

/** 一晚睡眠，分钟数；日期是醒来当天（civil end date）。 */
data class Night(
    val date: LocalDate,
    val asleepMin: Long,
    val inBedMin: Long,
    val deepMin: Long,
    val remMin: Long,
    val lightMin: Long,
    val awakeMin: Long,
)

data class Overview(
    val lastNight: Night?,
    val restingHr: Long?,
    val hrvMs: Double?,
    val stepsToday: Long?,
    val latestHr: Long?,
)

private fun String?.minutes(): Long = this?.toLongOrNull() ?: 0L

private fun Date.toLocalDate() = LocalDate.of(year, month, day)

/** 无法确定醒来日期的数据点返回 null（此时无法归属到某一天，不猜测）。 */
fun DataPoint.toNight(): Night? {
    val sleep = sleep ?: return null
    val date = sleep.interval?.civilEndTime?.date?.toLocalDate() ?: return null
    val summary = sleep.summary ?: return null
    val byStage = summary.stagesSummary.associate { it.type to it.minutes.minutes() }
    return Night(
        date = date,
        asleepMin = summary.minutesAsleep.minutes(),
        inBedMin = summary.minutesInSleepPeriod.minutes(),
        deepMin = byStage["DEEP"] ?: 0,
        remMin = byStage["REM"] ?: 0,
        lightMin = byStage["LIGHT"] ?: 0,
        awakeMin = summary.minutesAwake.minutes(),
    )
}

fun Sleep.isMainSleep(): Boolean = metadata?.nap != true
