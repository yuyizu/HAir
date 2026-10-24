package dev.hair.app.data

import java.time.LocalDate

enum class CompanionStage { SEED, SPROUT, SAPLING, BLOOM }

/**
 * 身体伙伴的状态：近 [WINDOW_DAYS] 天里睡眠和步数各自达到目标的天数之和就是 [growth]，
 * 所以它只由已有的睡眠、步数和目标推出，没有另存的进度；断了几天，它会回落。
 */
data class Companion(val stage: CompanionStage, val growth: Int, val sleepDays: Int, val stepDays: Int) {
    companion object {
        const val WINDOW_DAYS = 30
        const val MAX_GROWTH = WINDOW_DAYS * 2
    }

    /** 下一个阶段和还差的成长值；已经是最高阶段返回 null。 */
    fun toNextStage(): Pair<CompanionStage, Int>? =
        STAGE_FROM.firstOrNull { it.second > growth }?.let { (stage, from) -> stage to from - growth }
}

private val STAGE_FROM = listOf(CompanionStage.SPROUT to 6, CompanionStage.SAPLING to 16, CompanionStage.BLOOM to 32)

/** 窗口是截至 [today]（含）的 [Companion.WINDOW_DAYS] 天；今天的步数还没走完也照实算。 */
fun companion(nights: List<Night>, vitals: List<DailyVitals>, goals: Goals, today: LocalDate): Companion {
    val from = today.minusDays(Companion.WINDOW_DAYS - 1L)
    val inWindow = { d: LocalDate -> d >= from && d <= today }
    val sleepDays = nights.filter { inWindow(it.date) && it.asleepMin >= goals.sleepMin }.map { it.date }.toSet().size
    val stepDays = vitals.count { inWindow(it.date) && (it.steps ?: 0) >= goals.steps }
    val growth = sleepDays + stepDays
    val stage = STAGE_FROM.lastOrNull { growth >= it.second }?.first ?: CompanionStage.SEED
    return Companion(stage, growth, sleepDays, stepDays)
}

/** 年度图里的一天：睡眠和步数各是目标的几分之几（封顶 1.2，超目标一点也画出来），缺数据为 0。 */
data class ArtDay(val date: LocalDate, val sleepFraction: Float, val stepFraction: Float, val restingHr: Long?)

/** 年度图里一项指标相对目标的最大比例；超过的部分不再变长。 */
const val ART_CAP = 1.2f

/** 截至 [today] 的 [days] 天，从旧到新，每天一个 [ArtDay]。 */
fun artDays(nights: List<Night>, vitals: List<DailyVitals>, goals: Goals, today: LocalDate, days: Int): List<ArtDay> {
    val nightByDate = nights.associateBy { it.date }
    val vitalsByDate = vitals.associateBy { it.date }
    return List(days) { i ->
        val date = today.minusDays((days - 1 - i).toLong())
        val sleep = nightByDate[date]?.asleepMin ?: 0L
        val steps = vitalsByDate[date]?.steps ?: 0L
        ArtDay(
            date,
            (sleep.toFloat() / goals.sleepMin).coerceAtMost(ART_CAP),
            (steps.toFloat() / goals.steps).coerceAtMost(ART_CAP),
            vitalsByDate[date]?.restingHr,
        )
    }
}
