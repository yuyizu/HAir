package dev.hair.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.hair.app.data.DailyVitals
import dev.hair.app.data.Night
import dev.hair.app.ui.theme.TideNumeral
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val WEEK = 7
private val WEEKDAY = DateTimeFormatter.ofPattern("E", Locale.CHINA)
private val COLUMN_WIDTH = 24.dp
private val TIDE_HEIGHT = 104.dp

/**
 * 睡眠：页眉下是近 7 晚的水位柱（满格是睡眠目标，和今日页的水位同一个意思），再往下每晚一行。
 * [animateIn] 为 true 表示这个标签页还没出场过：水位柱涨起、行依次入场。
 */
@Composable
fun SleepTab(nights: List<Night>, vitals: List<DailyVitals>, goalMin: Long, animateIn: Boolean, onOpenNight: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    val vitalsByDate = vitals.associateBy { it.date }
    val recent = nights.sortedBy { it.date }.takeLast(WEEK)
    val subtitle = recent.takeIf { it.isNotEmpty() }?.let { "近 ${it.size} 晚平均 ${formatMinutes(it.map { n -> n.asleepMin }.average().toLong())}" }
    TabPage("睡眠", subtitle) {
        if (nights.isEmpty()) {
            item { Block { Text("暂无睡眠记录", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            return@TabPage
        }
        item { Entrance(0, animateIn) { WeekTide(recent, goalMin, animateIn, onOpenNight) } }
        item { SectionLabel("每晚") }
        itemsIndexed(nights, key = { _, n -> n.date.toString() }) { i, n ->
            Entrance(i + 1, animateIn) { NightRow(n, vitalsByDate[n.date], today, goalMin) { onOpenNight(n.date) } }
        }
    }
}

/**
 * 近几晚的水位：每晚一根细柱，水面高度是睡眠时长占目标的比例，柱顶就是目标线。
 * 点按一根柱打开那一晚。水面从底部涨起（弹簧，不回弹），只在首次出场时播。
 */
@Composable
private fun WeekTide(nights: List<Night>, goalMin: Long, animateIn: Boolean, onOpen: (LocalDate) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val rise = remember { Animatable(if (animateIn) 0f else 1f) }
    LaunchedEffect(Unit) { if (rise.value < 1f) rise.animateTo(1f, spring(dampingRatio = 1f, stiffness = 170f)) }
    val description = nights.joinToString("，") { "${WEEKDAY.format(it.date)} ${formatMinutes(it.asleepMin)}" }
    Block(vertical = 8.dp) {
        Text(
            "满格是目标 ${formatSleepGoal(goalMin)}",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        val water = colors.secondary
        val track = colors.onSurfaceVariant
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(TIDE_HEIGHT)
                .semantics { contentDescription = "近 ${nights.size} 晚睡眠水位：$description" }
                .pointerInput(nights) {
                    detectTapGestures { tap -> onOpen(nights[(tap.x / (size.width / nights.size)).toInt().coerceIn(0, nights.lastIndex)].date) }
                },
        ) {
            val slot = size.width / nights.size
            val width = minOf(COLUMN_WIDTH.toPx(), slot * 0.7f)
            nights.forEachIndexed { i, n ->
                val x = slot * i + (slot - width) / 2
                val shape = Path().apply { addRoundRect(RoundRect(x, 0f, x + width, size.height, CornerRadius(width / 2))) }
                drawPath(shape, track.copy(alpha = 0.1f))
                val level = (goalProgress(n.asleepMin, goalMin) ?: 0f) * rise.value
                val surface = size.height * (1f - level)
                clipPath(shape) {
                    drawRect(
                        Brush.verticalGradient(listOf(water.copy(alpha = 0.55f), water), startY = surface, endY = size.height),
                        Offset(x, surface),
                        Size(width, size.height - surface),
                    )
                }
                drawPath(shape, track.copy(alpha = 0.35f), style = Stroke(1.dp.toPx()))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            nights.forEachIndexed { i, n ->
                Text(
                    WEEKDAY.format(n.date),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (i == nights.lastIndex) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (i == nights.lastIndex) colors.onSurface else colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NightRow(n: Night, vitals: DailyVitals?, today: LocalDate, goalMin: Long, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val label = if (n.date == today) "昨夜 · ${nightDateText(n.date)}" else nightDateText(n.date)
    RowFrame(onClick = onClick, verticalPadding = 16.dp) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(formatHm(n.asleepMin), style = TideNumeral)
            Text(
                "/ ${formatHm(goalMin)}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp).weight(1f),
            )
            Text(
                "${n.asleepMin * 100 / goalMin}%",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        if (n.stages.isEmpty()) StageBar(n) else Hypnogram(n, height = 44.dp)
        Spacer(Modifier.height(12.dp))
        StageSummary(n)
        vitals?.let(::nightVitalsText)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

/** 这一晚的血氧、皮温偏离、呼吸率，有几项显示几项；一项都没有返回 null。 */
fun nightVitalsText(v: DailyVitals): String? = listOfNotNull(
    v.spo2Avg?.let { "血氧 %.0f%%".format(it) },
    v.skinTempDeltaC?.let { "皮温 ${formatDeltaC(it)}" },
    v.respRate?.let { "呼吸 %.1f 次/分".format(it) },
).takeIf { it.isNotEmpty() }?.joinToString(" · ")
