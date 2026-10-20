package dev.hair.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 无阴影、细描边的白卡：靠留白和底色差分层，不靠彩色描边。 */
@Composable
fun HairCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

/** 单项指标；[value] 为 null 表示没有数据，显示破折号和"暂无数据"。 */
@Composable
fun MetricTile(label: String, value: String?, unit: String?, caption: String?, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    HairCard(modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value ?: "—",
                style = MaterialTheme.typography.headlineMedium,
                color = if (value == null) colors.onSurfaceVariant else colors.onSurface,
            )
            if (value != null && unit != null) {
                Spacer(Modifier.width(4.dp))
                Text(unit, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
        val note = caption ?: "暂无数据".takeIf { value == null }
        if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}

fun formatMinutes(minutes: Long): String = "${minutes / 60} 小时 ${minutes % 60} 分"
