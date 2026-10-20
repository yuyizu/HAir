package dev.hair.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** 无描边、无阴影的白色圆角卡，靠灰底分层。 */
@Composable
fun HairCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

/** 摘要卡：左上是按类别着色的标题，右上是时间标签（没有可靠时间时传 null）。 */
@Composable
fun SummaryCard(
    title: String,
    titleColor: Color,
    modifier: Modifier = Modifier,
    time: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    HairCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = titleColor, modifier = Modifier.weight(1f))
            if (time != null) {
                Text(time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

fun formatMinutes(minutes: Long): String = "${minutes / 60} 小时 ${minutes % 60} 分"
