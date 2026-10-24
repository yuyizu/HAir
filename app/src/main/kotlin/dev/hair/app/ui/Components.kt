package dev.hair.app.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.hair.app.data.FitnessAge
import dev.hair.app.ui.theme.DmSans
import dev.hair.app.ui.theme.TideNumeral
import dev.hair.app.ui.theme.linkColor

/** 所有页面共用的左右边距：行、块、说明文字的文字都从这条线开始。 */
val PAGE_GUTTER = 24.dp

/** 行底的细线，左右缩进一个 [PAGE_GUTTER]；[progress] 不为 null 时画成完成度（2dp，赭金）。 */
@Composable
fun RowLine(modifier: Modifier = Modifier, progress: Float? = null) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.padding(horizontal = PAGE_GUTTER).fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(if (progress != null) 2.dp else 1.dp).background(colors.outlineVariant))
        if (progress != null) Box(Modifier.fillMaxWidth(progress).height(2.dp).background(colors.primary))
    }
}

/**
 * 抽屉里的一行，没有卡片：左右留 [PAGE_GUTTER]，行底一条细线。
 * 传入 [onClick] 时整行可点，按下会轻轻缩一下；[content] 在行内竖向排列。
 */
@Composable
fun RowFrame(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    minHeight: Dp = 56.dp,
    verticalPadding: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val tap = if (onClick != null) {
        Modifier
            .pressScale(source)
            .clickable(interactionSource = source, indication = LocalIndication.current, role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    Box(modifier.fillMaxWidth().then(tap)) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = minHeight).padding(horizontal = PAGE_GUTTER, vertical = verticalPadding),
            verticalArrangement = Arrangement.Center,
            content = content,
        )
        RowLine(Modifier.align(Alignment.BottomStart))
    }
}

/** 没有容器的内容块：图表、图形、大数字直接落在页面底色上，只留左右边距。 */
@Composable
fun Block(modifier: Modifier = Modifier, vertical: Dp = 12.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().padding(horizontal = PAGE_GUTTER, vertical = vertical), content = content)
}

/** [RowFrame] 里的一行：左标签，右灰色数值。 */
@Composable
fun InfoRow(label: String, value: String) {
    RowFrame {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.padding(start = 16.dp).widthIn(max = 220.dp),
            )
        }
    }
}

/** 大数字加灰色小单位；[value] 为 null 表示没有数据。 */
@Composable
fun Numeral(value: String?, unit: String? = null) {
    val colors = MaterialTheme.colorScheme
    if (value == null) {
        Text("暂无数据", style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant)
        return
    }
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, style = TideNumeral)
        if (unit != null) {
            Text(unit, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp, bottom = 8.dp))
        }
    }
}

/**
 * 抽屉里的指标行：左边圆点和名称，右边数值、单位，最右一列是状态（始终占位，各行的数值才对得齐）。
 * [deviated] 表示偏离常态，圆点和状态用珊瑚色；[progress] 不为 null 时行底的细线画成完成度。
 * 传入 [onClick] 时整行可点，按下会轻轻缩一下。
 */
@Composable
fun MetricRow(
    name: String,
    value: String,
    unit: String? = null,
    status: String? = null,
    deviated: Boolean = false,
    dot: Color = MaterialTheme.colorScheme.secondary,
    progress: Float? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val tap = if (onClick != null) {
        Modifier
            .pressScale(source)
            .clickable(interactionSource = source, indication = LocalIndication.current, role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    Box(Modifier.fillMaxWidth().then(tap)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = PAGE_GUTTER), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(if (deviated) colors.tertiary else dot, CircleShape))
            Spacer(Modifier.width(12.dp))
            Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 19.sp))
                if (unit != null) {
                    Text(unit, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
                }
            }
            Text(
                status.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = if (deviated) colors.tertiary else colors.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.width(60.dp),
            )
        }
        RowLine(Modifier.align(Alignment.BottomStart), progress)
    }
}

/** 温度偏离带符号、保留一位；四舍五入后是 0 就不带符号，避免出现“-0.0”。 */
fun formatDeltaC(delta: Double): String {
    val rounded = Math.round(delta * 10) / 10.0
    return if (rounded == 0.0) "0.0°C" else "%+.1f°C".format(rounded)
}

fun formatMinutes(minutes: Long): String = "${minutes / 60} 小时 ${minutes % 60} 分"

/** 目标整小时时省掉“0 分”，否则同 [formatMinutes]。 */
fun formatSleepGoal(minutes: Long): String = if (minutes % 60 == 0L) "${minutes / 60} 小时" else formatMinutes(minutes)

fun formatFitnessAge(age: FitnessAge): String = when (age) {
    is FitnessAge.Exact -> "${age.years} 岁"
    is FitnessAge.AtMost -> "≤ ${age.years} 岁"
    is FitnessAge.AtLeast -> "≥ ${age.years} 岁"
}

/** 只有精确的健康年龄才能和实际年龄比较；上下限没有可比的差值，返回 null。 */
fun compareToActualAge(age: FitnessAge, actualAge: Int): String? {
    if (age !is FitnessAge.Exact) return null
    val younger = actualAge - age.years
    return when {
        younger > 0 -> "比实际年龄小 $younger 岁"
        younger < 0 -> "比实际年龄大 ${-younger} 岁"
        else -> "与实际年龄相当"
    }
}

/** 一组行的小标题：灰色、略放宽字距，上方留出分组的间隔。 */
@Composable
fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium.copy(fontFamily = DmSans, fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 0.06.em),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = PAGE_GUTTER, end = PAGE_GUTTER, top = 28.dp, bottom = 8.dp),
    )
}

@Composable
fun Footnote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = PAGE_GUTTER, vertical = 12.dp),
    )
}

/** [RowFrame] 里的整行文字按钮。 */
@Composable
fun ActionRow(label: String, color: Color, enabled: Boolean = true, onClick: () -> Unit) {
    RowFrame(onClick = if (enabled) onClick else null) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = if (enabled) color else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * [RowFrame] 里的输入行：左边输入文字（最多 [maxLength] 字），右边是文字按钮 [action]。
 * 提交后清空输入框；[allowBlank] 为 false 时没输入内容按钮置灰。
 */
@Composable
fun TextActionRow(hint: String, action: String, maxLength: Int, allowBlank: Boolean, onSubmit: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val colors = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.bodyLarge
    RowFrame(minHeight = 56.dp, verticalPadding = 0.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = text,
                onValueChange = { text = it.take(maxLength) },
                modifier = Modifier.weight(1f),
                textStyle = style.copy(color = colors.onSurface),
                singleLine = true,
                cursorBrush = SolidColor(linkColor()),
                decorationBox = { field ->
                    Box {
                        if (text.isEmpty()) Text(hint, style = style, color = colors.onSurfaceVariant)
                        field()
                    }
                },
            )
            TextAction(action, enabled = allowBlank || text.isNotBlank(), modifier = Modifier.offset(x = 12.dp)) { onSubmit(text.trim()); text = "" }
        }
    }
}
