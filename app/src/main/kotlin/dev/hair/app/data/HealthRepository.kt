package dev.hair.app.data

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HealthRepository(private val api: HealthApi, private val store: SnapshotStore) {

    suspend fun verifyAccountLinked() = api.checkIdentity()

    fun cached(): Snapshot? = store.load()

    fun clearCache() = store.clear()

    /** 从 API 拉取全部展示数据并写入缓存；任何一步失败都会抛出，且不改动旧缓存。 */
    suspend fun refresh(today: LocalDate = LocalDate.now()): Snapshot = coroutineScope {
        val since = today.minusDays(3)
        val historyFrom = today.minusDays(HISTORY_DAYS - 1L)
        // 日汇总类型的过滤语法都是 `<类型>.date >= <日期>`，结果最新的在前
        fun daily(dataType: String, field: String) =
            async { api.list(dataType, "$field.date >= \"$historyFrom\"", HISTORY_DAYS + 5) }

        val nights = async { nights(HISTORY_DAYS, today) }
        val rhr = daily("daily-resting-heart-rate", "daily_resting_heart_rate")
        val hrv = daily("daily-heart-rate-variability", "daily_heart_rate_variability")
        val temperature = daily("daily-sleep-temperature-derivations", "daily_sleep_temperature_derivations")
        val spo2 = daily("daily-oxygen-saturation", "daily_oxygen_saturation")
        val respiration = daily("daily-respiratory-rate", "daily_respiratory_rate")
        val steps = async { api.dailyRollUp(Metric.STEPS, historyFrom, today.plusDays(1)) }
        val cardio = async {
            api.list("daily-vo2-max", "daily_vo2_max.date >= \"${today.minusDays(30)}\"", 5)
        }
        val hr = async {
            val from = Instant.now().minus(Duration.ofHours(6))
            api.list("heart-rate", "heart_rate.sample_time.physical_time >= \"$from\"", 1)
        }

        val vitals = buildVitals(
            rhr.await(), hrv.await(), steps.await(), temperature.await(), spo2.await(), respiration.await(),
        )
        val allNights = nights.await()
        // “最新值”只认最近 3 天内的日汇总，更早的不冒充当前；vitals 升序，lastOrNull 即最新
        val recent = vitals.filter { it.date >= since }
        val overview = Overview(
            // “昨晚”只认最近 3 天内醒来的睡眠，更早的不冒充昨晚；列表按时间倒序，first() 即最新一条
            lastNight = allNights.firstOrNull { it.date >= since },
            restingHr = recent.lastOrNull { it.restingHr != null }?.restingHr,
            hrvMs = recent.lastOrNull { it.hrvMs != null }?.hrvMs,
            stepsToday = vitals.lastOrNull { it.date == today }?.steps,
            latestHr = hr.await().firstOrNull()?.heartRate?.beatsPerMinute?.toLongOrNull(),
            cardioFitness = cardio.await().firstNotNullOfOrNull { it.toCardioFitness() },
        )
        Snapshot(overview, allNights, System.currentTimeMillis(), vitals).also(store::save)
    }

    /**
     * 详情页的时间序列，不入缓存，每次现取。日档是 [today] 当天的 24 个小时桶，
     * 周、月档是截至 [today] 的最近 7、30 天。任何一步失败都会抛出。
     */
    suspend fun series(
        metric: Metric,
        range: DetailRange,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Series = when (range) {
        DetailRange.DAY -> {
            val start = today.atStartOfDay(zone).toInstant()
            val end = today.plusDays(1).atStartOfDay(zone).toInstant()
            hourlySeries(today, api.rollUp(metric.dataType, Duration.ofHours(1), start, end), zone, metric)
        }
        else -> {
            val from = today.minusDays(range.days - 1L)
            dailySeries(range, today, api.dailyRollUp(metric, from, today.plusDays(1)), metric)
        }
    }

    /** 一晚的心率（5 分钟窗口）和血氧采样，不入缓存。没有分期就不知道起止，返回 null。任何一步失败都会抛出。 */
    suspend fun nightDetail(night: Night): NightDetail? = coroutineScope {
        val (startSec, endSec) = night.windowSec ?: return@coroutineScope null
        val start = Instant.ofEpochSecond(startSec)
        val end = Instant.ofEpochSecond(endSec)
        val spo2Filter = "oxygen_saturation.sample_time.physical_time >= \"$start\" AND " +
            "oxygen_saturation.sample_time.physical_time < \"$end\""
        val heart = async { heartSeries(start, end, NIGHT_HR_WINDOW) }
        val spo2 = async { api.list("oxygen-saturation", spo2Filter, SPO2_LIMIT) }
        NightDetail(heart.await(), spo2.await().mapNotNull { it.toSpo2Point() }.sortedBy { it.sec })
    }

    /** [start] 含、[end] 不含的心率，按 [window] 汇总成最低、平均、最高；没有数据的窗口不返回。 */
    suspend fun heartSeries(start: Instant, end: Instant, window: Duration): List<HeartBucket> =
        api.rollUp("heart-rate", window, start, end).mapNotNull { it.toHeartBucket() }.sortedBy { it.startSec }

    /** 最近 [days] 天醒来的主睡眠（排除小睡），新的在前。 */
    private suspend fun nights(days: Int, today: LocalDate): List<Night> {
        val from = today.minusDays(days.toLong())
        val points = api.list("sleep", "sleep.interval.civil_end_time >= \"$from\"", limit = days + 5)
        return points.filter { it.sleep?.isMainSleep() == true }.mapNotNull { it.toNight() }
            .sortedByDescending { it.date }
    }

    private companion object {
        const val HISTORY_DAYS = 30
        const val SPO2_LIMIT = 2_000
    }
}
