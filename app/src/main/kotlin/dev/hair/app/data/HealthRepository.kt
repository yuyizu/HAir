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
        val nights = async { nights(NIGHTS_DAYS, today) }
        val rhr = async {
            api.list("daily-resting-heart-rate", "daily_resting_heart_rate.date >= \"$since\"", 5)
        }
        val hrv = async {
            api.list("daily-heart-rate-variability", "daily_heart_rate_variability.date >= \"$since\"", 5)
        }
        val steps = async { api.dailyRollUp(Metric.STEPS, today, today.plusDays(1)) }
        val cardio = async {
            api.list("daily-vo2-max", "daily_vo2_max.date >= \"${today.minusDays(30)}\"", 5)
        }
        val hr = async {
            val from = Instant.now().minus(Duration.ofHours(6))
            api.list("heart-rate", "heart_rate.sample_time.physical_time >= \"$from\"", 1)
        }

        val allNights = nights.await()
        // 列表按时间倒序返回，first() 即最新一条
        val overview = Overview(
            // “昨晚”只认最近 3 天内醒来的睡眠，更早的不冒充昨晚
            lastNight = allNights.firstOrNull { it.date >= since },
            restingHr = rhr.await().firstOrNull()?.dailyRestingHeartRate?.beatsPerMinute?.toLongOrNull(),
            hrvMs = hrv.await().firstOrNull()?.dailyHeartRateVariability?.averageHeartRateVariabilityMilliseconds,
            stepsToday = steps.await().firstOrNull()?.steps?.countSum?.toLongOrNull(),
            latestHr = hr.await().firstOrNull()?.heartRate?.beatsPerMinute?.toLongOrNull(),
            cardioFitness = cardio.await().firstNotNullOfOrNull { it.toCardioFitness() },
        )
        Snapshot(overview, allNights, System.currentTimeMillis()).also(store::save)
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
            hourlySeries(today, api.hourlyRollUp(metric, start, end), zone, metric)
        }
        else -> {
            val from = today.minusDays(range.days - 1L)
            dailySeries(range, today, api.dailyRollUp(metric, from, today.plusDays(1)), metric)
        }
    }

    /** 最近 [days] 天醒来的主睡眠（排除小睡），新的在前。 */
    private suspend fun nights(days: Int, today: LocalDate): List<Night> {
        val from = today.minusDays(days.toLong())
        val points = api.list("sleep", "sleep.interval.civil_end_time >= \"$from\"", pageSize = days + 5)
        return points.filter { it.sleep?.isMainSleep() == true }.mapNotNull { it.toNight() }
            .sortedByDescending { it.date }
    }

    private companion object {
        const val NIGHTS_DAYS = 14
    }
}
