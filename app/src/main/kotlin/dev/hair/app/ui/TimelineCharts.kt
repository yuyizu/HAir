package dev.hair.app.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hair.app.data.HeartBucket
import dev.hair.app.data.Night
import dev.hair.app.data.SleepStage
import dev.hair.app.data.Spo2Point
import dev.hair.app.ui.theme.tide
import kotlin.math.ceil
import kotlin.math.floor

/** 分期在页面底色上的图表色（潮汐色阶，见 [dev.hair.app.ui.theme.TideColors.stageDeep]）；清醒是空心环，这里是它的描边色。 */
@Composable
fun stageColor(stage: SleepStage): Color {
    val sea = MaterialTheme.tide
    return when (stage) {
        SleepStage.AWAKE -> sea.stageAwake
        SleepStage.REM -> sea.stageRem
        SleepStage.LIGHT -> sea.stageLight
        SleepStage.DEEP -> sea.stageDeep
    }
}

fun stageLabel(stage: SleepStage): String = when (stage) {
    SleepStage.AWAKE -> "清醒"
    SleepStage.REM -> "REM"
    SleepStage.LIGHT -> "浅睡"
    SleepStage.DEEP -> "深睡"
}

/** 时间轴图表右侧给刻度文字留的宽度；睡眠、心率、血氧三张图共用，横轴才对得齐。 */
val AXIS_GUTTER = 44.dp

private val STAGE_ROWS = listOf(SleepStage.AWAKE, SleepStage.REM, SleepStage.LIGHT, SleepStage.DEEP)
private val SUMMARY_ORDER = listOf(SleepStage.DEEP, SleepStage.LIGHT, SleepStage.REM, SleepStage.AWAKE)
private const val AWAKE_RING_MIN_DP = 5
private const val AWAKE_RING_DP = 1.5f
private const val GRID_LINES = 3
private const val GAP_FACTOR = 2

/** 点按或横向拖动时，把横坐标换成 [t0]..[t1]（epoch 秒）之间的时刻；留给刻度的 [gutter] 不算在内。 */
private fun Modifier.scrub(t0: Long, t1: Long, gutter: Dp, onSelect: ((Long?) -> Unit)?): Modifier = composed {
    val current by rememberUpdatedState(onSelect)
    if (current == null) {
        Modifier
    } else {
        Modifier
            .pointerInput(t0, t1) {
                detectTapGestures { current?.invoke(secAt(it.x, size.width - gutter.toPx(), t0, t1)) }
            }
            .pointerInput(t0, t1) {
                detectHorizontalDragGestures(
                    onDragStart = { current?.invoke(secAt(it.x, size.width - gutter.toPx(), t0, t1)) },
                ) { change, _ -> current?.invoke(secAt(change.position.x, size.width - gutter.toPx(), t0, t1)) }
            }
    }
}

private fun secAt(x: Float, plotWidth: Float, t0: Long, t1: Long): Long =
    t0 + ((x / plotWidth).coerceIn(0f, 1f) * (t1 - t0)).toLong()

private fun xOf(sec: Long, t0: Long, t1: Long, plotWidth: Float): Float = (sec - t0).toFloat() / (t1 - t0) * plotWidth

private fun DrawScope.drawCursor(sec: Long?, t0: Long, t1: Long, plotWidth: Float, color: Color) {
    if (sec == null || sec < t0 || sec > t1) return
    val x = xOf(sec, t0, t1, plotWidth)
    drawLine(color.copy(alpha = 0.55f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.5.dp.toPx())
}

/**
 * 睡眠分期图：清醒、REM、浅睡、深睡各占一行（行中线有一条淡参考线），色块按时间铺开；
 * 清醒画成空心环，其余是实心块。
 * 传入 [onSelect] 后可点按或拖动选时刻，[selectedSec] 处画一条竖线；没有分期数据时不画。
 */
@Composable
fun Hypnogram(
    night: Night,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
    gutter: Dp = 0.dp,
    selectedSec: Long? = null,
    onSelect: ((Long?) -> Unit)? = null,
) {
    val (t0, t1) = night.windowSec ?: return
    val cursor = MaterialTheme.colorScheme.onSurface
    val guide = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.14f)
    val colors = SleepStage.entries.associateWith { stageColor(it) }
    Canvas(modifier.fillMaxWidth().height(height).scrub(t0, t1, gutter, onSelect)) {
        val plotWidth = size.width - gutter.toPx()
        val rowHeight = size.height / STAGE_ROWS.size
        val minWidth = 1.dp.toPx()
        val ring = AWAKE_RING_DP.dp.toPx()
        STAGE_ROWS.indices.forEach { row ->
            val y = (row + 0.5f) * rowHeight
            drawLine(guide, Offset(0f, y), Offset(plotWidth, y), strokeWidth = 1.dp.toPx())
        }
        night.stages.forEach { seg ->
            val x = xOf(seg.startSec, t0, t1, plotWidth)
            val width = (xOf(seg.endSec, t0, t1, plotWidth) - x).coerceAtLeast(minWidth)
            val top = STAGE_ROWS.indexOf(seg.stage) * rowHeight + rowHeight * 0.15f
            val blockHeight = rowHeight * 0.7f
            val radius = CornerRadius(rowHeight * 0.2f)
            val color = colors.getValue(seg.stage)
            // 窄到画不出环的清醒小段用实心，免得看不见
            if (seg.stage == SleepStage.AWAKE && width >= AWAKE_RING_MIN_DP.dp.toPx()) {
                drawRoundRect(color, Offset(x + ring / 2, top + ring / 2), Size(width - ring, blockHeight - ring), radius, style = Stroke(ring))
            } else {
                drawRoundRect(color, Offset(x, top), Size(width, blockHeight), radius)
            }
        }
        drawCursor(selectedSec, t0, t1, plotWidth, cursor)
    }
}

