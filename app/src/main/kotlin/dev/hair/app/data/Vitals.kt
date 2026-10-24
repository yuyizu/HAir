@file:UseSerializers(LocalDateSerializer::class)

package dev.hair.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.LocalDate

/**
 * 某一天的各项日汇总，缺的项为 null。睡眠派生的指标（HRV、皮温、血氧、呼吸率）按它们自己的日期归档，
 * 步数按活动当天。
 */
@Serializable
data class DailyVitals(
    val date: LocalDate,
    val restingHr: Long? = null,
    val hrvMs: Double? = null,
    val steps: Long? = null,
    /** 夜间腕部皮温减去个人基线，正数表示比平时热。 */
    val skinTempDeltaC: Double? = null,
    /** 皮温 30 天的波动幅度，用来判断偏离算不算大。 */
    val skinTempSpreadC: Double? = null,
    val spo2Avg: Double? = null,
    val spo2Min: Double? = null,
    val respRate: Double? = null,
)

/** 接口用 NaN、Infinity 表示“没有这个值”；映射层统一当缺失，不让它们流进计算。 */
internal fun Double?.finiteOrNull(): Double? = this?.takeIf { it.isFinite() }

/** 把各类日汇总按日期合并，日期升序。缺日期的数据点无法归档，直接丢弃。 */
fun buildVitals(
    restingHr: List<DataPoint>,
    hrv: List<DataPoint>,
    steps: List<DailyRollupDataPoint>,
    temperature: List<DataPoint>,
    spo2: List<DataPoint>,
    respiration: List<DataPoint>,
): List<DailyVitals> {
    val byDate = sortedMapOf<LocalDate, DailyVitals>()
    fun update(date: Date?, change: (DailyVitals) -> DailyVitals) {
        val day = date?.toLocalDate() ?: return
        byDate[day] = change(byDate[day] ?: DailyVitals(day))
    }

    restingHr.forEach { p ->
        p.dailyRestingHeartRate?.let { r -> update(r.date) { it.copy(restingHr = r.beatsPerMinute?.toLongOrNull()) } }
    }
    hrv.forEach { p ->
        p.dailyHeartRateVariability?.let { h -> update(h.date) { it.copy(hrvMs = h.averageHeartRateVariabilityMilliseconds.finiteOrNull()) } }
    }
    steps.forEach { p -> update(p.civilStartTime?.date) { it.copy(steps = p.steps?.countSum?.toLongOrNull()) } }
    temperature.forEach { p ->
        p.dailySleepTemperatureDerivations?.let { t ->
            val nightly = t.nightlyTemperatureCelsius.finiteOrNull()
            val baseline = t.baselineTemperatureCelsius.finiteOrNull()
            update(t.date) {
                it.copy(
                    skinTempDeltaC = if (nightly != null && baseline != null) nightly - baseline else null,
                    skinTempSpreadC = t.relativeNightlyStddev30dCelsius.finiteOrNull(),
                )
            }
        }
    }
    spo2.forEach { p ->
        p.dailyOxygenSaturation?.let { o -> update(o.date) { it.copy(spo2Avg = o.averagePercentage.finiteOrNull(), spo2Min = o.lowerBoundPercentage.finiteOrNull()) } }
    }
    respiration.forEach { p ->
        p.dailyRespiratoryRate?.let { r -> update(r.date) { it.copy(respRate = r.breathsPerMinute.finiteOrNull()) } }
    }
    return byDate.values.toList()
}
