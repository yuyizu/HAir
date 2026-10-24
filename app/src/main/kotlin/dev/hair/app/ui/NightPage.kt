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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.hair.app.data.DailyVitals
import dev.hair.app.data.HeartBucket
import dev.hair.app.data.NIGHT_HR_WINDOW
import dev.hair.app.data.Night
import dev.hair.app.data.NightDetail
import dev.hair.app.data.Spo2Point
import dev.hair.app.data.bucketAt
import java.time.LocalDate
import kotlin.math.abs

private const val SPO2_NEAR_SEC = 300L

/**
 * 夜间全景：睡眠分期、心率、血氧放在同一条时间轴上，点按或拖动任一张图，三张图的游标一起走，
 * 上方读数显示那一刻的分期、心率和血氧。心率和血氧每次现取（[loadDetail]），失败可重试。
 */
@Composable
fun NightPage(
    night: Night?,
    date: LocalDate,
    vitals: DailyVitals?,
    loadDetail: suspend (Night) -> LoadResult<NightDetail?>,
    onBack: () -> Unit,
) {
    val window = night?.windowSec
    PageScaffold("睡眠", onBack, nightDateText(date), subtitle = window?.let { "${clockText(it.first)} – ${clockText(it.second)}" }) {
        if (night == null) {
            Block { Text("没有这一晚的数据", style = MaterialTheme.typography.titleMedium) }
            return@PageScaffold
        }
        var attempt by remember { mutableIntStateOf(0) }
        var detail by remember(night, attempt) { mutableStateOf<LoadResult<NightDetail?>?>(null) }
        var selected by remember(night) { mutableStateOf<Long?>(null) }
        // 没有分期就无法确定起止，也就不取细节
        LaunchedEffect(night, attempt) { if (window != null) detail = loadDetail(night) }

        Block {
            Numeral(formatHm(night.asleepMin))
            Text("睡着的时间", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val loaded = (detail as? LoadResult.Loaded)?.value
        if (window != null) {
            Block {
                Readout(night, loaded, selected)
                Hypnogram(night, Modifier.padding(top = 12.dp), height = 96.dp, gutter = AXIS_GUTTER, selectedSec = selected, onSelect = { selected = it })
                TimeAxis(window.first, window.second)
                StageSummary(night, Modifier.padding(top = 16.dp))
            }
            when (val d = detail) {
                null -> Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) { TideLoader() }
                is LoadResult.Failed -> Block {
                    Text(d.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    TextAction("重试", Modifier.offset(x = (-12).dp)) { attempt++ }
                }
                is LoadResult.Loaded -> d.value?.let { Charts(it, window.first, window.second, selected) { sec -> selected = sec } }
            }
        } else {
            Block { StageSummary(night) }
            Footnote("这一晚没有分期数据（可能是经典睡眠记录），无法画出心率和血氧的时间轴。")
        }
        vitals?.let { OtherVitals(it) }
        Footnote("心率和血氧来自手环夜间的采样；点按或拖动图表可以对照同一时刻。")
    }
}

@Composable
private fun Readout(night: Night, detail: NightDetail?, selected: Long?) {
    val colors = MaterialTheme.colorScheme
    val text = if (selected == null) {
        "点按或拖动图表，查看某一刻"
    } else {
        val stage = night.stages.firstOrNull { selected >= it.startSec && selected < it.endSec }?.stage?.let(::stageLabel)
        val heart = detail?.heart?.let { bucketAt(it, selected) }?.takeIf { selected < it.startSec + NIGHT_HR_WINDOW.seconds }
        val spo2 = detail?.spo2?.nearest(selected)
        listOfNotNull(
            clockText(selected),
            stage,
            heart?.let { "心率 %.0f".format(it.avgBpm) },
            spo2?.let { "血氧 %.0f%%".format(it.percent) },
        ).joinToString(" · ")
    }
    Text(text, style = MaterialTheme.typography.titleMedium, color = if (selected == null) colors.onSurfaceVariant else colors.onSurface)
}

private fun List<Spo2Point>.nearest(sec: Long): Spo2Point? =
    minByOrNull { abs(it.sec - sec) }?.takeIf { abs(it.sec - sec) <= SPO2_NEAR_SEC }

@Composable
private fun Charts(detail: NightDetail, t0: Long, t1: Long, selected: Long?, onSelect: (Long?) -> Unit) {
    val colors = MaterialTheme.colorScheme
    SectionLabel("心率")
    Block(vertical = 4.dp) {
        if (detail.heart.isEmpty()) {
            Text("这一晚没有心率数据", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        } else {
            HeartSummary(detail.heart)
            HeartChart(detail.heart, t0, t1, NIGHT_HR_WINDOW.seconds, colors.tertiary, Modifier.padding(top = 8.dp), selected, onSelect = onSelect)
            TimeAxis(t0, t1)
        }
    }
    SectionLabel("血氧")
    Block(vertical = 4.dp) {
        if (detail.spo2.isEmpty()) {
            Text("这一晚没有血氧数据", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        } else {
            Spo2Summary(detail.spo2)
            Spo2Chart(detail.spo2, t0, t1, colors.secondary, Modifier.padding(top = 8.dp), selected, onSelect)
            TimeAxis(t0, t1)
        }
    }
}

@Composable
private fun HeartSummary(buckets: List<HeartBucket>) {
    val lowest = buckets.minBy { it.avgBpm }
    Text(
        "平均 %.0f · 最低 %.0f（%s）· 最高 %.0f bpm".format(
            buckets.map { it.avgBpm }.average(),
            lowest.avgBpm,
            clockText(lowest.startSec),
            buckets.maxOf { it.avgBpm },
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Spo2Summary(points: List<Spo2Point>) {
    val lowest = points.minBy { it.percent }
    Text(
        "平均 %.0f%% · 最低 %.0f%%（%s）".format(points.map { it.percent }.average(), lowest.percent, clockText(lowest.sec)),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 当晚的日汇总：血氧、皮温偏离、呼吸率；有几项显示几项。 */
@Composable
private fun OtherVitals(v: DailyVitals) {
    val rows = listOfNotNull(
        v.spo2Avg?.let { "血氧（日汇总）" to "平均 %.0f%%%s".format(it, v.spo2Min?.let { m -> "，最低 %.0f%%".format(m) }.orEmpty()) },
        v.skinTempDeltaC?.let { "腕部皮温" to "比基线 ${formatDeltaC(it)}" },
        v.respRate?.let { "呼吸率" to "%.1f 次/分".format(it) },
    )
    if (rows.isEmpty()) return
    SectionLabel("当晚其他指标")
    rows.forEach { (label, value) -> InfoRow(label, value) }
}