private fun minutesOf(n: Night, stage: SleepStage): Long = when (stage) {
    SleepStage.DEEP -> n.deepMin
    SleepStage.LIGHT -> n.lightMin
    SleepStage.REM -> n.remMin
    SleepStage.AWAKE -> n.awakeMin
}

/** 没有分期数据（经典睡眠）时的占位：按分钟占比画一条分期条；四项都是 0 就不画。 */
@Composable
fun StageBar(n: Night) {
    val parts = SUMMARY_ORDER.map { it to minutesOf(n, it) }.filter { it.second > 0 }
    if (parts.isEmpty()) return
    Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))) {
        parts.forEach { (stage, minutes) ->
            val color = stageColor(stage)
            Box(Modifier.weight(minutes.toFloat()).fillMaxHeight().background(if (stage == SleepStage.AWAKE) color.copy(alpha = 0.45f) else color))
        }
    }
}

/** 深睡、浅睡、REM、清醒各一列：图例点加时长。四项都是 0 就不画。 */
@Composable
fun StageSummary(n: Night, modifier: Modifier = Modifier) {
    if (SUMMARY_ORDER.all { minutesOf(n, it) == 0L }) return
    Row(modifier.fillMaxWidth()) {
        SUMMARY_ORDER.forEach { stage ->
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StageDot(stage)
                    Spacer(Modifier.width(6.dp))
                    Text(stageLabel(stage), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    formatHm(minutesOf(n, stage)),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** 图例点：和图里的画法一致，清醒是空心环。 */
@Composable
private fun StageDot(stage: SleepStage) {
    val color = stageColor(stage)
    Canvas(Modifier.size(8.dp)) {
        if (stage == SleepStage.AWAKE) {
            drawCircle(color, radius = size.minDimension / 2 - AWAKE_RING_DP.dp.toPx() / 2, style = Stroke(AWAKE_RING_DP.dp.toPx()))
        } else {
            drawCircle(color)
        }
    }
}

/** 时间轴下方的起止时刻，和图表同宽（右侧留出 [AXIS_GUTTER]）。 */
@Composable
fun TimeAxis(t0: Long, t1: Long, gutter: Dp = AXIS_GUTTER) {
    Row(Modifier.fillMaxWidth().padding(end = gutter, top = 4.dp)) {
        Text(clockText(t0), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(clockText(t1), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 纵轴取整到 [step] 的倍数，至少跨 [minSpan]。返回 (下限, 上限)。 */
private fun niceRange(lo: Double, hi: Double, step: Double, minSpan: Double): Pair<Double, Double> {
    val bottom = floor(lo / step) * step
    var top = ceil(hi / step) * step
    if (top - bottom < minSpan) top = bottom + minSpan
    return bottom to top
}

private fun DrawScope.drawGrid(paint: Paint, lo: Double, hi: Double, plotWidth: Float, top: Float, plotHeight: Float, grid: Color) {
    for (i in 0 until GRID_LINES) {
        val fraction = i / (GRID_LINES - 1f)
        val y = top + plotHeight * fraction
        drawLine(grid, Offset(0f, y), Offset(plotWidth, y), strokeWidth = 1.dp.toPx())
        drawIntoCanvas {
            paint.textAlign = Paint.Align.RIGHT
            it.nativeCanvas.drawText("%.0f".format(hi - (hi - lo) * fraction), size.width, y + paint.textSize * 0.35f, paint)
        }
    }
}

/** 相邻两点间隔超过 [maxGap] 秒就断开，缺数据的时段不连线。 */
private fun <T> runsOf(points: List<T>, secOf: (T) -> Long, maxGap: Long): List<List<T>> {
    val runs = mutableListOf<MutableList<T>>()
    points.forEach { p ->
        val last = runs.lastOrNull()
        if (last != null && secOf(p) - secOf(last.last()) <= maxGap) last.add(p) else runs.add(mutableListOf(p))
    }
    return runs
}

/**
 * 心率图：每个汇总窗口画最低到最高的浅色带和平均值折线。窗口宽 [spanSec] 秒，
 * 横轴是 [t0]..[t1]（epoch 秒）。[markers] 是要标出的时刻（竖虚线），[selectedSec] 处画游标。
 */
@Composable
fun HeartChart(
    buckets: List<HeartBucket>,
    t0: Long,
    t1: Long,
    spanSec: Long,
    color: Color,
    modifier: Modifier = Modifier,
    selectedSec: Long? = null,
    markers: List<Long> = emptyList(),
    onSelect: ((Long?) -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val textColor = colors.onSurfaceVariant.toArgb()
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val (lo, hi) = remember(buckets) {
        niceRange(buckets.minOfOrNull { it.minBpm } ?: 50.0, buckets.maxOfOrNull { it.maxBpm } ?: 100.0, step = 10.0, minSpan = 20.0)
    }
    Canvas(modifier.fillMaxWidth().height(CHART_HEIGHT).scrub(t0, t1, AXIS_GUTTER, onSelect)) {
        val plotWidth = size.width - AXIS_GUTTER.toPx()
        val plotTop = 6.dp.toPx()
        val plotHeight = size.height - plotTop * 2
        paint.textSize = 11.sp.toPx()
        paint.color = textColor
        drawGrid(paint, lo, hi, plotWidth, plotTop, plotHeight, colors.outlineVariant)

        fun y(bpm: Double) = plotTop + ((hi - bpm) / (hi - lo)).toFloat() * plotHeight
        fun x(b: HeartBucket) = xOf(b.startSec + spanSec / 2, t0, t1, plotWidth)

        runsOf(buckets, { it.startSec }, spanSec * GAP_FACTOR).forEach { run ->
            if (run.size == 1) {
                drawCircle(color, 3.dp.toPx(), Offset(x(run[0]), y(run[0].avgBpm)))
                return@forEach
            }
            val band = Path().apply {
                moveTo(x(run.first()), y(run.first().maxBpm))
                run.forEach { lineTo(x(it), y(it.maxBpm)) }
                run.asReversed().forEach { lineTo(x(it), y(it.minBpm)) }
                close()
            }
            drawPath(band, color.copy(alpha = 0.18f))
            val line = Path().apply {
                moveTo(x(run.first()), y(run.first().avgBpm))
                run.drop(1).forEach { lineTo(x(it), y(it.avgBpm)) }
            }
            drawPath(line, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        markers.forEach { sec ->
            if (sec in t0..t1) {
                val mx = xOf(sec, t0, t1, plotWidth)
                drawLine(colors.onSurfaceVariant, Offset(mx, plotTop), Offset(mx, plotTop + plotHeight), strokeWidth = 1.dp.toPx())
                drawCircle(colors.onSurface, 3.dp.toPx(), Offset(mx, plotTop))
            }
        }
        drawCursor(selectedSec, t0, t1, plotWidth, colors.onSurface)
    }
}

/** 血氧图：每个样本一个点，相邻点连线；纵轴上限固定 100%。 */
@Composable
fun Spo2Chart(
    points: List<Spo2Point>,
    t0: Long,
    t1: Long,
    color: Color,
    modifier: Modifier = Modifier,
    selectedSec: Long? = null,
    onSelect: ((Long?) -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val textColor = colors.onSurfaceVariant.toArgb()
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    // 上限固定 100，跨度取偶数（至少 6），中间的刻度才是整数
    val lo = remember(points) {
        val lowest = floor(points.minOfOrNull { it.percent } ?: 100.0)
        100.0 - maxOf(SPO2_MIN_SPAN, ceil((100.0 - lowest + 1) / 2).toInt() * 2)
    }
    Canvas(modifier.fillMaxWidth().height(CHART_HEIGHT).scrub(t0, t1, AXIS_GUTTER, onSelect)) {
        val plotWidth = size.width - AXIS_GUTTER.toPx()
        val plotTop = 6.dp.toPx()
        val plotHeight = size.height - plotTop * 2
        paint.textSize = 11.sp.toPx()
        paint.color = textColor
        drawGrid(paint, lo, 100.0, plotWidth, plotTop, plotHeight, colors.outlineVariant)

        fun y(percent: Double) = plotTop + ((100.0 - percent) / (100.0 - lo)).toFloat() * plotHeight
        fun x(p: Spo2Point) = xOf(p.sec, t0, t1, plotWidth)

        runsOf(points, { it.sec }, SPO2_MAX_GAP_SEC).forEach { run ->
            if (run.size > 1) {
                val line = Path().apply {
                    moveTo(x(run.first()), y(run.first().percent))
                    run.drop(1).forEach { lineTo(x(it), y(it.percent)) }
                }
                drawPath(line, color.copy(alpha = 0.6f), style = Stroke(1.dp.toPx(), join = StrokeJoin.Round))
            }
            run.forEach { drawCircle(color, 1.5.dp.toPx(), Offset(x(it), y(it.percent))) }
        }
        drawCursor(selectedSec, t0, t1, plotWidth, colors.onSurface)
    }
}

private val CHART_HEIGHT = 140.dp
private const val SPO2_MAX_GAP_SEC = 600L
private const val SPO2_MIN_SPAN = 6
