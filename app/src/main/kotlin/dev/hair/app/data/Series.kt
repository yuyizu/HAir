package dev.hair.app.data

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

/** 详情页支持的活动类指标；[dataType] 是 API 的 kebab-case 数据类型。 */
enum class Metric(val dataType: String, val title: String, val unit: String) {
    STEPS("steps", "步数", "步"),
    ACTIVE_ENERGY("active-energy-burned", "活动能量", "千卡"),
}

/** 日档按小时（24 桶），周、月档按天。 */
enum class DetailRange(val label: String, val days: Int) {
    DAY("日", 1),
    WEEK("周", 7),
    MONTH("月", 30),
}

/**
 * 一段时间内按桶排列的数值，从旧到新。[start] 是第一个桶所在的日期；
 * 日档的桶是 0..23 点，周、月档的桶是连续的日期。没有记录的桶为 0。
 */
data class Series(val range: DetailRange, val start: LocalDate, val values: List<Double>) {
    val total: Double get() = values.sum()

    /** 有记录（大于 0）的桶数。 */
    val recordedCount: Int get() = values.count { it > 0 }

    /** 只在有记录的桶上取平均，没戴设备的空白天不会拉低日均；一个记录都没有时为 0。 */
    val average: Double get() = if (recordedCount == 0) 0.0 else total / recordedCount

    /** 数值最大的桶；全为 0 时返回 null，不把空数据说成“最高”。 */
    val peakIndex: Int? get() = values.indices.maxByOrNull { values[it] }?.takeIf { values[it] > 0 }
}

private fun pickValue(metric: Metric, steps: StepsRollupValue?, energy: EnergyRollupValue?): Double? = when (metric) {
    Metric.STEPS -> steps?.countSum?.toDoubleOrNull()
    Metric.ACTIVE_ENERGY -> energy?.kcalSum
}

fun DailyRollupDataPoint.value(metric: Metric): Double? = pickValue(metric, steps, activeEnergyBurned)

fun RollupDataPoint.value(metric: Metric): Double? = pickValue(metric, steps, activeEnergyBurned)

/** 周、月档：截至 [today]（含）的最近 [DetailRange.days] 天，按日期对齐，缺的天补 0。 */
fun dailySeries(range: DetailRange, today: LocalDate, points: List<DailyRollupDataPoint>, metric: Metric): Series {
    val start = today.minusDays(range.days - 1L)
    val byDate = points.mapNotNull { p ->
        p.civilStartTime?.date?.let { it.toLocalDate() to (p.value(metric) ?: 0.0) }
    }.toMap()
    return Series(range, start, List(range.days) { byDate[start.plusDays(it.toLong())] ?: 0.0 })
}

/** 日档：24 个小时桶，窗口按 [zone] 的本地钟点落桶（夏令时重复的那一小时会累加进同一桶）。 */
fun hourlySeries(today: LocalDate, points: List<RollupDataPoint>, zone: ZoneId, metric: Metric): Series {
    val values = DoubleArray(24)
    for (p in points) {
        val start = p.startTime ?: continue
        values[OffsetDateTime.parse(start).atZoneSameInstant(zone).hour] += p.value(metric) ?: 0.0
    }
    return Series(DetailRange.DAY, today, values.toList())
}
