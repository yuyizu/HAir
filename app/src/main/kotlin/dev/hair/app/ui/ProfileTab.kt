package dev.hair.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.hair.app.data.BIRTH_YEARS
import dev.hair.app.data.Sex
import dev.hair.app.data.UserSettings
import dev.hair.app.ui.theme.linkColor

/** 设置清单：每组一个小标题，行直接落在页面上，行间只有细线。 */
@Composable
fun ProfileTab(
    state: UiState.Ready,
    settings: UserSettings,
    onSettingsChange: (UserSettings) -> Unit,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val goals = settings.goals
    TabPage("我的") {
        item {
            Column {
                SectionLabel("每日目标")
                StepperRow(
                    "步数目标",
                    "%,d 步".format(goals.steps),
                    canDecrease = goals.adjustSteps(-1) != goals,
                    canIncrease = goals.adjustSteps(1) != goals,
                    onDecrease = { onSettingsChange(settings.copy(goals = goals.adjustSteps(-1))) },
                    onIncrease = { onSettingsChange(settings.copy(goals = goals.adjustSteps(1))) },
                )
                StepperRow(
                    "睡眠目标",
                    formatSleepGoal(goals.sleepMin),
                    canDecrease = goals.adjustSleep(-1) != goals,
                    canIncrease = goals.adjustSleep(1) != goals,
                    onDecrease = { onSettingsChange(settings.copy(goals = goals.adjustSleep(-1))) },
                    onIncrease = { onSettingsChange(settings.copy(goals = goals.adjustSleep(1))) },
                )
                SectionLabel("健康年龄")
                SexRow(settings.sex) { onSettingsChange(settings.copy(sex = it)) }
                BirthYearRow(settings.birthYear) { onSettingsChange(settings.copy(birthYear = it)) }
                Footnote("性别用于对照参考表算出健康年龄；出生年份可不填，填了会和实际年龄比较。两项都只保存在这台手机上。")
                SceneSettingsSection(settings.scene) { onSettingsChange(settings.copy(scene = it)) }
                SectionLabel("数据")
                InfoRow("数据来源", "Google Health API")
                ActionRow("立即刷新", linkColor(), enabled = !state.refreshing, onClick = onRefresh)
                ActionRow("退出登录", colors.error, onClick = onSignOut)
                Footnote("健康数据只保存在这台手机上，退出登录时会一并清除。")
            }
        }
    }
}

/** 左标签，右侧当前值加 − / + 两个按钮；到达边界的一侧置灰。 */
@Composable
private fun StepperRow(
    label: String,
    value: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    RowFrame(verticalPadding = 4.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), modifier = Modifier.padding(end = 4.dp))
            // 按钮的点按区域比圆大 7dp，右移回去，圆的右缘才贴齐页面边距
            Row(Modifier.offset(x = 7.dp)) {
                StepButton("−", "降低$label", canDecrease, onDecrease)
                StepButton("+", "提高$label", canIncrease, onIncrease)
            }
        }
    }
}

private fun sexLabel(sex: Sex): String = when (sex) {
    Sex.MALE -> "男"
    Sex.FEMALE -> "女"
}

@Composable
private fun SexRow(sex: Sex?, onSelect: (Sex) -> Unit) {
    RowFrame(verticalPadding = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("性别", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Segmented(
                options = Sex.entries.map(::sexLabel),
                selected = sex?.ordinal ?: -1,
                onSelect = { onSelect(Sex.entries[it]) },
                modifier = Modifier.width(120.dp),
            )
        }
    }
}

/**
 * 只有输入了范围内的 4 位年份才保存，清空则取消设置；输到一半的内容不保存。
 * 输入框里的文字是本行自己的状态，离开本页后按已保存的值重新显示。
 */
@Composable
private fun BirthYearRow(birthYear: Int?, onChange: (Int?) -> Unit) {
    var text by remember { mutableStateOf(birthYear?.toString().orEmpty()) }
    val colors = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.bodyLarge
    val outOfRange = text.length == 4 && text.toInt() !in BIRTH_YEARS
    RowFrame {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("出生年份", style = style, modifier = Modifier.weight(1f))
            BasicTextField(
                value = text,
                onValueChange = { input ->
                    val digits = input.filter(Char::isDigit).take(4)
                    text = digits
                    if (digits.isEmpty()) {
                        onChange(null)
                    } else {
                        digits.toInt().takeIf { it in BIRTH_YEARS }?.let(onChange)
                    }
                },
                modifier = Modifier.width(96.dp),
                textStyle = style.copy(color = if (outOfRange) colors.error else colors.onSurface, textAlign = TextAlign.End),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                cursorBrush = SolidColor(linkColor()),
                decorationBox = { field ->
                    Box(contentAlignment = Alignment.CenterEnd) {
                        if (text.isEmpty()) Text("未设置", style = style, color = colors.onSurfaceVariant)
                        field()
                    }
                },
            )
        }
    }
}
