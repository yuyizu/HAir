package dev.hair.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 字段名对照 Google Health API v4 Discovery 文档（revision 20261019）。
 * int64 在 JSON 中是字符串，这里保持 String，由映射层统一转换。
 */
val HealthJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    // 个别字段会返回字面量 NaN（如还没有个人基线时），解析放行，由映射层当作缺失值
    allowSpecialFloatingPointValues = true
}

@Serializable
data class ListResponse(
    val dataPoints: List<DataPoint> = emptyList(),
    val nextPageToken: String? = null,
)

@Serializable
data class DataPoint(
    val sleep: Sleep? = null,
    val heartRate: HeartRate? = null,
    val dailyRestingHeartRate: DailyRestingHeartRate? = null,
    val dailyHeartRateVariability: DailyHeartRateVariability? = null,
    val dailyVo2Max: DailyVo2Max? = null,
    val dailySleepTemperatureDerivations: DailySleepTemperatureDerivations? = null,
    val dailyOxygenSaturation: DailyOxygenSaturation? = null,
    val dailyRespiratoryRate: DailyRespiratoryRate? = null,
    val oxygenSaturation: OxygenSaturation? = null,
)

@Serializable
data class Date(val year: Int = 0, val month: Int = 0, val day: Int = 0)

@Serializable
data class TimeOfDay(val hours: Int = 0, val minutes: Int = 0, val seconds: Int = 0, val nanos: Int = 0)

@Serializable
data class CivilDateTime(val date: Date? = null, val time: TimeOfDay? = null)

/** 真机响应里只有起止时刻（RFC 3339）和各自的 UTC 偏移（如 "28800s"），没有 civilEndTime。 */
@Serializable
data class SessionInterval(
    val startTime: String? = null,
    val endTime: String? = null,
    val endUtcOffset: String? = null,
)

@Serializable
data class Sleep(
    val interval: SessionInterval? = null,
    val summary: SleepSummary? = null,
    val metadata: SleepMetadata? = null,
    val stages: List<StageInterval> = emptyList(),
)

/** 一段睡眠分期；[type] 为 AWAKE、LIGHT、DEEP、REM（经典睡眠用别的值）。 */
@Serializable
data class StageInterval(val startTime: String? = null, val endTime: String? = null, val type: String? = null)

@Serializable
data class SleepMetadata(val mainSleep: Boolean? = null, val nap: Boolean? = null)

@Serializable
data class SleepSummary(
    val minutesAsleep: String? = null,
    val minutesAwake: String? = null,
    val minutesInSleepPeriod: String? = null,
    val stagesSummary: List<StageSummary> = emptyList(),
)

@Serializable
data class StageSummary(val type: String? = null, val minutes: String? = null)

@Serializable
data class SampleTime(val physicalTime: String? = null)

@Serializable
data class HeartRate(val sampleTime: SampleTime? = null, val beatsPerMinute: String? = null)

@Serializable
data class DailyRestingHeartRate(val date: Date? = null, val beatsPerMinute: String? = null)

@Serializable
data class DailyHeartRateVariability(
    val date: Date? = null,
    val averageHeartRateVariabilityMilliseconds: Double? = null,
)

@Serializable
data class DailyRollUpRequest(val range: CivilRange, val windowSizeDays: Int = 1)

/** 左闭右开区间。 */
@Serializable
data class CivilRange(val start: CivilDateTime, val end: CivilDateTime)

@Serializable
data class DailyRollUpResponse(val rollupDataPoints: List<DailyRollupDataPoint> = emptyList())

@Serializable
data class DailyRollupDataPoint(
    val civilStartTime: CivilDateTime? = null,
    val steps: StepsRollupValue? = null,
    val activeEnergyBurned: EnergyRollupValue? = null,
)

@Serializable
data class StepsRollupValue(val countSum: String? = null)

/** 夜间皮温和个人基线（摄氏度，腕部皮肤温度，不是体温）；[relativeNightlyStddev30dCelsius] 是 30 天的波动幅度。 */
@Serializable
data class DailySleepTemperatureDerivations(
    val date: Date? = null,
    val nightlyTemperatureCelsius: Double? = null,
    val baselineTemperatureCelsius: Double? = null,
    val relativeNightlyStddev30dCelsius: Double? = null,
)

@Serializable
data class DailyOxygenSaturation(
    val date: Date? = null,
    val averagePercentage: Double? = null,
    val lowerBoundPercentage: Double? = null,
    val upperBoundPercentage: Double? = null,
)

@Serializable
data class DailyRespiratoryRate(val date: Date? = null, val breathsPerMinute: Double? = null)

@Serializable
data class OxygenSaturation(val sampleTime: SampleTime? = null, val percentage: Double? = null)

@Serializable
data class HeartRateRollupValue(
    val beatsPerMinuteMin: Double? = null,
    val beatsPerMinuteAvg: Double? = null,
    val beatsPerMinuteMax: Double? = null,
)

@Serializable
data class DailyVo2Max(
    val date: Date? = null,
    val vo2Max: Double? = null,
    val cardioFitnessLevel: String? = null,
)

@Serializable
data class TimeInterval(val startTime: String, val endTime: String)

/** 物理时间窗口汇总，[windowSize] 为 google-duration，如 "3600s"；[range] 左闭右开。 */
@Serializable
data class RollUpRequest(val windowSize: String, val range: TimeInterval)

@Serializable
data class RollUpResponse(val rollupDataPoints: List<RollupDataPoint> = emptyList())

@Serializable
data class RollupDataPoint(
    val startTime: String? = null,
    val endTime: String? = null,
    val steps: StepsRollupValue? = null,
    val activeEnergyBurned: EnergyRollupValue? = null,
    val heartRate: HeartRateRollupValue? = null,
)

@Serializable
data class EnergyRollupValue(val kcalSum: Double? = null)
