package dev.hair.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.hair.app.data.Goals
import dev.hair.app.data.Night
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin

private const val SKY_DAYS = 30
private const val BACKGROUND_STARS = 48
private const val TOUCH_RADIUS_DP = 28
private const val DEEP_SHARE_FOR_GOLD = 0.25f
private const val BRIGHTNESS_CAP = 1.2f
private const val GOLDEN_GAMMA = -7046029254386353131L
private const val MIX_1 = -4658895280553007687L
private const val MIX_2 = -7723592293110705685L
private val SkyTop = Color(0xFF0B1026)
private val SkyBottom = Color(0xFF1B2A4A)
private val StarCool = Color(0xFFBFD9FF)
private val StarWarm = Color(0xFFFFD670)

/** 由 [seed] 定的 0..1 伪随机数（SplitMix64 混合）：同一晚每次打开位置都一样；[salt] 区分同一个种子的不同用途。 */
private fun unit(seed: Long, salt: Int): Float {
    var z = seed + (salt + 1) * GOLDEN_GAMMA
    z = (z xor (z ushr 30)) * MIX_1
    z = (z xor (z ushr 27)) * MIX_2
    z = z xor (z ushr 31)
    return (z ushr 40).toFloat() / (1L shl 24)
}

private class Star(val night: Night, val x: Float, val y: Float, val radiusDp: Float, val brightness: Float, val color: Color)

/** 近 [SKY_DAYS] 天的睡眠按日期从左到右排开，x 是相对位置 0..1，y 由日期决定。 */
private fun starsOf(nights: List<Night>, goals: Goals, today: LocalDate): List<Star> {
    val recent = nights.filter { it.date > today.minusDays(SKY_DAYS.toLong()) && it.date <= today }.sortedBy { it.date }
    return recent.mapIndexed { i, n ->
        val fraction = (n.asleepMin.toFloat() / goals.sleepMin).coerceAtMost(BRIGHTNESS_CAP) / BRIGHTNESS_CAP
        val deepShare = if (n.asleepMin > 0) n.deepMin.toFloat() / n.asleepMin else 0f
        Star(
            n,
            x = (i + 0.5f) / recent.size,
            y = 0.2f + 0.55f * unit(n.date.toEpochDay(), 1),
            radiusDp = 2f + 5f * fraction,
            brightness = 0.45f + 0.55f * fraction,
            color = lerp(StarCool, StarWarm, (deepShare / DEEP_SHARE_FOR_GOLD).coerceIn(0f, 1f)),
        )
    }
}

/**
 * 睡眠星空：近 30 天每晚一颗星，睡得越久越大越亮，深睡占比越高越偏金色，相邻两晚连成线。
 * 点一颗星看那一晚。
 */
@Composable
fun SkyPage(nights: List<Night>, goals: Goals, onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val stars = remember(nights, goals) { starsOf(nights, goals, LocalDate.now()) }
    var picked by remember(stars) { mutableStateOf<Int?>(null) }
    val twinkle by rememberInfiniteTransition(label = "twinkle").animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart),
        label = "twinklePhase",
    )
    PageScaffold("探索", onBack, "睡眠星空") {
        if (stars.isEmpty()) {
            Block { Text("还没有睡眠记录", style = MaterialTheme.typography.titleMedium) }
        } else {
            Canvas(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(320.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Brush.verticalGradient(listOf(SkyTop, SkyBottom)))
                    .pointerInput(stars) {
                        detectTapGestures { tap ->
                            val hit = stars.indices.minByOrNull { hypot(tap.x - stars[it].x * size.width, tap.y - stars[it].y * size.height) }
                            picked = hit?.takeIf {
                                hypot(tap.x - stars[it].x * size.width, tap.y - stars[it].y * size.height) <= TOUCH_RADIUS_DP.dp.toPx()
                            }
                        }
                    },
            ) {
                repeat(BACKGROUND_STARS) { i ->
                    drawCircle(Color.White.copy(alpha = 0.15f + 0.2f * unit(i.toLong(), 7)), 1.dp.toPx(), Offset(unit(i.toLong(), 3) * size.width, unit(i.toLong(), 5) * size.height))
                }
                stars.zipWithNext().forEach { (a, b) ->
                    drawLine(
                        Color.White.copy(alpha = 0.22f),
                        Offset(a.x * size.width, a.y * size.height),
                        Offset(b.x * size.width, b.y * size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
                stars.forEachIndexed { i, s ->
                    val center = Offset(s.x * size.width, s.y * size.height)
                    val alpha = s.brightness * (0.85f + 0.15f * sin(twinkle + i))
                    drawCircle(s.color.copy(alpha = alpha * 0.25f), s.radiusDp.dp.toPx() * 2.4f, center)
                    drawCircle(s.color.copy(alpha = alpha), s.radiusDp.dp.toPx(), center)
                    if (picked == i) drawCircle(Color.White, s.radiusDp.dp.toPx() * 2.2f, center, style = Stroke(1.5.dp.toPx()))
                }
            }
            val night = picked?.let { stars[it].night }
            Text(
                night?.let { "${nightDateText(it.date)} · ${formatMinutes(it.asleepMin)} · 深睡 ${formatMinutes(it.deepMin)}" } ?: "点一颗星，看那一晚",
                style = MaterialTheme.typography.titleMedium,
                color = if (night == null) colors.onSurfaceVariant else colors.onSurface,
                modifier = Modifier.padding(horizontal = PAGE_GUTTER, vertical = 16.dp),
            )
        }
        Footnote("星越大越亮，睡得越接近目标；颜色越偏金色，深睡占比越高。左边是更早的夜晚，位置的高低只是装饰。")
    }
}
