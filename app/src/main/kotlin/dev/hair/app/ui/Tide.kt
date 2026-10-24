package dev.hair.app.ui

import dev.hair.app.data.Night
import dev.hair.app.data.SleepStage

/** 完成度 0..1；没有数据返回 null，超过目标按 1 处理。 */
fun goalProgress(value: Long?, goal: Long): Float? = value?.let { (it.toFloat() / goal).coerceIn(0f, 1f) }

/** 水景里的一层水：睡眠阶段、占整片水体的比例（0..1）和分钟数。 */
data class TideLayer(val stage: SleepStage, val fraction: Float, val minutes: Long)

/**
 * 昨夜睡眠的水景布局，全是比例，不含像素。[level] 是睡眠相对目标的完成度，水面随它升高；
 * [layers] 自水面向下排列，占比之和为 1；没有分期数据（经典睡眠）时为空，画成单层水。
 */
data class Tide(val level: Float, val layers: List<TideLayer>)

/** 越深的阶段越靠下：清醒贴着水面，深睡沉在最底。 */
private val SURFACE_TO_BOTTOM = listOf(SleepStage.AWAKE, SleepStage.LIGHT, SleepStage.REM, SleepStage.DEEP)

fun tide(night: Night, goalMin: Long): Tide {
    val minutes = mapOf(
        SleepStage.AWAKE to night.awakeMin,
        SleepStage.LIGHT to night.lightMin,
        SleepStage.REM to night.remMin,
        SleepStage.DEEP to night.deepMin,
    )
    val total = minutes.values.sum()
    val layers = SURFACE_TO_BOTTOM.mapNotNull { stage ->
        minutes.getValue(stage).takeIf { it > 0 }?.let { TideLayer(stage, it.toFloat() / total, it) }
    }
    return Tide(goalProgress(night.asleepMin, goalMin)!!, layers)
}
