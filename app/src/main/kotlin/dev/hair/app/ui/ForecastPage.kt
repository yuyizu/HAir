package dev.hair.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import dev.hair.app.data.Recovery
import dev.hair.app.data.RecoveryFactor
import dev.hair.app.data.RecoveryMetric
import dev.hair.app.data.SkinTempSignal
import dev.hair.app.data.Weather
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val SHORT_DATE = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
private const val NEAR_BASELINE = 0.15

fun weatherLabel(weather: Weather): String = when (weather) {
    Weather.SUNNY -> "晴"
    Weather.FAIR -> "少云"
    Weather.CLOUDY -> "多云"
    Weather.OVERCAST -> "阴"
}

fun weatherSummary(weather: Weather): String = when (weather) {
    Weather.SUNNY -> "整体比你近期的水平更好"
    Weather.FAIR -> "和你近期的水平差不多"
    Weather.CLOUDY -> "比你近期的水平略低"
    Weather.OVERCAST -> "比你近期的水平明显偏低"
}

private fun metricName(metric: RecoveryMetric): String = when (metric) {
    RecoveryMetric.HRV -> "心率变异性"
    RecoveryMetric.RESTING_HR -> "静息心率"
    RecoveryMetric.SLEEP -> "睡眠时长"
}

private fun metricValue(metric: RecoveryMetric, value: Double): String = when (metric) {
    RecoveryMetric.HRV -> "%.0f ms".format(value)
    RecoveryMetric.RESTING_HR -> "%.0f bpm".format(value)
    RecoveryMetric.SLEEP -> formatMinutes(value.toLong())
}

/**
 * 身体天气详情：天气结论、每项指标和自己近期中位数的比较、夜间腕部皮温偏离。
 * [recovery] 为 null 表示数据还不够；[nightCount] 和 [vitalsDays] 用来告诉用户已经积累了多少。
 */
@Composable
fun ForecastPage(recovery: Recovery?, skin: SkinTempSignal?, nightCount: Int, vitalsDays: Int, onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    PageScaffold("今日", onBack, "身体天气") {
        Block {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (recovery != null) {
                    WeatherGlyph(recovery.weather, Modifier.size(72.dp))
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(weatherLabel(recovery.weather), style = MaterialTheme.typography.headlineMedium)
                        Text(weatherSummary(recovery.weather), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                } else {
                    Column {
                        Text("积累中", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "至少要有两项指标各积累 4 天以上的历史，才能和你自己比较。目前有 $nightCount 晚睡眠、$vitalsDays 天的日汇总。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (recovery != null) {
            SectionLabel("和你自己近期比")
            recovery.factors.forEach { FactorRow(it) }
        }
        SectionLabel("夜间腕部皮温")
        if (skin == null) {
            InfoRow("皮温偏离", "积累中")
        } else {
            InfoRow(SHORT_DATE.format(skin.date) + " 比基线", formatDeltaC(skin.deltaC))
            InfoRow("与日常波动", if (skin.notable) "偏离较大" else "在正常范围内")
        }
        Footnote(
            "身体天气把最近一次的心率变异性、静息心率和睡眠时长，分别和你此前 28 天的中位数比较，按 4:3:3 加权。" +
                "它只说明“相对你自己”的变化，不是医学评估。皮温是睡眠中的腕部皮肤温度，不是体温，只作参考。",
        )
    }
}

@Composable
private fun FactorRow(f: RecoveryFactor) {
    val colors = MaterialTheme.colorScheme
    val verdict = when {
        abs(f.score) < NEAR_BASELINE -> "接近近期水平"
        f.score > 0 -> "比近期更好"
        else -> "比近期更差"
    }
    RowFrame(verticalPadding = 16.dp) {
        Row {
            Text(metricName(f.metric), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(metricValue(f.metric, f.value), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
        }
        Text(
            "$verdict · 近期 ${metricValue(f.metric, f.baseline)} · ${SHORT_DATE.format(f.date)}",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        ScoreBar(f.score, Modifier.padding(top = 10.dp))
    }
}

/** 以中线为 0 的一条细条：向右（浅海色）更好，向左（珊瑚色，偏离）更差，满格是 ±1。 */
@Composable
private fun ScoreBar(score: Double, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val better = colors.secondary
    val worse = colors.tertiary
    Canvas(modifier.fillMaxWidth().height(8.dp)) {
        val radius = CornerRadius(size.height / 2)
        drawRoundRect(colors.surfaceVariant, Offset.Zero, size, radius)
        val half = size.width / 2
        val length = (abs(score) * half).toFloat()
        val left = if (score >= 0) half else half - length
        drawRoundRect(if (score >= 0) better else worse, Offset(left, 0f), Size(length, size.height), radius)
        drawLine(colors.onSurfaceVariant, Offset(half, 0f), Offset(half, size.height), strokeWidth = 1.5.dp.toPx())
    }
}
