package dev.hair.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.hair.app.data.DailyVitals
import dev.hair.app.data.HabitComparison
import dev.hair.app.data.HabitLog
import dev.hair.app.data.Night
import dev.hair.app.data.OutcomeComparison
import dev.hair.app.data.compareHabit
import dev.hair.app.ui.theme.linkColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val MAX_HABIT_LENGTH = 12
private const val MARK_BACK_DAYS = 14L
private val HABIT_SEGMENT_WIDTH = 168.dp
private val DAY_FORMAT = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)

/**
 * 习惯实验室：给某一天的每个习惯记“做了 / 没做”，攒够天数后，把做了和没做的第二天的睡眠、心率并排比较。
 * 没记录的天不属于任何一组，每组至少 3 天才给出对比。
 */
@Composable
fun LabPage(log: HabitLog, nights: List<Night>, vitals: List<DailyVitals>, onChange: (HabitLog) -> Unit, onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val today = LocalDate.now()
    var dayText by rememberSaveable { mutableStateOf(today.toString()) }
    val day = LocalDate.parse(dayText)
    var removing by remember { mutableStateOf<String?>(null) }
    val comparisons = remember(log, nights, vitals) {
        log.habits.map { compareHabit(it, log, nights, vitals) }.filter { it.doneDays + it.skippedDays > 0 }
    }

    PageScaffold("探索", onBack, "习惯实验室") {
        DayPicker(
            day, today,
            onPrev = { dayText = day.minusDays(1).toString() },
            onNext = { dayText = day.plusDays(1).toString() },
        )
        Footnote("记下这一天傍晚到睡前做了什么，它影响的是 ${DAY_FORMAT.format(day.plusDays(1))} 醒来后的睡眠和心率。")
        log.habits.forEach { habit ->
            HabitRow(habit, log.markOf(day, habit), onMark = { onChange(log.withMark(day, habit, it)) }, onRemove = { removing = habit })
        }
        TextActionRow("新增习惯", "添加", MAX_HABIT_LENGTH, allowBlank = false) { onChange(log.withHabit(it)) }
        if (comparisons.isNotEmpty()) SectionLabel("第二天的对比")
        comparisons.forEach { ComparisonRow(it) }
        Footnote("这只是你自己记录里的关联，样本很少，不说明因果。没做的日子必须也记下来，才会算进“没做”这一组。")
    }

    removing?.let { habit ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("删除「$habit」？") },
            text = { Text("这个习惯的全部记录也会一起删除。") },
            confirmButton = {
                TextButton(onClick = { onChange(log.withoutHabit(habit)); removing = null }) { Text("删除", color = colors.error) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("取消", color = linkColor()) } },
        )
    }
}

@Composable
private fun DayPicker(day: LocalDate, today: LocalDate, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = PAGE_GUTTER - 7.dp), verticalAlignment = Alignment.CenterVertically) {
        StepButton("‹", "前一天", day > today.minusDays(MARK_BACK_DAYS), onPrev)
        Text(
            when (day) {
                today -> "今天"
                today.minusDays(1) -> "昨天"
                else -> DAY_FORMAT.format(day)
            },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        StepButton("›", "后一天", day < today, onNext)
    }
}

@Composable
private fun HabitRow(habit: String, mark: Boolean?, onMark: (Boolean?) -> Unit, onRemove: () -> Unit) {
    RowFrame(verticalPadding = 8.dp) {
        // 文字按钮自带 12dp 内边距：整行右移 12dp，最后一个按钮的文字才贴齐页面边距，标题再左移回来
        Row(Modifier.offset(x = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(habit, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).offset(x = (-12).dp))
            if (mark != null) TextAction("清除") { onMark(null) }
            TextAction("删除", color = MaterialTheme.colorScheme.error, onClick = onRemove)
        }
        Segmented(
            listOf("没做", "做了"),
            selected = when (mark) {
                null -> -1
                false -> 0
                true -> 1
            },
            onSelect = { onMark(it == 1) },
            modifier = Modifier.width(HABIT_SEGMENT_WIDTH).padding(bottom = 4.dp),
        )
    }
}

@Composable
private fun ComparisonRow(c: HabitComparison) {
    val colors = MaterialTheme.colorScheme
    val enough = c.outcomes.filter { it.enough }
    RowFrame(verticalPadding = 16.dp) {
        Text(c.habit, style = MaterialTheme.typography.bodyLarge)
        Text("做了 ${c.doneDays} 天 · 没做 ${c.skippedDays} 天", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        if (enough.isEmpty()) {
            Text(
                "样本不足：做了和没做各至少要有 3 天、且有第二天的数据，才会给出对比。",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        enough.forEach { OutcomeRow(it) }
    }
}

@Composable
private fun OutcomeRow(o: OutcomeComparison) {
    val done = o.done.mean ?: return
    val skipped = o.skipped.mean ?: return
    val colors = MaterialTheme.colorScheme
    Column(Modifier.padding(top = 8.dp)) {
        Row {
            Text(o.outcome.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("%+.0f %s".format(done - skipped, o.outcome.unit), style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            "做了 %.0f（${o.done.days} 天）· 没做 %.0f（${o.skipped.days} 天）".format(done, skipped),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }
}
