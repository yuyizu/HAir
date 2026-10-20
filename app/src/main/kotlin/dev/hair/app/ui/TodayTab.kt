package dev.hair.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.hair.app.data.Overview
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// 环的默认目标，暂不可配置
private const val STEP_GOAL = 8_000L
private const val SLEEP_GOAL_MIN = 480L

private val DATE_FORMAT = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)
private val UPDATED_FORMAT = DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.CHINA)

@Composable
fun TodayTab(state: UiState.Ready, onRefresh: () -> Unit) {
    val o = state.snapshot.overview
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { TodayHeader(state, onRefresh) }
        item { ActivityHero(o) }
        item {
            TileRow(
                { MetricTile("静息心率", o.restingHr?.toString(), "bpm", null, it) },
                { MetricTile("最近心率", o.latestHr?.toString(), "bpm", o.latestHr?.let { "6 小时内" }, it) },
            )
        }
        item {
            TileRow(
                { MetricTile("HRV", o.hrvMs?.let { "%.0f".format(it) }, "ms", null, it) },
                { MetricTile("心肺适能", o.cardioFitness?.level?.let(::cardioLevelLabel), null, o.cardioFitness?.let { "VO₂max %.1f".format(it.vo2Max) }, it) },
            )
        }
    }
}

@Composable
private fun TodayHeader(state: UiState.Ready, onRefresh: () -> Unit) {
    val updatedAt = UPDATED_FORMAT.format(Instant.ofEpochMilli(state.snapshot.fetchedAtMillis).atZone(ZoneId.systemDefault()))
    val (text, color) = when {
        state.refreshing -> "正在更新…（当前是 $updatedAt 的数据）" to MaterialTheme.colorScheme.onSurfaceVariant
        state.refreshError != null -> "更新失败，显示的是 $updatedAt 的数据：${state.refreshError}" to MaterialTheme.colorScheme.error
        else -> "更新于 $updatedAt" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(DATE_FORMAT.format(LocalDate.now()), style = MaterialTheme.typography.headlineSmall)
            Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.padding(top = 2.dp))
        }
        TextButton(onClick = onRefresh, enabled = !state.refreshing) { Text("刷新") }
    }
}

@Composable
private fun ActivityHero(o: Overview) {
    val colors = MaterialTheme.colorScheme
    val steps = o.stepsToday
    val sleep = o.lastNight?.asleepMin
    HairCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ActivityRings(
                outer = ringProgress(steps, STEP_GOAL),
                inner = ringProgress(sleep, SLEEP_GOAL_MIN),
                outerColor = colors.primary,
                innerColor = colors.secondary,
                trackColor = colors.surfaceVariant,
                modifier = Modifier.size(132.dp),
            ) {
                Text(steps?.let { "%,d".format(it) } ?: "—", style = MaterialTheme.typography.headlineSmall)
            }
            Spacer(Modifier.width(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                RingLegend("步数", colors.primary, steps?.let { "%,d / %,d".format(it, STEP_GOAL) })
                RingLegend("睡眠", colors.secondary, sleep?.let { "${formatMinutes(it)} / ${SLEEP_GOAL_MIN / 60} 小时" })
            }
        }
    }
}

@Composable
private fun RingLegend(label: String, color: Color, value: String?) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.size(8.dp).background(color, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            value ?: "暂无数据",
            style = MaterialTheme.typography.titleMedium,
            color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 两个等宽等高的指标卡并排。 */
@Composable
private fun TileRow(left: @Composable (Modifier) -> Unit, right: @Composable (Modifier) -> Unit) {
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        left(Modifier.weight(1f).fillMaxHeight())
        right(Modifier.weight(1f).fillMaxHeight())
    }
}
