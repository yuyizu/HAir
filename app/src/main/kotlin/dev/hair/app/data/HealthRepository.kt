package dev.hair.app.data

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class HealthRepository(private val api: HealthApi) {

    suspend fun verifyAccountLinked() = api.checkIdentity()

    /** 最近 [days] 天醒来的主睡眠（排除小睡），新的在前。 */
    suspend fun nights(days: Int, today: LocalDate = LocalDate.now()): List<Night> {
        val from = today.minusDays(days.toLong())
        val points = api.list("sleep", "sleep.interval.civil_end_time >= \"$from\"", pageSize = days + 5)
        return points.filter { it.sleep?.isMainSleep() == true }.mapNotNull { it.toNight() }
            .sortedByDescending { it.date }
    }

    suspend fun overview(today: LocalDate = LocalDate.now()): Overview = coroutineScope {
        val since = today.minusDays(3)
        val nights = async { nights(days = 3, today = today) }
        val rhr = async {
            api.list("daily-resting-heart-rate", "daily_resting_heart_rate.date >= \"$since\"", 5)
        }
        val hrv = async {
            api.list("daily-heart-rate-variability", "daily_heart_rate_variability.date >= \"$since\"", 5)
        }
        val steps = async { api.dailySteps(today, today.plusDays(1)) }
        val cardio = async {
            api.list("daily-vo2-max", "daily_vo2_max.date >= \"${today.minusDays(30)}\"", 5)
        }
        val hr = async {
            val from = Instant.now().minus(Duration.ofHours(6))
            api.list("heart-rate", "heart_rate.sample_time.physical_time >= \"$from\"", 1)
        }

        // 列表按时间倒序返回，first() 即最新一条
        Overview(
            lastNight = nights.await().firstOrNull(),
            restingHr = rhr.await().firstOrNull()?.dailyRestingHeartRate?.beatsPerMinute?.toLongOrNull(),
            hrvMs = hrv.await().firstOrNull()?.dailyHeartRateVariability?.averageHeartRateVariabilityMilliseconds,
            stepsToday = steps.await().firstOrNull()?.steps?.countSum?.toLongOrNull(),
            latestHr = hr.await().firstOrNull()?.heartRate?.beatsPerMinute?.toLongOrNull(),
            cardioFitness = cardio.await().firstNotNullOfOrNull { it.toCardioFitness() },
        )
    }
}
