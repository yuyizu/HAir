package dev.hair.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.hair.app.data.Night
import java.time.format.DateTimeFormatter
import java.util.Locale

private val NIGHT_DATE = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)

// 分期配色参照 iOS 健康：清醒橙、REM 浅蓝、核心（浅睡）蓝、深睡靛
private val AwakeColor = Color(0xFFFF9F0A)
private val RemColor = Color(0xFF5AC8FA)
private val LightColor = Color(0xFF4C8DF6)
private val DeepColor = Color(0xFF5E5CE6)

@Composable
fun SleepTab(nights: List<Night>) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("睡眠", style = MaterialTheme.typography.headlineLarge) }
        if (nights.isEmpty()) {
            item { SummaryCard("睡眠", MaterialTheme.colorScheme.secondary) { ValueText(null) } }
        }
        items(nights, key = { it.date.toString() }) { NightCard(it) }
    }
}

@Composable
private fun NightCard(n: Night) {
    val colors = MaterialTheme.colorScheme
    SummaryCard(NIGHT_DATE.format(n.date), colors.secondary, Modifier.fillMaxWidth()) {
        ValueText(formatMinutes(n.asleepMin))
        Spacer(Modifier.height(12.dp))
        val stages = listOf(
            Stage("深睡", n.deepMin, DeepColor),
            Stage("核心", n.lightMin, LightColor),
            Stage("REM", n.remMin, RemColor),
            Stage("清醒", n.awakeMin, AwakeColor),
        ).filter { it.minutes > 0 }
        StageBar(stages)
        Spacer(Modifier.height(10.dp))
        stages.forEach { StageRow(it) }
    }
}

private data class Stage(val label: String, val minutes: Long, val color: Color)

/** 按分钟占比画分期条；没有分期数据（经典睡眠）时 [stages] 为空，不显示。 */
@Composable
private fun StageBar(stages: List<Stage>) {
    if (stages.isEmpty()) return
    Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))) {
        stages.forEach { Box(Modifier.weight(it.minutes.toFloat()).fillMaxSize().background(it.color)) }
    }
}

@Composable
private fun StageRow(s: Stage) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.size(8.dp).background(s.color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(s.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(formatMinutes(s.minutes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
