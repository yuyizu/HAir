package dev.hair.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import dev.hair.app.data.Season
import dev.hair.app.ui.theme.TideColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** 水面线：[top] 上下起伏的一条正弦浪。水体、涟漪和水面上的景物共用它，物体才会跟着浪起伏。 */
internal class WaterLine(val top: Float, val amplitude: Float, val phase: Float, val density: Float) {
    fun y(x: Float): Float = top + amplitude * sin(x / (WAVELENGTH_DP * density) * 2f * PI.toFloat() + phase)
}

private const val WAVELENGTH_DP = 240f

/** 右上角枝条末端离屏幕左缘的距离（dp）：随屏宽走，但至少 262dp，免得伸到大数字上。 */
internal fun branchTipXDp(widthDp: Float) = max(widthDp * 0.66f, 262f)

/** 风景的动态状态：枝条被点后摇晃，1 → 0 渐渐平息。 */
internal class SceneryState {
    var branchShake = 0f

    fun step(dt: Float) {
        branchShake = max(0f, branchShake - dt * 1.2f)
    }
}

internal enum class SceneryPart { BRANCH, FEATURE, SKY }

/** 点在水面以上 ([xDp], [yDp])，点的是枝条、水面上的景物（荷叶、芦苇、薄冰），还是天空；范围和绘制的位置一致。 */
internal fun sceneryPartAt(season: Season, xDp: Float, yDp: Float, widthDp: Float, surfaceDp: Float, statusBarDp: Float): SceneryPart {
    if (xDp >= branchTipXDp(widthDp) - 12f && yDp in (statusBarDp + 60f)..(statusBarDp + 140f)) return SceneryPart.BRANCH
    val onFeature = when (season) {
        Season.SPRING -> false
        Season.SUMMER -> xDp in widthDp * 0.28f..widthDp * 0.78f && yDp >= surfaceDp - 30f
        Season.AUTUMN -> xDp in widthDp * 0.66f..widthDp * 0.8f && yDp >= surfaceDp - 90f
        Season.WINTER -> (xDp <= widthDp * 0.1f || xDp >= widthDp - 30f) && yDp >= surfaceDp - 24f
    }
    return if (onFeature) SceneryPart.FEATURE else SceneryPart.SKY
}

internal fun sceneryCue(season: Season, part: SceneryPart): Cue = when (part) {
    SceneryPart.BRANCH -> when (season) {
        Season.SPRING -> Cue(SceneSound.CHIME)
        Season.SUMMER -> Cue(SceneSound.RUSTLE)
        Season.AUTUMN -> Cue(SceneSound.CRINKLE)
        Season.WINTER -> Cue(SceneSound.ICE)
    }
    SceneryPart.FEATURE -> when (season) {
        Season.SPRING -> Cue(SceneSound.DROP)
        Season.SUMMER -> Cue(SceneSound.DROP, 0.75f)
        Season.AUTUMN -> Cue(SceneSound.RUSTLE, 1.1f)
        Season.WINTER -> Cue(SceneSound.ICE, 1.15f)
    }
    SceneryPart.SKY -> when (season) {
        Season.SPRING -> Cue(SceneSound.BIRD)
        Season.SUMMER -> Cue(SceneSound.CRICKET)
        Season.AUTUMN -> Cue(SceneSound.WIND)
        Season.WINTER -> Cue(SceneSound.WIND, 1.3f)
    }
}

/**
 * 季节对水的影响：夏天浪大、鱼活跃、水草茂盛，冬天浪小、鱼迟缓沉底、水草枯矮。
 * [fishSpeed] 是游速倍率，[fishDepthShift] 是鱼活动水层的整体偏移（正为下沉），[plantDormant] 是水草向枯黄色靠拢的程度。
 */
internal class SeasonLook(
    val waveAmplitude: Float,
    val waveSpeed: Float,
    val fishSpeed: Float,
    val fishDepthShift: Float,
    val schoolSize: Int,
    val plantHeight: Float,
    val plantDormant: Float,
)

