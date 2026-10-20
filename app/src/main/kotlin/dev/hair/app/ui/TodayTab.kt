package dev.hair.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import dev.hair.app.ui.theme.linkColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// 环的默认目标，暂不可配置
private const val STEP_GOAL = 8_000L
private const val SLEEP_GOAL_MIN = 480L

private val DATE_FORMAT = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)
private val SHORT_DATE = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
private val UPDATED_FORMAT = DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.CHINA)

@Composable
fun TodayTab(state: UiState.Ready, onRefresh: () -> Unit) {
    val o = state.snapshot.overview
    val colors = MaterialTheme.colorScheme
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SummaryHeader(state, onRefresh) }
        item { SectionTitle("收藏") }
        item { ActivityCard(o) }
        item {
            SummaryCard("步数", colors.primary, time = "今天") {
                ValueText(o.stepsToday?.let { "%,d".format(it) }, "步")
            }
        }
        item {
            SummaryCard("睡眠", colors.secondary, time = o.lastNight?.date?.let(SHORT_DATE::format)) {
                ValueText(o.lastNight?.asleepMin?.let(::formatMinutes))
            }
        }
        item {
            SummaryCard("静息心率", colors.tertiary) { ValueText(o.restingHr?.toString(), "bpm") }
        }
        item {
            SummaryCard("心率", colors.tertiary, time = o.latestHr?.let { "6 小时内" }) {
                ValueText(o.latestHr?.toString(), "bpm")
            }
        }
        item {
            SummaryCard("心率变异性", colors.tertiary) { ValueText(o.hrvMs?.let { "%.0f".format(it) }, "ms") }
        }
        item {
            SummaryCard("心肺适能", colors.tertiary, time = o.cardioFitness?.date?.let(SHORT_DATE::format)) {
                ValueText(o.cardioFitness?.level?.let(::cardioLevelLabel))
                o.cardioFitness?.let {
                    Text(
                        "VO₂max %.1f ml/kg/min".format(it.vo2Max),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryHeader(state: UiState.Ready, onRefresh: () -> Unit) {
    val updatedAt = UPDATED_FORMAT.format(Instant.ofEpochMilli(state.snapshot.fetchedAtMillis).atZone(ZoneId.systemDefault()))
    val (text, color) = when {
        state.refreshing -> "正在更新…（当前是 $updatedAt 的数据）" to MaterialTheme.colorScheme.onSurfaceVariant
        state.refreshError != null -> "更新失败，显示的是 $updatedAt 的数据：${state.refreshError}" to MaterialTheme.colorScheme.error
        else -> "更新于 $updatedAt" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(DATE_FORMAT.format(LocalDate.now()), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("摘要", style = MaterialTheme.typography.headlineLarge)
            Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.padding(top = 2.dp))
        }
        TextButton(onClick = onRefresh, enabled = !state.refreshing) { Text("刷新", color = linkColor()) }
    }
}

@Composable
private fun ActivityCard(o: Overview) {
    val colors = MaterialTheme.colorScheme
    val steps = o.stepsToday
    val sleep = o.lastNight?.asleepMin
    SummaryCard("活动", colors.primary, time = "今天") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ActivityRings(
                outer = ringProgress(steps, STEP_GOAL),
                inner = ringProgress(sleep, SLEEP_GOAL_MIN),
                outerColor = colors.primary,
                innerColor = colors.secondary,
                trackColor = colors.surfaceVariant,
                modifier = Modifier.size(104.dp),
            ) {}
            Spacer(Modifier.width(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            Text(label, style = MaterialTheme.typography.labelLarge, color = color)
        }
        Text(
            value ?: "暂无数据",
            style = MaterialTheme.typography.titleMedium,
            color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}
