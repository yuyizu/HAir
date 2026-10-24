package dev.hair.app.ui

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hair.app.ui.theme.linkColor

private val SEGMENT_HEIGHT = 32.dp
private val SEGMENT_TRACK_PADDING = 2.dp

/**
 * 分段控件：一条细描边的药丸，选中的一段是浅海色的淡底和同色文字，淡底会滑到新选项下，首次显示直接就位。
 * [selected] 为 -1 表示还没选，不画淡底。
 */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(percent = 50)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .border(1.dp, colors.onSurfaceVariant.copy(alpha = 0.3f), shape)
            .padding(SEGMENT_TRACK_PADDING),
    ) {
        val itemWidth = maxWidth / options.size
        val thumbX by animateDpAsState(
            targetValue = itemWidth * selected.coerceAtLeast(0),
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
            label = "segmentThumb",
        )
        if (selected >= 0) {
            Box(
                Modifier
                    .offset(x = thumbX)
                    .width(itemWidth)
                    .height(SEGMENT_HEIGHT)
                    .background(colors.secondary.copy(alpha = 0.14f), shape),
            )
        }
        Row {
            options.forEachIndexed { i, label ->
                val isSelected = i == selected
                val source = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .weight(1f)
                        .height(SEGMENT_HEIGHT)
                        .pressScale(source)
                        .selectable(
                            selected = isSelected,
                            interactionSource = source,
                            indication = null,
                            role = Role.RadioButton,
                            onClick = { onSelect(i) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) colors.secondary else colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * 主按钮：浅海色实心药丸；[filled] 为 false 时是描边药丸，用在同屏的次要动作上。
 * 按下会轻轻缩一下；点按区域至少 [height] 高。
 */
@Composable
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, filled: Boolean = true, height: Dp = 48.dp) {
    val colors = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(percent = 50)
    val background = when {
        !filled -> Color.Transparent
        enabled -> colors.secondary
        else -> colors.onSurfaceVariant.copy(alpha = 0.25f)
    }
    val content = when {
        filled -> colors.onSecondary
        enabled -> colors.secondary
        else -> colors.onSurfaceVariant
    }
    Box(
        modifier
            .heightIn(min = height)
            .pressScale(source)
            .clip(shape)
            .background(background)
            .then(if (filled) Modifier else Modifier.border(1.dp, content.copy(alpha = 0.5f), shape))
            .clickable(interactionSource = source, indication = LocalIndication.current, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = content)
    }
}

/** 一行里的文字按钮：浅海色（[color] 可换），点按区域不小于 48dp，按下会轻轻缩一下。 */
@Composable
fun TextAction(label: String, modifier: Modifier = Modifier, color: Color = linkColor(), enabled: Boolean = true, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (enabled) color else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        modifier = modifier
            .pressScale(source)
            .clickable(interactionSource = source, indication = LocalIndication.current, enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp)
            .wrapContentHeight(Alignment.CenterVertically)
            .padding(horizontal = 12.dp),
    )
}

/** 步进器的 − / +：34dp 的描边圆，点按区域 48dp；到边界时置灰。 */
@Composable
fun StepButton(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(48.dp)
            .semantics { contentDescription = description }
            .pressScale(source)
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(34.dp).border(1.dp, colors.onSurfaceVariant.copy(alpha = if (enabled) 0.45f else 0.2f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(symbol, fontSize = 20.sp, color = if (enabled) colors.secondary else colors.onSurfaceVariant.copy(alpha = 0.5f))
        }
    }
}

/** 加载中：一圈圈散开的涟漪，和今日页点水面的涟漪同一个意象。系统关闭动画时停成一个点。 */
@Composable
fun TideLoader(modifier: Modifier = Modifier.size(56.dp)) {
    val color = MaterialTheme.colorScheme.secondary
    val phase by rememberInfiniteTransition(label = "loader").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearOutSlowInEasing), RepeatMode.Restart),
        label = "ripplePhase",
    )
    Canvas(modifier) {
        listOf(0f, 0.5f).forEach { offset ->
            val p = (phase + offset) % 1f
            drawCircle(color.copy(alpha = (1f - p) * 0.8f), radius = size.minDimension / 2 * p, style = Stroke(2.dp.toPx()))
        }
        drawCircle(color, radius = 3.dp.toPx())
    }
}