internal fun seasonLook(season: Season) = when (season) {
    Season.SPRING -> SeasonLook(1f, 0.7f, 1f, 0f, 7, 0.95f, 0f)
    Season.SUMMER -> SeasonLook(1.15f, 1f, 1.15f, -0.04f, 9, 1.1f, 0f)
    Season.AUTUMN -> SeasonLook(0.9f, 0.6f, 0.9f, 0.04f, 7, 0.85f, 0.4f)
    Season.WINTER -> SeasonLook(0.5f, 0.25f, 0.55f, 0.14f, 5, 0.6f, 0.65f)
}

private val PETAL = Color(0xFFF7B8C8)
private val PETAL_CORE = Color(0xFFF2B24A)
private val LOTUS = Color(0xFFF4A6BC)
private val LEAF_GREEN = Color(0xFF4F9A5E)
private val MAPLE = listOf(Color(0xFFC8501E), Color(0xFFE0841E), Color(0xFFD9A32B), Color(0xFF9E3B1F))
private val REED = Color(0xFFB89B5E)
private val CATTAIL = Color(0xFF7A4E2D)
private val WOOD = Color(0xFF7A5A48)
private val SNOW = Color(0xFFFFFFFF)
private val ICE = Color(0xFFF1F7FA)
private val ICE_EDGE = Color(0xFFB9D3E0)
private val FIREFLY = Color(0xFFD9F27A)
private val NIGHT = Color(0xFF0C1B21)

/** 夜间把景物色压暗，免得在夜空里发亮。 */
internal fun Color.forTheme(dark: Boolean) = if (dark) lerp(this, NIGHT, 0.4f) else this

/** 确定性的“随机”：同一个 [i]、[salt] 永远得到同一个 0..1 的数，景物每次出现的位置稳定。 */
internal fun hash(i: Int, salt: Int = 0): Float {
    val v = sin(i * 12.9898f + salt * 78.233f) * 43758.547f
    return v - floor(v)
}

private fun fade(p: Float, inEnd: Float = 0.08f, outStart: Float = 0.88f) = min(1f, min(p / inEnd, (1f - p) / (1f - outStart)))

/** 三次贝塞尔上 [s] 处的点。 */
private fun bezier(a: Offset, b: Offset, c: Offset, e: Offset, s: Float): Offset {
    val u = 1f - s
    return a * (u * u * u) + b * (3f * u * u * s) + c * (3f * u * s * s) + e * (s * s * s)
}

/**
 * 水面以上的季节景物，画在天空和云之后、水之前：近水的薄雾、右上角垂下的枝条（春花、夏叶、秋叶、冬雪），
 * 以及飘落的花瓣、落叶、雪；夏天白天是穿过天空的光柱，夜里是萤火。
 * 景物都放在右侧，让出左侧的大数字和说明。
 */
internal fun DrawScope.drawSeasonSky(season: Season, tide: TideColors, dark: Boolean, t: Float, scenery: SceneryState, line: WaterLine, statusBar: Float, d: Float) {
    drawMist(season, dark, line.top, d)
    if (season == Season.SUMMER) {
        if (dark) drawFireflies(t, line.top, statusBar, d) else drawLightShafts(tide, t, line.top, statusBar, d)
    }
    drawBranch(season, dark, t, scenery.branchShake, statusBar, d)
    when (season) {
        Season.SPRING -> drawFallingPetals(dark, t, line, statusBar, d)
        Season.AUTUMN -> drawFallingLeaves(dark, t, line, statusBar, d)
        Season.WINTER -> drawSnow(dark, t, line, statusBar, d)
        Season.SUMMER -> Unit
    }
}

/** 水面上的季节景物，画在水之后：落花、荷叶与荷花、落叶与芦苇、岸边的薄冰。 */
internal fun DrawScope.drawSeasonSurface(season: Season, dark: Boolean, t: Float, line: WaterLine, d: Float) {
    when (season) {
        Season.SPRING -> drawFloatingPetals(dark, t, line, d)
        Season.SUMMER -> drawLotus(dark, t, line, d)
        Season.AUTUMN -> {
            drawFloatingLeaves(dark, t, line, d)
            drawReeds(dark, t, line, d)
        }
        Season.WINTER -> drawIce(line, d)
    }
}

