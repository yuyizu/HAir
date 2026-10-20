package dev.hair.app.ui

import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hair.app.data.DetailRange
import dev.hair.app.data.Series
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

private val WEEKDAY = DateTimeFormatter.ofPattern("EEE", Locale.CHINA)
private val MONTH_DAY = DateTimeFormatter.ofPattern("M/d", Locale.CHINA)
private val LONG_DATE = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)

private const val MONTH_LABEL_STEP = 7
private const val GRID_LINES = 3
private const val BAR_FILL = 0.62f
private const val DIMMED_ALPHA = 0.35f
private const val BARS_MS = 500

private val CHART_HEIGHT = 220.dp
private val Y_LABEL_WIDTH = 48.dp
private val TOP_PAD = 8.dp
private val X_LABEL_HEIGHT = 24.dp

/** 纵轴上限：不小于 [peak] 的 1、2、5 × 10ⁿ，且至少为 10，保证中间刻度是整数。 */
fun axisMax(peak: Double): Double {
    if (peak <= 0.0) return 10.0
    val base = 10.0.pow(floor(log10(peak)))
    val nice = when {
        peak / base <= 1.0 -> 1.0
        peak / base <= 2.0 -> 2.0
        peak / base <= 5.0 -> 5.0
        else -> 10.0
    }
    return max(nice * base, 10.0)
}

fun bucketDate(series: Series, index: Int): LocalDate = series.start.plusDays(index.toLong())

/** 横轴要标出的 (桶下标, 文字)：日档每 6 小时一个，周档每天一个，月档从今天起每 7 天一个。 */
fun axisLabels(series: Series): List<Pair<Int, String>> = when (series.range) {
    DetailRange.DAY -> listOf(0, 6, 12, 18).map { it to "${it}时" }
    DetailRange.WEEK -> series.values.indices.map { it to bucketDate(series, it).format(WEEKDAY) }
    DetailRange.MONTH -> series.values.indices
        .filter { (series.values.lastIndex - it) % MONTH_LABEL_STEP == 0 }
        .map { it to bucketDate(series, it).format(MONTH_DAY) }
}

/** 选中某个桶时展示的时间描述。 */
fun bucketLabel(series: Series, index: Int): String = when (series.range) {
    DetailRange.DAY -> "%02d:00–%02d:00".format(index, index + 1)
    else -> bucketDate(series, index).format(LONG_DATE)
}

/**
 * 柱状图：点按或横向拖动选中一根柱，[selected] 为选中的下标（null 表示未选）。
 * 其余柱在有选中时变淡；数据变化时所有柱从基线长出。
 */
@Composable
fun BarChart(
    series: Series,
    color: Color,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val progress = remember(series) { Animatable(0f) }
    LaunchedEffect(series) { progress.animateTo(1f, tween(BARS_MS, easing = FastOutSlowInEasing)) }

    val labels = remember(series) { axisLabels(series) }
    val axisTop = axisMax(series.values.maxOrNull() ?: 0.0)
    val textColor = colors.onSurfaceVariant.toArgb()
    val gridColor = colors.outlineVariant
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelect by rememberUpdatedState(onSelect)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT)
            .pointerInput(series) {
                val plotWidth = size.width - Y_LABEL_WIDTH.toPx()
                detectTapGestures { tap ->
                    val index = barAt(tap.x, plotWidth, series.values.size)
                    currentOnSelect(if (index == currentSelected) null else index)
                }
            }
            .pointerInput(series) {
                val plotWidth = size.width - Y_LABEL_WIDTH.toPx()
                detectHorizontalDragGestures(
                    onDragStart = { currentOnSelect(barAt(it.x, plotWidth, series.values.size)) },
                ) { change, _ ->
                    currentOnSelect(barAt(change.position.x, plotWidth, series.values.size))
                }
            },
    ) {
        val plotWidth = size.width - Y_LABEL_WIDTH.toPx()
        val plotTop = TOP_PAD.toPx()
        val plotHeight = size.height - plotTop - X_LABEL_HEIGHT.toPx()
        val slot = plotWidth / series.values.size
        val barWidth = slot * BAR_FILL
        val labelSize = 11.sp.toPx()

        paint.textSize = labelSize
        paint.color = textColor

        // 网格线与右侧刻度：顶部是上限，底部是 0
        for (i in 0 until GRID_LINES) {
            val fraction = i / (GRID_LINES - 1f)
            val y = plotTop + plotHeight * fraction
            drawLine(gridColor, Offset(0f, y), Offset(plotWidth, y), strokeWidth = 1.dp.toPx())
            drawIntoCanvas {
                paint.textAlign = Paint.Align.RIGHT
                it.nativeCanvas.drawText("%,.0f".format(axisTop * (1 - fraction)), size.width, y + labelSize * 0.35f, paint)
            }
        }

        // 柱子多画一个圆角高度再裁掉基线以下的部分，只有顶部是圆角
        val radius = minOf(barWidth / 2, 4.dp.toPx())
        clipRect(right = plotWidth, bottom = plotTop + plotHeight) {
            series.values.forEachIndexed { i, value ->
                if (value <= 0.0) return@forEachIndexed
                val full = (value / axisTop).toFloat().coerceIn(0f, 1f) * plotHeight
                val height = max(full, 2.dp.toPx()) * progress.value
                drawRoundRect(
                    color = if (selected == null || selected == i) color else color.copy(alpha = DIMMED_ALPHA),
                    topLeft = Offset(slot * i + (slot - barWidth) / 2, plotTop + plotHeight - height),
                    size = Size(barWidth, height + radius),
                    cornerRadius = CornerRadius(radius),
                )
            }
        }

        drawIntoCanvas {
            paint.textAlign = Paint.Align.CENTER
            labels.forEach { (i, text) ->
                it.nativeCanvas.drawText(text, slot * (i + 0.5f), size.height - labelSize * 0.4f, paint)
            }
        }
    }
}

private fun barAt(x: Float, plotWidth: Float, count: Int): Int = (x / plotWidth * count).toInt().coerceIn(0, count - 1)
