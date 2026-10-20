package dev.hair.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToLong

/** 数字滚动和圆环填充共用的时长，两者同时起跑、同时到位。 */
const val COUNT_MS = 700

private const val ENTRANCE_STEP_MS = 45L
private const val ENTRANCE_MS = 260
private const val ENTRANCE_MAX_INDEX = 8

/** 整个出场序列的总时长，调用方据此判断“首次出场已播完”。 */
const val INTRO_TOTAL_MS = ENTRANCE_MAX_INDEX * ENTRANCE_STEP_MS + ENTRANCE_MS + 100

/**
 * 首次进入时按阅读顺序依次淡入并上浮。[play] 为 false（出场已播过）时直接显示，
 * 所以滚动回来或切回本页不会重播。只在首次组合时读取 [play]。
 */
@Composable
fun Entrance(index: Int, play: Boolean, content: @Composable () -> Unit) {
    val progress = remember { Animatable(if (play) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) {
            delay(index.coerceAtMost(ENTRANCE_MAX_INDEX) * ENTRANCE_STEP_MS)
            progress.animateTo(1f, tween(ENTRANCE_MS, easing = FastOutSlowInEasing))
        }
    }
    Box(
        Modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * 24.dp.toPx()
        },
    ) { content() }
}

/**
 * 数字滚到新值。[fromZero] 为 true 时首次显示从 0 涨起（出场），之后值变化（如刷新）从当前显示值接着滚；
 * 为 false 时首次直接显示当前值。[value] 为 null 时返回 null，由调用方显示空状态。
 */
@Composable
fun animatedCount(value: Long?, fromZero: Boolean): Long? {
    var target by remember { mutableFloatStateOf(if (fromZero) 0f else (value ?: 0L).toFloat()) }
    LaunchedEffect(value) { target = (value ?: 0L).toFloat() }
    val shown by animateFloatAsState(target, tween(COUNT_MS, easing = FastOutSlowInEasing), label = "count")
    return if (value == null) null else shown.roundToLong()
}

/** 按下时缩到 0.97，松开弹回；与点击共用同一个 [source]。 */
@Composable
fun Modifier.pressScale(source: MutableInteractionSource): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.97f else 1f,
        spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "press",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