private fun DrawScope.drawMist(season: Season, dark: Boolean, surface: Float, d: Float) {
    val color = when (season) {
        Season.SPRING -> Color.White
        Season.SUMMER -> return
        Season.AUTUMN -> Color(0xFFFFC978)
        Season.WINTER -> Color.White
    }
    val alpha = if (dark) 0.08f else if (season == Season.WINTER) 0.32f else 0.22f
    val top = surface - 70f * d
    drawRect(
        Brush.verticalGradient(listOf(Color.Transparent, color.copy(alpha = alpha)), startY = top, endY = surface + 6f * d),
        topLeft = Offset(0f, top),
        size = Size(size.width, 76f * d),
    )
}

private fun DrawScope.drawLightShafts(tide: TideColors, t: Float, surface: Float, statusBar: Float, d: Float) {
    val sunX = size.width * 0.78f
    val sunY = (statusBar + 64f) * d
    val base = tide.sun
    // 光柱的源头：不论身体天气如何，夏天的天上总有一轮大太阳
    drawCircle(
        Brush.radialGradient(listOf(base.copy(alpha = base.alpha * 0.8f), Color.Transparent), center = Offset(sunX, sunY), radius = 110f * d),
        radius = 110f * d,
        center = Offset(sunX, sunY),
    )
    repeat(5) { k ->
        val endX = sunX + (k - 3) * 62f * d
        val path = Path().apply {
            moveTo(sunX - 4f * d, sunY)
            lineTo(sunX + 4f * d, sunY)
            lineTo(endX + 20f * d, surface)
            lineTo(endX - 20f * d, surface)
            close()
        }
        val shimmer = 0.6f + 0.4f * sin(t * 0.7f + k * 1.9f)
        drawPath(
            path,
            Brush.verticalGradient(listOf(base.copy(alpha = 0.34f * shimmer), Color.Transparent), startY = sunY, endY = surface),
        )
    }
}

private fun DrawScope.drawFireflies(t: Float, surface: Float, statusBar: Float, d: Float) {
    val top = (statusBar + 96f) * d
    repeat(9) { i ->
        val x = size.width * (0.3f + 0.65f * hash(i)) + sin(t * 0.4f + hash(i, 1) * 6f) * 18f * d
        val y = top + (surface - top - 16f * d) * hash(i, 2) + cos(t * 0.33f + hash(i, 3) * 5f) * 12f * d
        val blink = 0.5f + 0.5f * sin(t * 1.3f + hash(i, 4) * 9f)
        val glow = blink * blink
        drawCircle(
            Brush.radialGradient(listOf(FIREFLY.copy(alpha = 0.55f * glow), Color.Transparent), center = Offset(x, y), radius = 8f * d),
            radius = 8f * d,
            center = Offset(x, y),
        )
        drawCircle(FIREFLY.copy(alpha = 0.3f + 0.7f * glow), radius = 1.4f * d, center = Offset(x, y))
    }
}

