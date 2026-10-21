package dev.hair.app.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.hair.app.data.BIRTH_YEARS
import dev.hair.app.data.Sex
import dev.hair.app.data.UserSettings
import dev.hair.app.ui.theme.linkColor

/** 仿 iOS 设置的分组列表：一张白卡里多行，行间细分隔线。 */
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
    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("我的", style = MaterialTheme.typography.headlineLarge)
        GroupCaption("每日目标")
        GroupedList {
            StepperRow(
                "步数目标",
                "%,d 步".format(goals.steps),
                canDecrease = goals.adjustSteps(-1) != goals,
                canIncrease = goals.adjustSteps(1) != goals,
                onDecrease = { onSettingsChange(settings.copy(goals = goals.adjustSteps(-1))) },
                onIncrease = { onSettingsChange(settings.copy(goals = goals.adjustSteps(1))) },
            )
            HorizontalDivider(color = colors.outlineVariant)
            StepperRow(
                "睡眠目标",
                formatSleepGoal(goals.sleepMin),
                canDecrease = goals.adjustSleep(-1) != goals,
                canIncrease = goals.adjustSleep(1) != goals,
                onDecrease = { onSettingsChange(settings.copy(goals = goals.adjustSleep(-1))) },
                onIncrease = { onSettingsChange(settings.copy(goals = goals.adjustSleep(1))) },
            )
        }
        GroupCaption("健康年龄")
        GroupedList {
            SexRow(settings.sex) { onSettingsChange(settings.copy(sex = it)) }
            HorizontalDivider(color = colors.outlineVariant)
            BirthYearRow(settings.birthYear) { onSettingsChange(settings.copy(birthYear = it)) }
        }
        Footnote("性别用于对照参考表算出健康年龄；出生年份可不填，填了会和实际年龄比较。两项都只保存在这台手机上。")
        GroupedList {
            InfoRow("数据来源", "Google Health API")
            HorizontalDivider(color = colors.outlineVariant)
            ActionRow("立即刷新", linkColor(), enabled = !state.refreshing, onClick = onRefresh)
            HorizontalDivider(color = colors.outlineVariant)
            ActionRow("退出登录", colors.error, onClick = onSignOut)
        }
        Footnote("健康数据只保存在这台手机上，退出登录时会一并清除。")
    }
}

@Composable
private fun GroupCaption(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun Footnote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
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
    val buttonColors = ButtonDefaults.textButtonColors(contentColor = linkColor())
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(
            onClick = onDecrease,
            enabled = canDecrease,
            colors = buttonColors,
            modifier = Modifier.semantics { contentDescription = "降低$label" },
        ) { Text("−") }
        TextButton(
            onClick = onIncrease,
            enabled = canIncrease,
            colors = buttonColors,
            modifier = Modifier.semantics { contentDescription = "提高$label" },
        ) { Text("+") }
    }
}

private fun sexLabel(sex: Sex): String = when (sex) {
    Sex.MALE -> "男"
    Sex.FEMALE -> "女"
}

@Composable
private fun SexRow(sex: Sex?, onSelect: (Sex) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("性别", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Segmented(
            options = Sex.entries.map(::sexLabel),
            selected = sex?.ordinal ?: -1,
            onSelect = { onSelect(Sex.entries[it]) },
            modifier = Modifier.width(120.dp),
        )
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
    val style = MaterialTheme.typography.titleMedium
    val outOfRange = text.length == 4 && text.toInt() !in BIRTH_YEARS
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
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

@Composable
private fun ActionRow(label: String, color: Color, enabled: Boolean = true, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Text(
        label,
        style = MaterialTheme.typography.titleMedium,
        color = if (enabled) color else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(source)
            .clickable(interactionSource = source, indication = LocalIndication.current, enabled = enabled, onClick = onClick)
            .padding(16.dp),
    )
}
