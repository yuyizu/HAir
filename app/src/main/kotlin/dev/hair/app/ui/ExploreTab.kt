package dev.hair.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.hair.app.data.Companion
import dev.hair.app.data.CompanionStage
import dev.hair.app.data.HabitLog
import dev.hair.app.data.MomentLog

fun stageName(stage: CompanionStage): String = when (stage) {
    CompanionStage.SEED -> "种子"
    CompanionStage.SPROUT -> "幼苗"
    CompanionStage.SAPLING -> "小树"
    CompanionStage.BLOOM -> "开花"
}

/** 探索：用同一份睡眠、心率、步数数据做的几个小玩法，每一行点进去是一个整页。 */
@Composable
fun ExploreTab(companion: Companion, habits: HabitLog, moments: MomentLog, animateIn: Boolean, onOpen: (Route) -> Unit) {
    val finished = moments.moments.count { !it.isOpen }
    val entries = listOf(
        ExploreEntry(
            ExploreGlyph.LAB, "习惯实验室", "记下傍晚到睡前的习惯，对比第二天的睡眠和心率",
            "${habits.habits.size} 个习惯 · ${habits.marks.map { it.date }.toSet().size} 天有记录", Route.Lab,
        ),
        ExploreEntry(
            ExploreGlyph.PLANT, "身体伙伴", "睡够、走够，它就长大；断了几天会回落",
            "${stageName(companion.stage)} · 成长值 ${companion.growth} / ${Companion.MAX_GROWTH}", Route.Pet,
        ),
        ExploreEntry(ExploreGlyph.SKY, "睡眠星空", "每晚一颗星，睡得越久越亮", null, Route.Sky),
        ExploreEntry(ExploreGlyph.ART, "30 天图", "把近 30 天的睡眠和步数画成一朵花", null, Route.Art),
        ExploreEntry(
            ExploreGlyph.PULSE, "心跳记忆", "记下看电影、玩游戏、做呼吸练习的时段，事后回看心率",
            if (finished > 0) "已记录 $finished 段" else null, Route.Moments,
        ),
    )
    TabPage("探索", "用同一份睡眠、心率和步数做的几个小玩法") {
        itemsIndexed(entries) { i, entry -> Entrance(i, animateIn) { ExploreRow(entry) { onOpen(entry.route) } } }
    }
}

private class ExploreEntry(val glyph: ExploreGlyph, val title: String, val description: String, val status: String?, val route: Route)

/** 一行入口：小图形、标题、一句说明，[ExploreEntry.status] 非空时再加一行当前状态。 */
@Composable
private fun ExploreRow(entry: ExploreEntry, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    RowFrame(onClick = onClick, minHeight = 96.dp, verticalPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ExploreGlyphView(entry.glyph, Modifier.size(44.dp))
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(entry.description, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                if (entry.status != null) {
                    Text(entry.status, style = MaterialTheme.typography.bodySmall, color = colors.secondary, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}