/** 右上角伸进来的一段枝条；叶、花、雪挂在枝上的几个点。 */
private fun DrawScope.drawBranch(season: Season, dark: Boolean, t: Float, shake: Float, statusBar: Float, d: Float) {
    val w = size.width
    val tipX = branchTipXDp(w / d) * d
    val a = Offset(w + 12f * d, (statusBar + 86f) * d)
    val b = Offset(w * 0.9f, (statusBar + 78f) * d)
    val e = Offset(tipX, (statusBar + 116f) * d)
    val c = Offset((tipX + w) / 2f, (statusBar + 112f) * d)
    val nodes = listOf(0.18f, 0.32f, 0.46f, 0.6f, 0.74f, 0.88f, 1f).map { bezier(a, b, c, e, it) } +
        // 三根小枝的末端
        listOf(Offset(-14f, -16f) to 0.45f, Offset(-10f, 14f) to 0.7f, Offset(-6f, -10f) to 0.9f).map { (delta, s) ->
            bezier(a, b, c, e, s) + Offset(delta.x * d, delta.y * d)
        }
    withTransform({ rotate(sin(t * 0.5f) * 0.7f + shake * sin(t * 20f) * 3f, pivot = a) }) {
        val wood = WOOD.forTheme(dark)
        val stroke = Stroke(2.4f * d, cap = StrokeCap.Round)
        drawPath(Path().apply { moveTo(a.x, a.y); cubicTo(b.x, b.y, c.x, c.y, e.x, e.y) }, wood, style = stroke)
        listOf(Offset(-14f, -16f) to 0.45f, Offset(-10f, 14f) to 0.7f, Offset(-6f, -10f) to 0.9f).forEach { (delta, s) ->
            val from = bezier(a, b, c, e, s)
            drawLine(wood, from, from + Offset(delta.x * d, delta.y * d), strokeWidth = 1.6f * d, cap = StrokeCap.Round)
        }
        nodes.forEachIndexed { i, p -> drawFoliage(season, dark, p, i, d) }
    }
}

private fun DrawScope.drawFoliage(season: Season, dark: Boolean, p: Offset, i: Int, d: Float) {
    when (season) {
        Season.SPRING -> if (i % 4 == 3) {
            drawCircle(PETAL.forTheme(dark), 2.6f * d, p)
        } else {
            drawBlossom(dark, p, (5f + 3f * hash(i)) * d)
        }
        Season.SUMMER -> repeat(2) { k ->
            val angle = -40f + 80f * hash(i, k + 1) + k * 90f
            withTransform({ rotate(angle, pivot = p) }) {
                drawOval(LEAF_GREEN.forTheme(dark), Offset(p.x, p.y - 3f * d), Size(15f * d, 6f * d))
            }
        }
        Season.AUTUMN -> if (i % 3 != 2) drawMaple(MAPLE[i % MAPLE.size].forTheme(dark), p, 6.5f * d, hash(i) * 360f, 1f)
        Season.WINTER -> drawBranchSnow(dark, p, d)
    }
}

private fun DrawScope.drawBranchSnow(dark: Boolean, p: Offset, d: Float) {
    drawOval((if (dark) Color(0xFFCFE8EC) else SNOW).copy(alpha = 0.92f), Offset(p.x - 6f * d, p.y - 4.2f * d), Size(12f * d, 4f * d))
}

private fun DrawScope.drawBlossom(dark: Boolean, c: Offset, r: Float) {
    val petal = PETAL.forTheme(dark)
    repeat(5) { k ->
        val a = (k * 72f - 90f) * PI.toFloat() / 180f
        drawCircle(petal.copy(alpha = 0.92f), r * 0.55f, c + Offset(cos(a) * r * 0.62f, sin(a) * r * 0.62f))
    }
    drawCircle(PETAL_CORE.forTheme(dark), r * 0.22f, c)
}

