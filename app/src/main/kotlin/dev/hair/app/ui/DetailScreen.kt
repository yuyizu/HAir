package dev.hair.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.hair.app.data.DetailRange
import dev.hair.app.data.Metric
import dev.hair.app.data.Series
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SHORT_DATE = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
private val PLACEHOLDER_HEIGHT = 280.dp

/**
 * 活动详情：步数、活动能量按日（24 小时）、周、月看柱状图，点按或拖动查看某根柱的数值。
 * 数据每次现取（[loadSeries]），切换指标或范围会取消上一次请求；失败可重试。
 */
@Composable
fun DetailScreen(onBack: () -> Unit, loadSeries: suspend (Metric, DetailRange) -> LoadResult<Series>) {
    val colors = MaterialTheme.colorScheme
    var metric by rememberSaveable { mutableStateOf(Metric.STEPS) }
    var range by rememberSaveable { mutableStateOf(DetailRange.DAY) }
    var attempt by remember { mutableIntStateOf(0) }
    // null 表示正在加载；键变化时回到加载中，同键的 LaunchedEffect 会取消上一次请求并重新取数
    var result by remember(metric, range, attempt) { mutableStateOf<LoadResult<Series>?>(null) }
    var selected by remember(result) { mutableStateOf<Int?>(null) }
    LaunchedEffect(metric, range, attempt) { result = loadSeries(metric, range) }

    PageScaffold("今日", onBack, metric.title) {
        Block(vertical = 6.dp) { Segmented(Metric.entries.map { it.title }, metric.ordinal, { metric = Metric.entries[it] }) }
        Block(vertical = 6.dp) { Segmented(DetailRange.entries.map { it.label }, range.ordinal, { range = DetailRange.entries[it] }) }

        when (val r = result) {
            null -> Box(Modifier.fillMaxWidth().height(PLACEHOLDER_HEIGHT), contentAlignment = Alignment.Center) { TideLoader() }
            is LoadResult.Failed -> Block {
                Text(r.message, color = colors.error, style = MaterialTheme.typography.bodyMedium)
                TextAction("重试", Modifier.offset(x = (-12).dp)) { attempt++ }
            }
            is LoadResult.Loaded -> {
                MetricChart(r.value, metric, selected) { selected = it }
                if (r.value.recordedCount > 0) Stats(r.value, metric)
            }
        }
    }
}

@Composable
private fun MetricChart(series: Series, metric: Metric, selected: Int?, onSelect: (Int?) -> Unit) {
    val color = metricColor(metric)
    val empty = series.recordedCount == 0
    val daily = series.range != DetailRange.DAY

    // 选中某根柱时头部显示它的值；否则日档显示总计、周月档显示日均
    val caption = when {
        selected != null -> bucketLabel(series, selected)
        daily -> "日均"
        else -> "总计"
    }
    val value = when {
        selected != null -> series.values[selected]
        empty -> null
        daily -> series.average
        else -> series.total
    }

    Block {
        Text(caption, style = MaterialTheme.typography.labelLarge, color = color)
        Numeral(value?.let(::formatAmount), metric.unit)
        Text(rangeText(series), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.padding(top = 12.dp), contentAlignment = Alignment.Center) {
            BarChart(series, color, selected, onSelect)
            if (empty) {
                Text("暂无数据", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Stats(series: Series, metric: Metric) {
    val peak = series.peakIndex
    SectionLabel("统计")
    InfoRow("总计", "${formatAmount(series.total)} ${metric.unit}")
    if (series.range != DetailRange.DAY) InfoRow("日均", "${formatAmount(series.average)} ${metric.unit}")
    if (peak != null) {
        InfoRow("最高", "${formatAmount(series.values[peak])} ${metric.unit}")
        InfoRow(if (series.range == DetailRange.DAY) "时段" else "日期", bucketLabel(series, peak))
    }
    if (series.range != DetailRange.DAY) InfoRow("有记录", "${series.recordedCount} / ${series.values.size} 天")
}

@Composable
private fun metricColor(metric: Metric): Color = when (metric) {
    Metric.STEPS -> MaterialTheme.colorScheme.primary
    Metric.ACTIVE_ENERGY -> MaterialTheme.colorScheme.tertiary
}

private fun formatAmount(value: Double): String = "%,.0f".format(value)

private fun rangeText(series: Series): String = when (series.range) {
    DetailRange.DAY -> if (series.start == LocalDate.now()) "今天" else SHORT_DATE.format(series.start)
    else -> "${SHORT_DATE.format(series.start)} – ${SHORT_DATE.format(series.start.plusDays(series.values.size - 1L))}"
}
