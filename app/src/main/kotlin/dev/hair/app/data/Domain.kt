@file:UseSerializers(LocalDateSerializer::class)

package dev.hair.app.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDate

/** 一晚睡眠，分钟数；日期是醒来当天（civil end date）。 */
@Serializable
data class Night(
    val date: LocalDate,
    val asleepMin: Long,
    val inBedMin: Long,
    val deepMin: Long,
    val remMin: Long,
    val lightMin: Long,
    val awakeMin: Long,
)

@Serializable
data class Overview(
    val lastNight: Night?,
    val restingHr: Long?,
    val hrvMs: Double?,
    val stepsToday: Long?,
    val latestHr: Long?,
    val cardioFitness: CardioFitness?,
)

private fun String?.minutes(): Long = this?.toLongOrNull() ?: 0L

internal fun Date.toLocalDate() = LocalDate.of(year, month, day)

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

/** Google 已按年龄、性别归一化的心肺适能等级，[level] 为 null 表示未给出等级。 */
@Serializable
data class CardioFitness(val date: LocalDate, val vo2Max: Double, val level: CardioLevel?)

enum class CardioLevel { POOR, FAIR, AVERAGE, GOOD, VERY_GOOD, EXCELLENT }

/** VO2Max 缺失时无法展示，返回 null；等级枚举之外的值（含 UNSPECIFIED）视为未给出等级。 */
fun DataPoint.toCardioFitness(): CardioFitness? {
    val daily = dailyVo2Max ?: return null
    val date = daily.date?.toLocalDate() ?: return null
    val vo2Max = daily.vo2Max ?: return null
    val level = CardioLevel.entries.firstOrNull { it.name == daily.cardioFitnessLevel }
    return CardioFitness(date, vo2Max, level)
}

/** LocalDate 以 ISO-8601 字符串（yyyy-MM-dd）持久化。 */
object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}
