package dev.hair.app.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.hair.app.data.Goals
import dev.hair.app.ui.theme.linkColor

/** 仿 iOS 设置的分组列表：一张白卡里多行，行间细分隔线。 */
@Composable
fun ProfileTab(
    state: UiState.Ready,
    goals: Goals,
    onGoalsChange: (Goals) -> Unit,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("我的", style = MaterialTheme.typography.headlineLarge)
        Text(
            "每日目标",
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        GroupedList {
            StepperRow(
                "步数目标",
                "%,d 步".format(goals.steps),
                canDecrease = goals.adjustSteps(-1) != goals,
                canIncrease = goals.adjustSteps(1) != goals,
                onDecrease = { onGoalsChange(goals.adjustSteps(-1)) },
                onIncrease = { onGoalsChange(goals.adjustSteps(1)) },
            )
            HorizontalDivider(color = colors.outlineVariant)
            StepperRow(
                "睡眠目标",
                formatSleepGoal(goals.sleepMin),
                canDecrease = goals.adjustSleep(-1) != goals,
                canIncrease = goals.adjustSleep(1) != goals,
                onDecrease = { onGoalsChange(goals.adjustSleep(-1)) },
                onIncrease = { onGoalsChange(goals.adjustSleep(1)) },
            )
        }
        GroupedList {
            InfoRow("数据来源", "Google Health API")
            HorizontalDivider(color = colors.outlineVariant)
            ActionRow("立即刷新", linkColor(), enabled = !state.refreshing, onClick = onRefresh)
            HorizontalDivider(color = colors.outlineVariant)
            ActionRow("退出登录", colors.error, onClick = onSignOut)
        }
        Text(
            "健康数据只保存在这台手机上，退出登录时会一并清除。",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
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
