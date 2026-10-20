package dev.hair.app.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val SEGMENT_HEIGHT = 32.dp

/** 无描边、无阴影的白色圆角卡，靠灰底分层。传入 [onClick] 时整张卡可点，按下会轻轻缩一下。 */
@Composable
fun HairCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val source = remember { MutableInteractionSource() }
    Card(
        modifier = if (onClick != null) modifier.pressScale(source) else modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        // 点击放在卡内部，涟漪才会被卡片的圆角裁剪
        val tap = if (onClick != null) {
            Modifier.clickable(interactionSource = source, indication = LocalIndication.current, role = Role.Button, onClick = onClick)
        } else {
            Modifier
        }
        Column(tap.padding(16.dp), content = content)
    }
}

/** 一张白卡里多行（行间用分隔线），仿 iOS 设置的分组列表。 */
@Composable
fun GroupedList(content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) { Column { content() } }
}

/** [GroupedList] 里的一行：左标签，右灰色数值。 */
@Composable
fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * 摘要卡：左上是按类别着色的标题，右上是时间标签（没有可靠时间时传 null）。
 * 传入 [onClick] 时右上多一个 ›，表示可以点进详情。
 */
@Composable
fun SummaryCard(
    title: String,
    titleColor: Color,
    modifier: Modifier = Modifier,
    time: String? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    HairCard(modifier, onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = titleColor, modifier = Modifier.weight(1f))
            if (time != null) {
                Text(time, style = MaterialTheme.typography.bodySmall, color = muted)
            }
            if (onClick != null) {
                Text("›", style = MaterialTheme.typography.labelLarge, color = muted, modifier = Modifier.padding(start = 6.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        content()
    }
}

/** 大数字加灰色小单位；[value] 为 null 表示没有数据。 */
@Composable
fun ValueText(value: String?, unit: String? = null) {
    val colors = MaterialTheme.colorScheme
    if (value == null) {
        Text("暂无数据", style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant)
        return
    }
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, style = MaterialTheme.typography.headlineMedium)
        if (unit != null) {
            Spacer(Modifier.width(4.dp))
            Text(unit, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 4.dp))
}

/** iOS 风格分段控件：选中的白色滑块会滑到新选项下，首次显示直接就位。 */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .background(colors.surfaceVariant, RoundedCornerShape(10.dp))
            .padding(2.dp),
    ) {
        val itemWidth = maxWidth / options.size
        val thumbX by animateDpAsState(
            targetValue = itemWidth * selected,
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
            label = "segmentThumb",
        )
        Box(
            Modifier
                .offset(x = thumbX)
                .width(itemWidth)
                .height(SEGMENT_HEIGHT)
                .background(colors.surface, RoundedCornerShape(8.dp)),
        )
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
                        color = if (isSelected) colors.onSurface else colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

fun formatMinutes(minutes: Long): String = "${minutes / 60} 小时 ${minutes % 60} 分"
