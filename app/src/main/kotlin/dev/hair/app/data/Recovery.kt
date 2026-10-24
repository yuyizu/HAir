package dev.hair.app.data

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max

/** 参与恢复评估的指标；[weight] 是权重，[minSpread] 是波动幅度的下限，避免基线太平时一点小变化就算大偏差。 */
enum class RecoveryMetric(val weight: Double, val minSpread: Double, val higherIsBetter: Boolean) {
    HRV(0.4, 4.0, true),
    RESTING_HR(0.3, 2.0, false),
    SLEEP(0.3, 30.0, true),
}

/** 晴、轻云、多云、阴，依次表示相对自己近期水平从高到低。 */
enum class Weather { SUNNY, FAIR, CLOUDY, OVERCAST }

/**
 * 某一项指标与自己近期水平的比较。[score] 在 -1（明显更差）到 1（明显更好）之间，
 * 0 表示和 [baseline]（近期中位数）一样。
 */
data class RecoveryFactor(
    val metric: RecoveryMetric,
    val date: LocalDate,
    val value: Double,
    val baseline: Double,
    val score: Double,
)

enum class Deviation { LOW, IN_RANGE, HIGH }

/** 和自己的近期水平比偏低、偏高还是在常态内；方向只看数值比基线大还是小，不论好坏。 */
val RecoveryFactor.deviation: Deviation
    get() = when {
        abs(score) < NOTABLE_SCORE -> Deviation.IN_RANGE
        value < baseline -> Deviation.LOW
        else -> Deviation.HIGH
    }

/** 恢复估计：[index] 是各项 [RecoveryFactor.score] 的加权平均。它是自定的参考指标，不是医学评估。 */
data class Recovery(val weather: Weather, val index: Double, val factors: List<RecoveryFactor>)

private const val BASELINE_DAYS = 28L
private const val MIN_BASELINE_VALUES = 4
private const val MIN_FACTORS = 2
private const val MAD_TO_SIGMA = 1.4826
private const val SUNNY_FROM = 0.25
private const val FAIR_FROM = -0.15
private const val CLOUDY_FROM = -0.5
/** 偏离不小于自己近期的一个波动幅度（z ≥ 1，即 |score| ≥ 0.5）才算偏离。 */
private const val NOTABLE_SCORE = 0.5

/**
 * 把最近的 HRV、静息心率、睡眠时长分别和自己此前 [BASELINE_DAYS] 天的中位数比较，加权成一个天气。
 * 每项的“当前值”取 [today] 或前一天里最新的一条；某项基线不足 [MIN_BASELINE_VALUES] 天就不参与，
 * 参与的项不足 [MIN_FACTORS] 个时返回 null（数据还在积累，不硬给结论）。
 */
fun recovery(nights: List<Night>, vitals: List<DailyVitals>, today: LocalDate): Recovery? {
    val factors = listOfNotNull(
        factor(RecoveryMetric.HRV, vitals.mapNotNull { v -> v.hrvMs?.let { v.date to it } }, today),
        factor(RecoveryMetric.RESTING_HR, vitals.mapNotNull { v -> v.restingHr?.let { v.date to it.toDouble() } }, today),
        factor(RecoveryMetric.SLEEP, nights.map { it.date to it.asleepMin.toDouble() }, today),
    )
    if (factors.size < MIN_FACTORS) return null
    val index = factors.sumOf { it.metric.weight * it.score } / factors.sumOf { it.metric.weight }
    return Recovery(weatherFor(index), index, factors)
}

private fun weatherFor(index: Double) = when {
    index >= SUNNY_FROM -> Weather.SUNNY
    index >= FAIR_FROM -> Weather.FAIR
    index >= CLOUDY_FROM -> Weather.CLOUDY
    else -> Weather.OVERCAST
}

private fun factor(metric: RecoveryMetric, series: List<Pair<LocalDate, Double>>, today: LocalDate): RecoveryFactor? {
    val (date, value) = series.filter { (d, _) -> d <= today && d >= today.minusDays(1) }.maxByOrNull { it.first } ?: return null
    val baseline = series.filter { (d, _) -> d < date && d >= date.minusDays(BASELINE_DAYS) }.map { it.second }
    if (baseline.size < MIN_BASELINE_VALUES) return null
    val center = median(baseline)
    val spread = max(median(baseline.map { abs(it - center) }) * MAD_TO_SIGMA, metric.minSpread)
    val z = (value - center) / spread
    val score = ((if (metric.higherIsBetter) z else -z) / 2).coerceIn(-1.0, 1.0)
    return RecoveryFactor(metric, date, value, center, score)
}

internal fun median(values: List<Double>): Double {
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
}

/** 夜间腕部皮温相对个人基线的偏离；[notable] 表示偏离不小于自己 30 天的波动幅度。 */
data class SkinTempSignal(val date: LocalDate, val deltaC: Double, val notable: Boolean)

/** 取 [today] 或前一天里最新的一条皮温偏离。它是腕部皮肤温度，不是体温，只作参考。 */
fun skinTempSignal(vitals: List<DailyVitals>, today: LocalDate): SkinTempSignal? {
    val day = vitals
        .filter { it.skinTempDeltaC != null && it.date <= today && it.date >= today.minusDays(1) }
        .maxByOrNull { it.date } ?: return null
    val delta = day.skinTempDeltaC ?: return null
    return SkinTempSignal(day.date, delta, notable = day.skinTempSpreadC?.let { abs(delta) >= it } ?: false)
}
