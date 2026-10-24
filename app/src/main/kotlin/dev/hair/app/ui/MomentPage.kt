package dev.hair.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import dev.hair.app.data.HeartBucket
import dev.hair.app.data.Moment
import dev.hair.app.data.beforeAfter
import dev.hair.app.data.bucketAt
import dev.hair.app.data.momentWindow
import dev.hair.app.data.peakBucket
import dev.hair.app.ui.theme.linkColor

/**
 * 一段已结束记录的心率回看：心率带和折线，竖线是用户按下感受的时刻；下面是最高点、开头和结尾的对比、每次标记时的心率。
 * 心率每次现取（[loadHeart]），手环还没同步时为空，可以重试。
 */
@Composable
fun MomentPage(moment: Moment?, loadHeart: suspend (Moment) -> LoadResult<List<HeartBucket>>, onDelete: () -> Unit, onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val endMillis = moment?.endMillis
    val subtitle = if (moment != null && endMillis != null) {
        "${moment.kind.label} · ${dateClockText(moment.startMillis)} · ${lengthText(endMillis - moment.startMillis)}"
    } else {
        null
    }
    PageScaffold("心跳记忆", onBack, moment?.title ?: "心跳记忆", subtitle) {
        if (moment == null) {
            Block { Text("没有这条记录", style = MaterialTheme.typography.titleMedium) }
            return@PageScaffold
        }
        if (endMillis == null) {
            Block { Text("这段还在记录，结束后才能回看心率。", style = MaterialTheme.typography.titleMedium) }
            return@PageScaffold
        }
        var attempt by remember { mutableIntStateOf(0) }
        var result by remember(moment, attempt) { mutableStateOf<LoadResult<List<HeartBucket>>?>(null) }
        var selected by remember(moment) { mutableStateOf<Long?>(null) }
        var confirmDelete by remember { mutableStateOf(false) }
        LaunchedEffect(moment, attempt) { result = loadHeart(moment) }

        when (val r = result) {
            null -> Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) { TideLoader() }
            is LoadResult.Failed -> Block {
                Text(r.message, color = colors.error, style = MaterialTheme.typography.bodyMedium)
                TextAction("重试", Modifier.offset(x = (-12).dp)) { attempt++ }
            }
            is LoadResult.Loaded -> HeartSections(moment, endMillis, r.value, selected, { selected = it }) { attempt++ }
        }
        SectionLabel("管理")
        ActionRow("删除这条记录", colors.error) { confirmDelete = true }
        Footnote("心率是按时间窗口汇总的平均值；竖线和感受是你自己按下的。")

        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("删除「${moment.title}」？") },
                text = { Text("只删除这条记录，手环里的心率数据不受影响。") },
                confirmButton = { TextButton(onClick = onDelete) { Text("删除", color = colors.error) } },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消", color = linkColor()) } },
            )
        }
    }
}

@Composable
private fun HeartSections(
    moment: Moment,
    endMillis: Long,
    buckets: List<HeartBucket>,
    selected: Long?,
    onSelect: (Long?) -> Unit,
    onRefresh: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val t0 = moment.startMillis / 1000
    val t1 = endMillis / 1000
    val span = momentWindow(endMillis - moment.startMillis).seconds
    SectionLabel("心率")
    Block(vertical = 4.dp) {
        if (buckets.isEmpty()) {
            Text(
                "这段时间还没有心率数据。手环同步有延迟，过一阵再刷新。",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            TextAction("刷新", Modifier.offset(x = (-12).dp), onClick = onRefresh)
        } else {
            val shown = selected?.let { sec -> bucketAt(buckets, sec)?.takeIf { sec < it.startSec + span } }
            Text(
                if (shown != null) "${clockText(selected)} · 心率 %.0f bpm".format(shown.avgBpm) else "点按或拖动图表，查看某一刻",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            HeartChart(buckets, t0, t1, span, colors.tertiary, Modifier.padding(top = 8.dp), selected, moment.marks.map { it.atMillis / 1000 }, onSelect)
            TimeAxis(t0, t1)
        }
    }
    if (buckets.isEmpty()) return
    SectionLabel("概览")
    peakBucket(buckets)?.let { InfoRow("最高", "%.0f bpm · %s".format(it.avgBpm, clockText(it.startSec))) }
    beforeAfter(buckets)?.let { (before, after) -> InfoRow("开头 → 结尾", "%.0f → %.0f bpm".format(before, after)) }
    if (moment.marks.isNotEmpty()) {
        SectionLabel("你的标记")
        moment.marks.forEach { mark ->
            val sec = mark.atMillis / 1000
            val bucket = bucketAt(buckets, sec)?.takeIf { sec < it.startSec + span }
            InfoRow("${mark.label} · ${clockText(sec)}", bucket?.let { "%.0f bpm".format(it.avgBpm) } ?: "无心率")
        }
    }
}
