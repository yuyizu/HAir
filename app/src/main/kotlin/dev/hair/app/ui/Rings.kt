package dev.hair.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/** 完成度 0..1；没有数据返回 null（画空圈），超过目标按满环处理。 */
fun ringProgress(value: Long?, goal: Long): Float? = value?.let { (it.toFloat() / goal).coerceIn(0f, 1f) }

/** 两层同心环，外环 [outer]、内环 [inner]；null 只画底轨。[animateFromZero] 为 true 时首次从 0 填充到目标，之后数据变化从当前位置接着走。 */
@Composable
fun ActivityRings(
    outer: Float?,
    inner: Float?,
    outerColor: Color,
    innerColor: Color,
    trackColor: Color,
    animateFromZero: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var outerTarget by remember { mutableFloatStateOf(if (animateFromZero) 0f else outer ?: 0f) }
    var innerTarget by remember { mutableFloatStateOf(if (animateFromZero) 0f else inner ?: 0f) }
    LaunchedEffect(outer, inner) {
        outerTarget = outer ?: 0f
        innerTarget = inner ?: 0f
    }
    val outerShown by animateFloatAsState(outerTarget, tween(COUNT_MS), label = "outerRing")
    val innerShown by animateFloatAsState(innerTarget, tween(COUNT_MS), label = "innerRing")

    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.10f
            val gap = stroke * 0.35f
            drawRing(outerShown, outerColor, trackColor, stroke, inset = stroke / 2)
            drawRing(innerShown, innerColor, trackColor, stroke, inset = stroke * 1.5f + gap)
        }
        content()
    }
}

private fun DrawScope.drawRing(progress: Float, color: Color, track: Color, stroke: Float, inset: Float) {
    val diameter = size.minDimension - inset * 2
    val topLeft = Offset(inset, inset)
    val arcSize = Size(diameter, diameter)
    val style = Stroke(width = stroke, cap = StrokeCap.Round)
    drawArc(track, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = style)
    if (progress > 0f) {
        drawArc(color, -90f, 360f * progress, useCenter = false, topLeft = topLeft, size = arcSize, style = style)
    }
}
