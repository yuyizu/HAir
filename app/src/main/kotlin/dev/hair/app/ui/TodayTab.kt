package dev.hair.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.hair.app.data.Deviation
import dev.hair.app.data.RecoveryFactor
import dev.hair.app.data.Recovery
import dev.hair.app.data.RecoveryMetric
import dev.hair.app.data.UserSettings
import dev.hair.app.data.deviation
import dev.hair.app.data.fitnessAge
import dev.hair.app.ui.theme.linkColor
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_FORMAT = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)
private val UPDATED_FORMAT = DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.CHINA)

/** 水景占屏幕高度的比例；剩下的是抽屉露出的指标行。 */
private const val SCENE_HEIGHT_FRACTION = 0.64f

/**
 * 水景加指标抽屉，同一个滚动列表：往上拉，抽屉盖过水景，露出更多指标。
 * [animateIn] 为 true 表示首次出场还没播过：水位涨起、数字从 0 滚起、指标行依次入场；
 * 播完后调用 [onIntroDone]，此后切回本页或滚动都不再重播，只有数据变化时数字接着滚。
 */
@Composable
fun TodayTab(
    state: UiState.Ready,
    settings: UserSettings,
    onRefresh: () -> Unit,
    animateIn: Boolean,
    onIntroDone: () -> Unit,
    recovery: Recovery?,
    onOpenDetail: () -> Unit,
    onOpenForecast: () -> Unit,
    onOpenNight: (LocalDate) -> Unit,
) {
    val snapshot = state.snapshot
    val o = snapshot.overview
    val goals = settings.goals
    val today = LocalDate.now()
    val steps = animatedCount(o.stepsToday, animateIn)
    val restingHr = animatedCount(o.restingHr, animateIn)
    val latestHr = animatedCount(o.latestHr, animateIn)
    val sleepVitals = o.lastNight?.let { night -> snapshot.vitals.firstOrNull { it.date == night.date } }
    val factors = recovery?.factors.orEmpty().associateBy { it.metric }
    val hrv = factors[RecoveryMetric.HRV]
    val buoy = hrv?.takeIf { it.deviation == Deviation.LOW }?.let { BuoyNote("心率变异性 ${"%.0f".format(it.value)} ms", "低于你的常态") }

    LaunchedEffect(Unit) {
        delay(INTRO_TOTAL_MS)
        onIntroDone()
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val listState = rememberLazyListState()
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                TideScene(
                    night = o.lastNight,
                    goalMin = goals.sleepMin,
                    weather = recovery?.weather,
                    scene = settings.scene,
                    today = today,
                    dateText = DATE_FORMAT.format(today),
                    buoy = buoy,
                    animateIn = animateIn,
                    onOpenNight = onOpenNight,
                    onOpenForecast = onOpenForecast,
                    scrollPx = { if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else Float.MAX_VALUE },
                    modifier = Modifier.height(maxHeight * SCENE_HEIGHT_FRACTION),
                )
            }
            item { Entrance(1, animateIn) { UpdateLine(state, onRefresh) } }
            item {
                Entrance(2, animateIn) {
                    MetricRow(
                        "步数",
                        value = steps?.let { "%,d".format(it) } ?: "—",
                        unit = "/ %,d".format(goals.steps),
                        status = goalProgress(o.stepsToday, goals.steps)?.let { "${(it * 100).toInt()}%" },
                        dot = MaterialTheme.colorScheme.primary,
                        progress = goalProgress(o.stepsToday, goals.steps),
                        onClick = onOpenDetail,
                    )
                }
            }
            restingHr?.let { rhr ->
                item { Entrance(3, animateIn) { BaselineMetricRow("静息心率", "$rhr", "bpm", factors[RecoveryMetric.RESTING_HR]) } }
            }
            o.hrvMs?.let { value ->
                item { Entrance(4, animateIn) { BaselineMetricRow("心率变异性", "%.0f".format(value), "ms", hrv) } }
            }
            sleepVitals?.spo2Avg?.let { value ->
                item { Entrance(5, animateIn) { MetricRow("血氧", "%.0f".format(value), "%") } }
            }
            sleepVitals?.respRate?.let { value ->
                item { Entrance(6, animateIn) { MetricRow("呼吸率", "%.1f".format(value), "/分") } }
            }
            latestHr?.let { bpm ->
                item { Entrance(7, animateIn) { MetricRow("最近心率", "$bpm", "bpm", status = "6 小时内") } }
            }
            o.cardioFitness?.let { cardio ->
                item {
                    Entrance(8, animateIn) {
                        MetricRow("心肺适能", cardio.level?.let(::cardioLevelLabel) ?: "—", "VO₂max %.1f".format(cardio.vo2Max))
                    }
                }
                val sex = settings.sex
                item {
                    Entrance(8, animateIn) {
                        if (sex == null) {
                            Hint("在「我的」里设置性别后，这里显示健康年龄。")
                        } else {
                            val age = fitnessAge(cardio.vo2Max, sex)
                            MetricRow("健康年龄", formatFitnessAge(age), settings.ageIn(today.year)?.let { compareToActualAge(age, it) })
                        }
                    }
                }
            }
            if (recovery == null) {
                item { Entrance(8, animateIn) { Hint("和你自己比较需要几天的记录，之后这里会标出偏离常态的指标。") } }
            }
        }
    }
}

/** 静息心率、HRV 这类有自己基线的指标：偏低、偏高用珊瑚色标出，没有基线时不下结论。 */
@Composable
private fun BaselineMetricRow(name: String, value: String, unit: String?, factor: RecoveryFactor?) {
    val deviation = factor?.deviation
    MetricRow(
        name,
        value,
        unit,
        status = when (deviation) {
            Deviation.LOW -> "偏低"
            Deviation.HIGH -> "偏高"
            Deviation.IN_RANGE -> "常态内"
            null -> null
        },
        deviated = deviation == Deviation.LOW || deviation == Deviation.HIGH,
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
    )
}

/** 抽屉第一行：更新时间和刷新；更新中、更新失败时换成对应的说明，失败用珊瑚色。 */
@Composable
private fun UpdateLine(state: UiState.Ready, onRefresh: () -> Unit) {
    val updatedAt = UPDATED_FORMAT.format(Instant.ofEpochMilli(state.snapshot.fetchedAtMillis).atZone(ZoneId.systemDefault()))
    val (text, color) = when {
        state.refreshing -> "正在更新…（当前是 $updatedAt 的数据）" to MaterialTheme.colorScheme.onSurfaceVariant
        state.refreshError != null -> "更新失败，显示的是 $updatedAt 的数据：${state.refreshError}" to MaterialTheme.colorScheme.error
        else -> "更新于 $updatedAt" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        // 状态文字换句时淡入淡出，用户能看出“开始更新 → 更新完成”
        Crossfade(targetState = text, animationSpec = tween(200), label = "status", modifier = Modifier.weight(1f)) { t ->
            Text(t, style = MaterialTheme.typography.bodySmall, color = color)
        }
        TextButton(onClick = onRefresh, enabled = !state.refreshing) { Text("刷新", color = linkColor()) }
    }
}
