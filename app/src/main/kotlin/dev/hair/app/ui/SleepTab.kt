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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
private val AwakeColor = Color(0xFFF5A623)

@Composable
fun SleepTab(nights: List<Night>) {
    if (nights.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("最近 14 天没有睡眠数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("睡眠", style = MaterialTheme.typography.headlineSmall) }
        items(nights, key = { it.date.toString() }) { NightCard(it) }
    }
}

@Composable
private fun NightCard(n: Night) {
    val colors = MaterialTheme.colorScheme
    HairCard(Modifier.fillMaxWidth()) {
        Text(NIGHT_DATE.format(n.date), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
        Text(formatMinutes(n.asleepMin), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        StageBar(
            listOf(n.deepMin to colors.secondary, n.remMin to colors.tertiary, n.lightMin to colors.secondary.copy(alpha = 0.35f), n.awakeMin to AwakeColor),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "深睡 ${formatMinutes(n.deepMin)} · REM ${formatMinutes(n.remMin)} · 浅睡 ${formatMinutes(n.lightMin)} · 清醒 ${formatMinutes(n.awakeMin)}",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }
}

/** 按分钟占比画分期条；没有分期数据（经典睡眠）时不显示。 */
@Composable
private fun StageBar(stages: List<Pair<Long, Color>>) {
    val segments = stages.filter { it.first > 0 }
    if (segments.isEmpty()) return
    Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))) {
        segments.forEach { (minutes, color) ->
            Box(Modifier.weight(minutes.toFloat()).fillMaxSize().background(color))
        }
    }
}
