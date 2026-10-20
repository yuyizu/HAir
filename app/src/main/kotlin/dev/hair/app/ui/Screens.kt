package dev.hair.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.hair.app.data.CardioFitness
import dev.hair.app.data.CardioLevel
import dev.hair.app.data.Night
import dev.hair.app.data.Overview
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DeepColor = Color(0xFF3F51B5)
private val RemColor = Color(0xFF9C27B0)
private val LightColor = Color(0xFF90CAF9)
private val AwakeColor = Color(0xFFFFB74D)

@Composable
fun HairScreen(state: UiState, onSignIn: () -> Unit, onRefresh: () -> Unit, onSignOut: () -> Unit) {
    when (state) {
        UiState.Loading -> Centered { CircularProgressIndicator() }
        UiState.SignedOut -> SignedOutScreen(onSignIn)
        is UiState.Error -> Centered {
            Text(state.message, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onRefresh) { Text("重试") }
                OutlinedButton(onClick = onSignOut) { Text("退出登录") }
            }
        }
        is UiState.Ready -> ReadyScreen(state, onRefresh, onSignOut)
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
private fun SignedOutScreen(onSignIn: () -> Unit) = Centered {
    Text("HAir", style = MaterialTheme.typography.displaySmall)
    Spacer(Modifier.height(12.dp))
    // Google 要求在使用过程中明确说明访问哪些健康数据及用途，隐私政策不能替代
    Text(
        "HAir 会读取你 Fitbit Air 的睡眠、心率、心率变异性和步数数据，" +
            "仅用于在本机展示睡眠与恢复情况。数据不会上传到任何服务器。",
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(24.dp))
    Button(onClick = onSignIn) { Text("使用 Google 账号连接") }
}

@Composable
private fun ReadyScreen(state: UiState.Ready, onRefresh: () -> Unit, onSignOut: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = {}, label = { Text("今日") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = {}, label = { Text("睡眠") })
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).statusBarsPadding().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = onRefresh) { Text("刷新") }
                Spacer(Modifier.padding(4.dp))
                OutlinedButton(onClick = onSignOut) { Text("退出") }
            }
            StatusLine(state)
            if (tab == 0) TodayTab(state.snapshot.overview) else SleepTab(state.snapshot.nights)
        }
    }
}

@Composable
private fun StatusLine(state: UiState.Ready) {
    val updatedAt = TIME_FORMAT.format(Instant.ofEpochMilli(state.snapshot.fetchedAtMillis).atZone(ZoneId.systemDefault()))
    val text = when {
        state.refreshing -> "正在更新…（当前显示 $updatedAt 的数据）"
        state.refreshError != null -> "更新失败，显示的是 $updatedAt 的数据：${state.refreshError}"
        else -> "更新于 $updatedAt"
    }
    val color = if (state.refreshError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.padding(top = 4.dp))
}

private val TIME_FORMAT = DateTimeFormatter.ofPattern("M月d日 HH:mm")

@Composable
private fun TodayTab(o: Overview) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
        item {
            val n = o.lastNight
            MetricCard("昨晚睡眠", n?.let { formatMinutes(it.asleepMin) } ?: "暂无数据", n?.let { "卧床 ${formatMinutes(it.inBedMin)}" })
        }
        item { MetricCard("静息心率", o.restingHr?.let { "$it bpm" } ?: "暂无数据") }
        item { MetricCard("HRV", o.hrvMs?.let { "%.0f ms".format(it) } ?: "暂无数据") }
        item { CardioFitnessCard(o.cardioFitness) }
        item { MetricCard("今日步数", o.stepsToday?.toString() ?: "暂无数据") }
        item { MetricCard("最近心率（6 小时内）", o.latestHr?.let { "$it bpm" } ?: "暂无数据") }
    }
}

@Composable
private fun MetricCard(title: String, value: String, sub: String? = null) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.headlineMedium)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SleepTab(nights: List<Night>) {
    if (nights.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("最近 14 天没有睡眠数据") }
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
        items(nights, key = { it.date.toString() }) { NightCard(it) }
    }
}

@Composable
private fun NightCard(n: Night) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(n.date.toString(), style = MaterialTheme.typography.labelLarge)
            Text(formatMinutes(n.asleepMin), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            StageBar(n)
            Spacer(Modifier.height(8.dp))
            Text(
                "深睡 ${formatMinutes(n.deepMin)} · REM ${formatMinutes(n.remMin)} · " +
                    "浅睡 ${formatMinutes(n.lightMin)} · 清醒 ${formatMinutes(n.awakeMin)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** 按分钟占比画分期条；没有分期数据（经典睡眠）时不显示。 */
@Composable
private fun StageBar(n: Night) {
    val segments = listOf(n.deepMin to DeepColor, n.remMin to RemColor, n.lightMin to LightColor, n.awakeMin to AwakeColor)
        .filter { it.first > 0 }
    if (segments.isEmpty()) return
    Row(Modifier.fillMaxWidth().height(10.dp)) {
        segments.forEach { (minutes, color) ->
            Box(Modifier.weight(minutes.toFloat()).fillMaxSize().background(color))
        }
    }
}

fun formatMinutes(minutes: Long): String = "${minutes / 60} 小时 ${minutes % 60} 分"

@Composable
private fun CardioFitnessCard(c: CardioFitness?) {
    val value = c?.level?.let(::cardioLevelLabel) ?: if (c != null) "暂无等级" else "暂无数据"
    MetricCard("心肺适能", value, c?.let { "VO₂max %.1f ml/kg/min · %s".format(it.vo2Max, it.date) })
}

private fun cardioLevelLabel(level: CardioLevel): String = when (level) {
    CardioLevel.POOR -> "较差"
    CardioLevel.FAIR -> "一般"
    CardioLevel.AVERAGE -> "平均"
    CardioLevel.GOOD -> "良好"
    CardioLevel.VERY_GOOD -> "很好"
    CardioLevel.EXCELLENT -> "优秀"
}
