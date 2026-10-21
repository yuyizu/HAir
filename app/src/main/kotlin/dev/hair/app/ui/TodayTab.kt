package dev.hair.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.hair.app.data.Goals
import dev.hair.app.data.Overview
import dev.hair.app.data.UserSettings
import dev.hair.app.data.fitnessAge
import dev.hair.app.ui.theme.linkColor
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_FORMAT = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)
private val SHORT_DATE = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
private val UPDATED_FORMAT = DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.CHINA)

/**
 * [animateIn] 为 true 表示首次出场还没播过：卡片依次入场，圆环和数字从 0 涨起；
 * 播完后调用 [onIntroDone]，此后切回本页或滚动都不再重播，只有数据变化时数字接着滚。
 */
@Composable
fun TodayTab(
    state: UiState.Ready,
    settings: UserSettings,
    onRefresh: () -> Unit,
    animateIn: Boolean,
    onIntroDone: () -> Unit,
    onOpenDetail: () -> Unit,
) {
    val o = state.snapshot.overview
    val colors = MaterialTheme.colorScheme
    val steps = animatedCount(o.stepsToday, animateIn)
    val restingHr = animatedCount(o.restingHr, animateIn)
    val latestHr = animatedCount(o.latestHr, animateIn)

    LaunchedEffect(Unit) {
        delay(INTRO_TOTAL_MS)
        onIntroDone()
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Entrance(0, animateIn) { SummaryHeader(state, onRefresh) } }
        item { Entrance(1, animateIn) { SectionTitle("收藏") } }
        item { Entrance(2, animateIn) { ActivityCard(o, settings.goals, steps, animateIn, onOpenDetail) } }
        item {
            Entrance(3, animateIn) {
                SummaryCard("步数", colors.primary, time = "今天", onClick = onOpenDetail) { ValueText(steps?.let { "%,d".format(it) }, "步") }
            }
        }
        item {
            Entrance(4, animateIn) {
                SummaryCard("睡眠", colors.secondary, time = o.lastNight?.date?.let(SHORT_DATE::format)) {
                    ValueText(o.lastNight?.asleepMin?.let(::formatMinutes))
                }
            }
        }
        item {
            Entrance(5, animateIn) {
                SummaryCard("静息心率", colors.tertiary) { ValueText(restingHr?.toString(), "bpm") }
            }
        }
        item {
            Entrance(6, animateIn) {
                SummaryCard("心率", colors.tertiary, time = o.latestHr?.let { "6 小时内" }) {
                    ValueText(latestHr?.toString(), "bpm")
                }
            }
        }
        item {
            Entrance(7, animateIn) {
                SummaryCard("心率变异性", colors.tertiary) { ValueText(o.hrvMs?.let { "%.0f".format(it) }, "ms") }
            }
        }
        item {
            Entrance(8, animateIn) {
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
        item { Entrance(9, animateIn) { FitnessAgeCard(o, settings) } }
    }
}

@Composable
private fun FitnessAgeCard(o: Overview, settings: UserSettings) {
    val colors = MaterialTheme.colorScheme
    val cardio = o.cardioFitness
    val sex = settings.sex
    SummaryCard("健康年龄", colors.tertiary, time = cardio?.date?.let(SHORT_DATE::format)) {
        when {
            cardio == null -> ValueText(null)
            sex == null -> Text("在「我的」里设置性别后显示", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            else -> {
                val age = fitnessAge(cardio.vo2Max, sex)
                ValueText(formatFitnessAge(age))
                settings.ageIn(LocalDate.now().year)?.let { compareToActualAge(age, it) }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
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
            // 状态文字换句时淡入淡出，用户能看出“开始更新 → 更新完成”
            Crossfade(targetState = text, animationSpec = tween(200), label = "status") { t ->
                Text(t, style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.padding(top = 2.dp))
            }
        }
        TextButton(onClick = onRefresh, enabled = !state.refreshing) { Text("刷新", color = linkColor()) }
    }
}

@Composable
private fun ActivityCard(o: Overview, goals: Goals, steps: Long?, animateIn: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val sleep = o.lastNight?.asleepMin
    SummaryCard("活动", colors.primary, time = "今天", onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ActivityRings(
                outer = ringProgress(o.stepsToday, goals.steps),
                inner = ringProgress(sleep, goals.sleepMin),
                outerColor = colors.primary,
                innerColor = colors.secondary,
                trackColor = colors.surfaceVariant,
                animateFromZero = animateIn,
                modifier = Modifier.size(104.dp),
            ) {}
            Spacer(Modifier.width(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RingLegend("步数", colors.primary, steps?.let { "%,d / %,d".format(it, goals.steps) })
                RingLegend("睡眠", colors.secondary, sleep?.let { "${formatMinutes(it)} / ${formatSleepGoal(goals.sleepMin)}" })
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