/** 五角叶：外半径 [r]，内半径取一半，[squash] 压扁用来画躺在水面上的叶子。 */
private fun DrawScope.drawMaple(color: Color, c: Offset, r: Float, degrees: Float, squash: Float, alpha: Float = 1f) {
    val path = Path()
    repeat(10) { k ->
        val a = (k * 36f - 90f) * PI.toFloat() / 180f
        val radius = if (k % 2 == 0) r else r * 0.5f
        val x = c.x + cos(a) * radius
        val y = c.y + sin(a) * radius
        if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    withTransform({
        rotate(degrees, pivot = c)
        scale(1f, squash, pivot = c)
    }) { drawPath(path, color, alpha = alpha) }
}

/** 一个下落中的物件在 [0,1) 里走到哪了：每个有自己的周期和起点，所以不会齐刷刷同时落下。 */
private fun fallProgress(t: Float, i: Int, minPeriod: Float, spread: Float): Float =
    (t / (minPeriod + spread * hash(i, 1)) + hash(i)) % 1f

private fun DrawScope.drawFallingPetals(dark: Boolean, t: Float, line: WaterLine, statusBar: Float, d: Float) {
    val y0 = (statusBar + 110f) * d
    repeat(7) { i ->
        val p = fallProgress(t, i, 10f, 6f)
        val x = size.width * (0.7f + 0.28f * hash(i, 2)) - p * 70f * d * (0.4f + hash(i, 3)) + sin(p * 7f + hash(i, 4) * 6f) * 12f * d
        val y = y0 + (line.y(x) - y0) * p
        val c = Offset(x, y)
        withTransform({
            rotate(p * 400f + hash(i, 5) * 360f, pivot = c)
            scale(0.4f + 0.6f * abs(cos(p * 9f + i)), 1f, pivot = c)
        }) {
            drawOval(PETAL.forTheme(dark).copy(alpha = 0.9f * fade(p)), Offset(x - 4f * d, y - 2.2f * d), Size(8f * d, 4.4f * d))
        }
    }
}

private fun DrawScope.drawFallingLeaves(dark: Boolean, t: Float, line: WaterLine, statusBar: Float, d: Float) {
    val y0 = (statusBar + 100f) * d
    repeat(8) { i ->
        val p = fallProgress(t, i, 11f, 6f)
        val x = size.width * (0.55f + 0.43f * hash(i, 2)) - p * 80f * d * (0.3f + hash(i, 3)) + sin(p * 6f + hash(i, 4) * 6f) * 16f * d
        val y = y0 + (line.y(x) - y0) * p
        val flutter = 0.35f + 0.65f * abs(cos(p * 8f + i))
        drawMaple(MAPLE[i % MAPLE.size].forTheme(dark), Offset(x, y), 5.5f * d + 3f * d * hash(i, 6), p * 500f + hash(i, 5) * 360f, flutter, 0.95f * fade(p))
    }
}

private fun DrawScope.drawSnow(dark: Boolean, t: Float, line: WaterLine, statusBar: Float, d: Float) {
    val y0 = (statusBar + 4f) * d
    val color = if (dark) Color(0xFFE6F2F0) else SNOW
    repeat(22) { i ->
        val p = fallProgress(t, i, 9f, 7f)
        val x = size.width * hash(i, 2) + sin(p * 9f + hash(i, 3) * 6f) * 10f * d
        drawCircle(color.copy(alpha = 0.85f * fade(p, 0.06f, 0.9f)), (1.1f + 1.5f * hash(i, 4)) * d, Offset(x, y0 + (line.y(x) - y0) * p))
    }
}

/** 水面上缓缓漂过的落花；漂出一侧后从另一侧回来。 */
private fun DrawScope.drawFloatingPetals(dark: Boolean, t: Float, line: WaterLine, d: Float) {
    repeat(6) { i ->
        val span = size.width + 40f * d
        val x = (size.width * hash(i, 5) + t * (3f + 3f * hash(i, 6)) * d) % span - 20f * d
        drawOval(PETAL.forTheme(dark).copy(alpha = 0.85f), Offset(x - 3.5f * d, line.y(x) - 1.6f * d), Size(7f * d, 2.6f * d))
    }
}

private fun DrawScope.drawFloatingLeaves(dark: Boolean, t: Float, line: WaterLine, d: Float) {
    repeat(4) { i ->
        val span = size.width + 40f * d
        val x = (size.width * hash(i, 5) + t * (4f + 4f * hash(i, 6)) * d) % span - 20f * d
        drawMaple(MAPLE[i % MAPLE.size].forTheme(dark), Offset(x, line.y(x) + 0.5f * d), 6f * d, hash(i, 7) * 60f - 30f, 0.32f, 0.9f)
    }
}

private fun DrawScope.drawLotus(dark: Boolean, t: Float, line: WaterLine, d: Float) {
    val green = LEAF_GREEN.forTheme(dark)
    listOf(0.34f to 24f, 0.5f to 30f, 0.68f to 36f).forEachIndexed { i, (frac, rx) ->
        val x = size.width * frac + sin(t * 0.4f + i) * 3f * d
        val y = line.y(x) + 1.5f * d
        drawOval(green, Offset(x - rx * d, y - rx * 0.17f * d), Size(rx * 2f * d, rx * 0.34f * d))
        drawOval(lerp(green, Color.White, 0.18f), Offset(x - rx * 0.7f * d, y - rx * 0.1f * d), Size(rx * 1.4f * d, rx * 0.2f * d))
        if (i == 2) drawLotusFlower(dark, Offset(x + 4f * d, y - 1f * d), d)
    }
}

private fun DrawScope.drawLotusFlower(dark: Boolean, base: Offset, d: Float) {
    val h = 16f * d
    val w = 5.4f * d
    val petal = Path().apply {
        moveTo(0f, 0f)
        cubicTo(-w, -h * 0.35f, -w * 0.5f, -h * 0.9f, 0f, -h)
        cubicTo(w * 0.5f, -h * 0.9f, w, -h * 0.35f, 0f, 0f)
    }
    val color = LOTUS.forTheme(dark)
    listOf(-62f, 62f, -34f, 34f, 0f).forEachIndexed { k, angle ->
        withTransform({
            translate(base.x, base.y)
            rotate(angle, pivot = Offset.Zero)
        }) { drawPath(petal, if (k < 4) color.copy(alpha = 0.85f) else lerp(color, Color.White, 0.35f)) }
    }
    drawCircle(PETAL_CORE.forTheme(dark), 1.8f * d, base + Offset(0f, -2.5f * d))
}

private fun DrawScope.drawReeds(dark: Boolean, t: Float, line: WaterLine, d: Float) {
    val stem = REED.forTheme(dark)
    val head = CATTAIL.forTheme(dark)
    listOf(78f, 64f, 86f, 58f, 72f).forEachIndexed { i, height ->
        val x = size.width * 0.68f + i * 11f * d
        val base = Offset(x, line.y(x) + 3f * d)
        val sway = sin(t * 0.9f + i * 1.3f) * 4f * d
        val tip = Offset(x + sway + (i - 2) * 2f * d, base.y - height * d)
        val mid = Offset(x + sway * 0.3f, base.y - height * d * 0.5f)
        drawPath(
            Path().apply { moveTo(base.x, base.y); cubicTo(mid.x, mid.y, mid.x + sway * 0.4f, mid.y - 10f * d, tip.x, tip.y) },
            stem,
            style = Stroke(1.6f * d),
        )
        if (i % 2 == 0) {
            val c = Offset(tip.x - sway * 0.05f, tip.y + 10f * d)
            drawCattail(head, c, d)
        }
    }
}

private fun DrawScope.drawCattail(color: Color, c: Offset, d: Float) {
    drawOval(color, Offset(c.x - 2.2f * d, c.y - 6f * d), Size(4.4f * d, 12f * d))
}

/** 左右两岸各一片薄冰，贴着水面起伏。 */
private fun DrawScope.drawIce(line: WaterLine, d: Float) {
    fun slab(x0: Float, x1: Float) {
        val step = 8f * d
        val top = Path()
        val bottom = mutableListOf<Offset>()
        var x = x0
        var first = true
        while (x <= x1) {
            val jag = if ((x / step).toInt() % 2 == 0) 0f else 1.2f * d
            val y = line.y(x) - 2.2f * d
            if (first) top.moveTo(x, y) else top.lineTo(x, y)
            bottom += Offset(x, y + 4.5f * d + jag)
            first = false
            x += step
        }
        bottom.asReversed().forEach { top.lineTo(it.x, it.y) }
        top.close()
        drawPath(top, ICE.copy(alpha = 0.92f))
        drawPath(top, ICE_EDGE.copy(alpha = 0.7f), style = Stroke(1f * d))
        drawLine(ICE_EDGE.copy(alpha = 0.6f), Offset(x0 + (x1 - x0) * 0.4f, line.y(x0) - 1.6f * d), Offset(x0 + (x1 - x0) * 0.55f, line.y(x0) + 1.8f * d), strokeWidth = 0.8f * d)
    }
    slab(-6f * d, size.width * 0.1f)
    slab(size.width - 30f * d, size.width + 6f * d)
}
