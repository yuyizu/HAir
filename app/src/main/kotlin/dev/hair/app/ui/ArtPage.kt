package dev.hair.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.hair.app.data.ART_CAP
import dev.hair.app.data.ArtDay
import dev.hair.app.data.DailyVitals
import dev.hair.app.data.Goals
import dev.hair.app.data.Night
import dev.hair.app.data.artDays
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val ART_DAYS = 30
private const val SPOKE_OFFSET_DEG = 2.4

/**
 * 30 天图：每天两根从圆心向外的辐条，浅海色是睡眠、赭金色是步数，长度是目标的几分之几，
 * 细圆环是目标线，今天在正上方，每早一天逆时针转一格。最外圈的小点是当天的静息心率（越红越高）。
 */
@Composable
fun ArtPage(nights: List<Night>, vitals: List<DailyVitals>, goals: Goals, onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val days = artDays(nights, vitals, goals, LocalDate.now(), ART_DAYS)
    val rhrRange = days.mapNotNull { it.restingHr }.let { if (it.isEmpty()) null else it.min() to it.max() }
    PageScaffold("探索", onBack, "30 天图") {
        Block {
            val sleepColor = colors.secondary
            val stepColor = colors.primary
            val ringColor = colors.outlineVariant
            val heartColor = colors.tertiary
            Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
                val center = Offset(size.width / 2, size.height / 2)
                val inner = size.minDimension * 0.12f
                val outer = size.minDimension * 0.5f - 6.dp.toPx()
                // 目标线对应 1.0，辐条最长到 ART_CAP，再留一点位置给末端的小点
                val unitLength = (outer - 10.dp.toPx() - inner) / ART_CAP
                drawCircle(ringColor, inner + unitLength, center, style = Stroke(1.dp.toPx()))
                days.forEachIndexed { i, d ->
                    // 今天在正上方，往前一天顺时针转一格
                    val base = -90.0 - 360.0 * (days.lastIndex - i) / days.size
                    spoke(center, inner, unitLength, base - SPOKE_OFFSET_DEG, d.sleepFraction, sleepColor)
                    spoke(center, inner, unitLength, base + SPOKE_OFFSET_DEG, d.stepFraction, stepColor)
                    dot(d, rhrRange, center, outer, base, heartColor)
                }
            }
            Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendDot(sleepColor, "睡眠")
                LegendDot(stepColor, "步数")
                LegendDot(heartColor, "静息心率")
            }
            Text(
                "圆环是目标线：睡眠 ${formatSleepGoal(goals.sleepMin)}，步数 %,d".format(goals.steps),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Footnote("没有记录的日子辐条是空的。长度封顶在目标的 ${(ART_CAP * 100).toInt()}%，超出一点也画出来，再多不再变长。")
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp))
    }
}

private fun DrawScope.spoke(center: Offset, inner: Float, unitLength: Float, degrees: Double, fraction: Float, color: Color) {
    if (fraction <= 0f) return
    val dx = cos(degrees * PI / 180).toFloat()
    val dy = sin(degrees * PI / 180).toFloat()
    drawLine(
        color,
        Offset(center.x + dx * inner, center.y + dy * inner),
        Offset(center.x + dx * (inner + unitLength * fraction), center.y + dy * (inner + unitLength * fraction)),
        strokeWidth = 4.dp.toPx(),
        cap = StrokeCap.Round,
    )
}

/** 静息心率在这 30 天里越高越红（透明度越高）；只有一个值或没有值时画成中间色。 */
private fun DrawScope.dot(
    d: ArtDay,
    range: Pair<Long, Long>?,
    center: Offset,
    radius: Float,
    degrees: Double,
    color: Color,
) {
    val hr = d.restingHr ?: return
    val (lo, hi) = range ?: return
    val level = if (hi > lo) (hr - lo).toFloat() / (hi - lo) else 0.5f
    val dx = cos(degrees * PI / 180).toFloat()
    val dy = sin(degrees * PI / 180).toFloat()
    drawCircle(color.copy(alpha = 0.25f + 0.75f * level), 2.5.dp.toPx(), Offset(center.x + dx * radius, center.y + dy * radius))
}
