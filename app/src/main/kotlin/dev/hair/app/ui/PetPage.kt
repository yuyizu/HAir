package dev.hair.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import dev.hair.app.data.Companion
import dev.hair.app.data.CompanionStage
import dev.hair.app.data.Goals
import dev.hair.app.data.Weather

private const val SWAY_DEGREES = 3f
private const val SWAY_MS = 2600

/** 植物各部分的颜色，取自主题：茎叶是浅海色，土和花是赭金色。 */
private class PlantColors(val stem: Color, val leaf: Color, val soil: Color, val seed: Color, val petal: Color, val center: Color)

/** 植物的尺寸都按这个参考高度算，画布本身按阶段变矮，种子阶段不会在页面上留一大片空白。 */
private val PLANT_UNIT = 260.dp

/** 土的下沿到画布底边的留白。 */
private val SOIL_MARGIN = 20.dp

/** 叶子相对水平线抬起的角度：晴天挺拔，阴天耷拉。没有天气结论时按“少云”画。 */
private fun leafLift(weather: Weather?): Float = when (weather) {
    Weather.SUNNY -> 38f
    Weather.FAIR, null -> 22f
    Weather.CLOUDY -> 6f
    Weather.OVERCAST -> -14f
}

private fun moodText(weather: Weather?): String = when (weather) {
    Weather.SUNNY -> "今天精神很好"
    Weather.FAIR, null -> "今天状态平稳"
    Weather.CLOUDY -> "今天有点蔫"
    Weather.OVERCAST -> "今天蔫蔫的，早点休息"
}

/**
 * 身体伙伴：一株植物，阶段由近 30 天睡眠、步数达标的天数推出（[companion]），
 * 叶子的姿态跟着身体天气 [weather] 走。它没有另存的进度，所以不会“饿死”，只是回落。
 */
@Composable
fun PetPage(companion: Companion, weather: Weather?, goals: Goals, onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    PageScaffold("探索", onBack, "身体伙伴") {
        Block {
            Plant(companion.stage, weather)
            Text(stageName(companion.stage), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp))
            Text(moodText(weather), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        InfoRow("成长值", "${companion.growth} / ${Companion.MAX_GROWTH}")
        InfoRow("睡眠达标", "${companion.sleepDays} 天（≥ ${formatSleepGoal(goals.sleepMin)}）")
        InfoRow("步数达标", "${companion.stepDays} 天（≥ %,d 步）".format(goals.steps))
        InfoRow(
            "下一阶段",
            companion.toNextStage()?.let { (stage, left) -> "再 $left 点长成「${stageName(stage)}」" } ?: "已经开花",
        )
        Footnote(
            "成长值 = 近 ${Companion.WINDOW_DAYS} 天里睡眠达标的天数 + 步数达标的天数，每天重新算，" +
                "所以断几天它会回落。目标可以在「我的」里调整。",
        )
    }
}

@Composable
private fun Plant(stage: CompanionStage, weather: Weather?) {
    val sway by rememberInfiniteTransition(label = "sway").animateFloat(
        initialValue = -SWAY_DEGREES,
        targetValue = SWAY_DEGREES,
        animationSpec = infiniteRepeatable(tween(SWAY_MS, easing = LinearEasing), RepeatMode.Reverse),
        label = "swayDegrees",
    )
    val lift = leafLift(weather)
    val colors = MaterialTheme.colorScheme
    val palette = PlantColors(
        stem = colors.secondary,
        leaf = colors.secondary.copy(alpha = 0.8f),
        soil = colors.primary.copy(alpha = 0.3f),
        seed = colors.primary,
        petal = colors.primary.copy(alpha = 0.7f),
        center = colors.primary,
    )
    val canvasHeight = when (stage) {
        CompanionStage.SEED -> 72.dp
        CompanionStage.SPROUT -> 130.dp
        CompanionStage.SAPLING -> 190.dp
        CompanionStage.BLOOM -> 230.dp
    }
    Canvas(Modifier.fillMaxWidth().height(canvasHeight)) {
        val unit = PLANT_UNIT.toPx()
        val base = Offset(size.width / 2, size.height - SOIL_MARGIN.toPx())
        drawOval(palette.soil, Offset(base.x - size.width * 0.22f, base.y - 10.dp.toPx()), Size(size.width * 0.44f, 26.dp.toPx()))
        rotate(if (stage == CompanionStage.SEED) 0f else sway, pivot = base) {
            when (stage) {
                CompanionStage.SEED -> drawOval(palette.seed, Offset(base.x - 9.dp.toPx(), base.y - 20.dp.toPx()), Size(18.dp.toPx(), 24.dp.toPx()))
                CompanionStage.SPROUT -> drawStem(palette, base, height = unit * 0.3f, leafPairs = 1, lift = lift)
                CompanionStage.SAPLING -> drawStem(palette, base, height = unit * 0.5f, leafPairs = 3, lift = lift)
                CompanionStage.BLOOM -> {
                    val top = drawStem(palette, base, height = unit * 0.55f, leafPairs = 3, lift = lift)
                    drawFlower(palette, top, radius = unit * 0.13f)
                }
            }
        }
    }
}

/** 画茎和 [leafPairs] 对叶子，返回茎顶的位置。 */
private fun DrawScope.drawStem(palette: PlantColors, base: Offset, height: Float, leafPairs: Int, lift: Float): Offset {
    val top = Offset(base.x, base.y - height)
    drawLine(palette.stem, base, top, strokeWidth = 7.dp.toPx(), cap = StrokeCap.Round)
    repeat(leafPairs) { i ->
        val attach = Offset(base.x, base.y - height * (0.35f + 0.55f * i / leafPairs.coerceAtLeast(1)))
        val length = height * (0.46f - 0.08f * i)
        drawLeaf(palette, attach, length, lift, mirrored = false)
        drawLeaf(palette, attach, length, lift, mirrored = true)
    }
    return top
}

private fun DrawScope.drawLeaf(palette: PlantColors, attach: Offset, length: Float, lift: Float, mirrored: Boolean) {
    val angle = if (mirrored) 180f + lift else -lift
    rotate(angle, pivot = attach) {
        drawOval(palette.leaf, Offset(attach.x, attach.y - length * 0.2f), Size(length, length * 0.4f))
    }
}

private fun DrawScope.drawFlower(palette: PlantColors, center: Offset, radius: Float) {
    repeat(8) { i ->
        rotate(i * 45f, pivot = center) {
            drawOval(palette.petal, Offset(center.x - radius * 0.3f, center.y - radius), Size(radius * 0.6f, radius * 0.9f))
        }
    }
    drawCircle(palette.center, radius * 0.35f, center)
}
