package dev.hair.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.hair.app.data.Moment
import dev.hair.app.data.MomentKind
import dev.hair.app.data.MomentLog
import dev.hair.app.ui.theme.TideNumeral
import dev.hair.app.ui.theme.linkColor
import kotlinx.coroutines.delay

/** 记录中途可以按的感受；标记只存用户按下的时刻和这几个词。 */
val MOMENT_FEELINGS = listOf("紧张", "兴奋", "没感觉")

private val STARTABLE_KINDS = listOf(MomentKind.FILM, MomentKind.GAME)

/**
 * 心跳记忆：自己记下看电影、玩游戏的起止和中途的感受，结束后再回看那段时间的心率。
 * 心率不在这里存，到详情页才去取。同一时间只能有一段在记录。
 */
@Composable
fun MomentsPage(
    log: MomentLog,
    onChange: (MomentLog) -> Unit,
    onBack: () -> Unit,
    onOpenBreath: () -> Unit,
    onOpenMoment: (Moment) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val open = log.open
    PageScaffold("探索", onBack, "心跳记忆") {
        if (open != null) {
            RecordingCard(open, onMark = { onChange(log.mark(it, System.currentTimeMillis())) }, onFinish = { onChange(log.finish(System.currentTimeMillis())) })
        } else {
            var kindIndex by rememberSaveable { mutableStateOf(0) }
            Block(vertical = 6.dp) { Segmented(STARTABLE_KINDS.map { it.label }, kindIndex, { kindIndex = it }) }
            TextActionRow("标题（可不填）", "开始", 20, allowBlank = true) {
                onChange(log.start(STARTABLE_KINDS[kindIndex], it, System.currentTimeMillis()))
            }
            ActionRow("呼吸花园", linkColor(), onClick = onOpenBreath)
        }
        val finished = log.moments.filter { !it.isOpen }
        if (finished.isNotEmpty()) {
            SectionLabel("已记录")
            finished.forEach { m ->
                RowFrame(onClick = { onOpenMoment(m) }, verticalPadding = 14.dp) {
                    Text(m.title, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${m.kind.label} · ${dateClockText(m.startMillis)} · ${lengthText((m.endMillis ?: m.startMillis) - m.startMillis)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
        Footnote("手环会隔一阵才把心率同步上来，记录结束后过一会儿再点开，才看得到那段时间的心率。")
    }
}

@Composable
private fun RecordingCard(moment: Moment, onMark: (String) -> Unit, onFinish: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    Block {
        Text("正在记录 · ${moment.kind.label}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
        Text(moment.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
        Text(timerText(now - moment.startMillis), style = TideNumeral, modifier = Modifier.padding(top = 4.dp))
        if (moment.kind != MomentKind.BREATH) {
            Text("此刻的感觉：", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MOMENT_FEELINGS.forEach { PillButton(it, onClick = { onMark(it) }, filled = false, height = 40.dp) }
            }
            if (moment.marks.isNotEmpty()) {
                Text("已标记 ${moment.marks.size} 次", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
        }
        PillButton("结束", onFinish, Modifier.padding(top = 20.dp))
    }
}
