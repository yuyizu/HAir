package dev.hair.app.data

import java.time.Duration
import java.time.OffsetDateTime

/** 夜间心率的汇总窗口；取数和画图共用，窗口宽度只在这里定义。 */
val NIGHT_HR_WINDOW: Duration = Duration.ofMinutes(5)

/** 一个汇总窗口内的心率：从 [startSec]（epoch 秒）起，最低、平均、最高。 */
data class HeartBucket(val startSec: Long, val minBpm: Double, val avgBpm: Double, val maxBpm: Double)

data class Spo2Point(val sec: Long, val percent: Double)

/** 一晚（或一段时间）的心率与血氧细节，数据量大，不入缓存，每次现取。 */
data class NightDetail(val heart: List<HeartBucket>, val spo2: List<Spo2Point>)

/** 窗口内没有心率（缺平均值）时返回 null；缺最低、最高值时用平均值补，窗口退化成一个点。 */
fun RollupDataPoint.toHeartBucket(): HeartBucket? {
    val start = startTime ?: return null
    val hr = heartRate ?: return null
    val avg = hr.beatsPerMinuteAvg.finiteOrNull() ?: return null
    return HeartBucket(
        OffsetDateTime.parse(start).toEpochSecond(),
        hr.beatsPerMinuteMin.finiteOrNull() ?: avg,
        avg,
        hr.beatsPerMinuteMax.finiteOrNull() ?: avg,
    )
}

fun DataPoint.toSpo2Point(): Spo2Point? {
    val sample = oxygenSaturation ?: return null
    val time = sample.sampleTime?.physicalTime ?: return null
    val percent = sample.percentage.finiteOrNull() ?: return null
    return Spo2Point(OffsetDateTime.parse(time).toEpochSecond(), percent)
}
