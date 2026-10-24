package dev.hair.app.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
private val DATE_CLOCK = DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.CHINA)
private val NIGHT_DATE = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)

/** 手机当前时区的时:分。 */
fun clockText(epochSec: Long): String = CLOCK.format(Instant.ofEpochSecond(epochSec).atZone(ZoneId.systemDefault()))

fun dateClockText(epochMillis: Long): String = DATE_CLOCK.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

fun nightDateText(date: LocalDate): String = NIGHT_DATE.format(date)

/** 睡眠记录的标题：醒来日期是今天叫“昨夜睡眠”，更早的写日期。 */
fun sleepCaption(wakeDate: LocalDate, today: LocalDate): String =
    if (wakeDate == today) "昨夜睡眠" else "${wakeDate.monthValue}月${wakeDate.dayOfMonth}日的睡眠"

/** 睡眠时长的 时:分，如 6:40。 */
fun formatHm(minutes: Long): String = "%d:%02d".format(minutes / 60, minutes % 60)

/** 计时器用的 分:秒，满一小时后是 时:分:秒。 */
fun timerText(millis: Long): String {
    val s = millis / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

/** 一段记录的时长；不足一分钟照实说，不进位。 */
fun lengthText(millis: Long): String {
    val minutes = millis / 60_000
    return if (minutes < 1) "不到 1 分钟" else if (minutes < 60) "$minutes 分钟" else formatMinutes(minutes)
}
