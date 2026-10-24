package dev.hair.app.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import dev.hair.app.data.Climate
import dev.hair.app.data.WaterType
import dev.hair.app.ui.theme.TideColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** 提示牌停留和淡出的时长（秒）。 */
internal const val TOAST_HOLD = 1.0f
internal const val TOAST_FADE = 0.6f

private const val INTRO_SECONDS = 1.2f
private const val MAX_PITCH_DEGREES = 25f

private val BLADE_GREEN = Color(0xFF3F9B6B)
private val KELP_GREEN = Color(0xFF6E8F4A)
private val DORMANT = Color(0xFF8A7A4A)
private val CORAL_PINK = Color(0xFFF2788C)
private val CORAL_ORANGE = Color(0xFFF29A58)
private val ANEMONE = Color(0xFFD86FA8)
private val ANEMONE_TIP = Color(0xFFF6C3DC)
private val SHRIMP_FRESH = Color(0xFFE6D3B8)
private val SHRIMP_SEA = Color(0xFFF2A57A)

/** 左侧是阶段标签：鱼游到字下面时淡下去，免得盖住字。 */
private fun labelDim(xDp: Float) = 0.4f + 0.6f * ((xDp - 70f) / 80f).coerceIn(0f, 1f)

/**
 * 画水里的一切：水草、虾、小鱼群、鱼、气泡。[surface]..[bottom] 是水体在画布上的范围（像素），
 * [t] 是场景时间；刚进场的头一秒多从无到有淡入，等水位涨起来。
 */
internal fun DrawScope.drawAquarium(a: Aquarium, look: SeasonLook, t: Float, surface: Float, bottom: Float, dark: Boolean, d: Float) {
    val intro = (t / INTRO_SECONDS).coerceIn(0f, 1f)
    val depth = bottom - surface
    fun px(x: Float) = x * size.width
    fun py(y: Float) = surface + y * depth

    a.plants.forEach { drawPlant(it, t, bottom, look, dark, d, intro) }

    val shrimpColor = (if (a.habitat.water == WaterType.FRESH) SHRIMP_FRESH else SHRIMP_SEA).forTheme(dark)
    a.shrimps.forEach { drawShrimp(it, px(it.x), py(it.y), shrimpColor, t, d, intro * labelDim(px(it.x) / d)) }

    val schoolColor = when {
        a.habitat.water == WaterType.SEA && a.habitat.climate == Climate.TROPICAL -> Color(0xFFFFD25A)
        a.habitat.water == WaterType.SEA -> Color(0xFFAFC9E8)
        else -> Color(0xFFCFE3E6)
    }
    a.smalls.forEach { s ->
        val (x, y) = a.smallPosition(s, t)
        drawSmallFish(px(x), py(y), (9f + 2f * sin(s.phase)) * d, a.school.facing, t * 14f + s.phase, schoolColor, 0.85f * intro * labelDim(px(x) / d))
    }

    a.fish.forEach { f ->
        val cx = px(f.x)
        val pitch = atan2(f.vy * depth, abs(f.vx) * size.width) * 180f / PI.toFloat()
        drawFish(f, cx, py(f.y), pitch.coerceIn(-MAX_PITCH_DEGREES, MAX_PITCH_DEGREES), d, intro * labelDim(cx / d))
    }

    a.bubbles.forEach { b ->
        val c = Offset(px(b.x), py(b.y))
        drawCircle(Color.White.copy(alpha = 0.5f * intro), b.radiusDp * d, c, style = Stroke(0.8f * d))
        drawCircle(Color.White.copy(alpha = 0.6f * intro), b.radiusDp * 0.25f * d, c + Offset(-b.radiusDp * 0.3f * d, -b.radiusDp * 0.3f * d))
    }
}

private fun bodyPath(h: Float) = Path().apply {
    moveTo(0.5f, 0f)
    cubicTo(0.36f, -h * 0.55f, -0.12f, -h * 0.6f, -0.5f, -h * 0.12f)
    lineTo(-0.5f, h * 0.12f)
    cubicTo(-0.12f, h * 0.6f, 0.36f, h * 0.55f, 0.5f, 0f)
    close()
}

private val BODY_PATHS = BodyShape.entries.associateWith { bodyPath(it.height) }

/** 鱼画在单位坐标里：体长为 1，鼻尖在 +x。外面的变换负责位置、大小、朝向和俯仰。 */
private fun DrawScope.drawFish(f: Fish, cx: Float, cy: Float, pitch: Float, d: Float, alpha: Float) {
    val s = f.species
    val h = s.shape.height
    val body = BODY_PATHS.getValue(s.shape)
    val fin = lerp(s.body, s.accent, 0.45f).copy(alpha = 0.85f)
    val swing = sin(f.phase) * 0.07f
    val tailHalf = h * 0.62f * s.shape.tail * s.tailScale
    val tailLength = 0.34f * (0.8f + 0.2f * s.tailScale)
    val finHeight = h * 0.55f * s.shape.dorsal + 0.05f
    val sign = if (f.facing >= 0f) 1f else -1f
    withTransform({
        translate(cx, cy)
        rotate(pitch * sign, pivot = Offset.Zero)
        scale(s.lengthDp * d * f.facing, s.lengthDp * d, pivot = Offset.Zero)
    }) {
        val tail = Path().apply {
            moveTo(-0.46f, 0f)
            lineTo(-0.46f - tailLength, -tailHalf + swing)
            lineTo(-0.46f - tailLength * 0.78f, swing * 0.6f)
            lineTo(-0.46f - tailLength, tailHalf + swing)
            close()
        }
        val dorsal = Path().apply {
            moveTo(0.15f, -h * 0.4f)
            cubicTo(0.05f, -h * 0.4f - finHeight, -0.12f, -h * 0.4f - finHeight * 0.9f, -0.25f, -h * 0.3f)
            close()
        }
        val pelvic = Path().apply {
            moveTo(0.05f, h * 0.38f)
            lineTo(-0.1f, h * 0.38f + finHeight * 0.5f)
            lineTo(-0.15f, h * 0.33f)
            close()
        }
        drawPath(tail, fin, alpha = alpha)
        drawPath(dorsal, fin, alpha = alpha)
        drawPath(pelvic, fin, alpha = alpha)
        drawPath(
            body,
            Brush.verticalGradient(0f to s.body, 0.55f to s.body, 1f to s.belly, startY = -h / 2f, endY = h / 2f),
            alpha = alpha,
        )
        clipPath(body) { drawPattern(s, h, alpha) }
        drawCircle(Color(0xFF10202A).copy(alpha = alpha), 0.034f, Offset(0.33f, -h * 0.1f))
        drawCircle(Color.White.copy(alpha = 0.8f * alpha), 0.012f, Offset(0.336f, -h * 0.13f))
    }
}

private val SPOT_POSITIONS = listOf(0.3f to -0.18f, 0.12f to 0.05f, -0.05f to -0.2f, -0.15f to 0.12f, -0.28f to -0.05f, 0.2f to 0.2f, -0.35f to 0.15f)

private fun DrawScope.drawPattern(s: Species, h: Float, alpha: Float) {
    val color = s.accent.copy(alpha = alpha)
    when (s.pattern) {
        Pattern.NONE -> Unit
        Pattern.BANDS -> {
            val width = if (s.bands >= 4) 0.05f else 0.07f
            repeat(s.bands) { i ->
                val x = if (s.bands == 1) 0.12f else 0.3f - 0.6f * i / (s.bands - 1)
                drawRect(color, Offset(x - width / 2f, -h), Size(width, h * 2f))
            }
        }
        Pattern.STRIPE -> drawRect(color, Offset(-0.4f, -h * 0.14f), Size(0.8f, h * 0.16f))
        Pattern.PATCHES -> {
            drawOval(color, Offset(0.03f, -h * 0.5f), Size(0.24f, h * 0.55f))
            drawOval(color, Offset(-0.26f, -h * 0.55f), Size(0.2f, h * 0.5f))
            drawOval(color, Offset(-0.1f, -h * 0.02f), Size(0.14f, h * 0.3f))
        }
        Pattern.SPOTS -> SPOT_POSITIONS.forEach { (x, y) -> drawCircle(color, 0.022f, Offset(x, y * h * 2.2f)) }
    }
}

private fun DrawScope.drawSmallFish(cx: Float, cy: Float, length: Float, facing: Float, phase: Float, color: Color, alpha: Float) {
    withTransform({
        translate(cx, cy)
        scale(length * facing, length, pivot = Offset.Zero)
    }) {
        val swing = sin(phase) * 0.08f
        drawPath(BODY_PATHS.getValue(BodyShape.SLIM), color, alpha = alpha)
        drawPath(
            Path().apply {
                moveTo(-0.4f, 0f)
                lineTo(-0.85f, -0.2f + swing)
                lineTo(-0.85f, 0.2f + swing)
                close()
            },
            color,
            alpha = alpha * 0.9f,
        )
    }
}

private fun DrawScope.drawShrimp(s: Shrimp, cx: Float, cy: Float, color: Color, t: Float, d: Float, alpha: Float) {
    val hop = if (s.flick > 0f) abs(sin(s.flick * 8f)) * 8f * d else 0f
    withTransform({
        translate(cx, cy - hop)
        scale(16f * d * s.dir, 16f * d, pivot = Offset.Zero)
    }) {
        val c = color.copy(alpha = 0.9f * alpha)
        val segments = List(5) { k -> Offset(0.42f - 0.2f * k, -0.16f * sin(k * PI.toFloat() / 5f)) }
        segments.forEachIndexed { k, p ->
            drawCircle(c, 0.17f - 0.015f * k, p)
            if (k in 0..3) {
                val swingLeg = sin(s.phase * 7f + k) * 0.06f
                drawLine(c, p + Offset(0f, 0.08f), p + Offset(-0.05f + swingLeg, 0.26f), strokeWidth = 0.03f, cap = StrokeCap.Round)
            }
        }
        drawPath(
            Path().apply {
                moveTo(-0.5f, 0f)
                lineTo(-0.72f, -0.12f)
                lineTo(-0.72f, 0.12f)
                close()
            },
            c,
        )
        val sway = sin(t * 2f + s.phase) * 0.05f
        drawLine(c, Offset(0.5f, -0.15f), Offset(0.95f, -0.42f + sway), strokeWidth = 0.025f, cap = StrokeCap.Round)
        drawLine(c, Offset(0.5f, -0.12f), Offset(0.92f, -0.2f + sway), strokeWidth = 0.025f, cap = StrokeCap.Round)
        drawCircle(Color(0xFF10202A).copy(alpha = alpha), 0.035f, Offset(0.5f, -0.18f))
    }
}

private fun DrawScope.drawPlant(p: Plant, t: Float, bottom: Float, look: SeasonLook, dark: Boolean, d: Float, alpha: Float) {
    val x0 = p.x * size.width
    val base = bottom + 4f * d
    val height = p.heightDp * d
    when (p.kind) {
        PlantKind.BLADES, PlantKind.KELP -> {
            val kelp = p.kind == PlantKind.KELP
            val count = if (kelp) 3 else 4
            val color = lerp(if (kelp) KELP_GREEN else BLADE_GREEN, DORMANT, look.plantDormant).forTheme(dark).copy(alpha = 0.9f * alpha)
            repeat(count) { j ->
                val offset = j - (count - 1) / 2f
                val h = height * (0.7f + 0.3f * hash(j, p.phase.toInt()))
                val bx = x0 + offset * (if (kelp) 9f else 6f) * d
                val sway = sin((t * 0.8f + p.phase + j * 0.7f) * (1f + p.shake * 5f)) * (4f + 0.07f * p.heightDp) * (1f + p.shake * 2.5f) * d
                val lean = offset * 6f * d
                drawPath(
                    Path().apply {
                        moveTo(bx, base)
                        cubicTo(bx + sway * 0.2f, base - h * 0.35f, bx + lean + sway * 0.7f, base - h * 0.7f, bx + lean + sway, base - h)
                    },
                    color,
                    style = Stroke(if (kelp) 5f * d else 2.4f * d, cap = StrokeCap.Round),
                )
            }
        }
        PlantKind.CORAL -> {
            val color = (if (p.phase.toInt() % 2 == 0) CORAL_PINK else CORAL_ORANGE).copy(alpha = alpha)
            val tip = lerp(color, Color.White, 0.4f)
            // 被点后珊瑚轻轻颤一下
            val wobble = sin(t * 30f) * p.shake * 0.04f
            fun limb(fromX: Float, fromY: Float, toX: Float, toY: Float, width: Float) {
                drawLine(color, Offset(x0 + fromX * height, base - fromY * height), Offset(x0 + (toX + wobble * toY) * height, base - toY * height), strokeWidth = width * d, cap = StrokeCap.Round)
                drawCircle(tip, width * 0.6f * d, Offset(x0 + (toX + wobble * toY) * height, base - toY * height))
            }
            limb(0f, 0f, 0f, 0.55f, 4f)
            limb(0f, 0.35f, -0.35f, 0.8f, 3f)
            limb(-0.2f, 0.58f, -0.5f, 0.62f, 2.4f)
            limb(0f, 0.35f, 0.38f, 0.78f, 3f)
            limb(0.22f, 0.57f, 0.5f, 0.6f, 2.4f)
            limb(0f, 0.55f, 0.08f, 1f, 3f)
        }
        PlantKind.ANEMONE -> {
            drawOval(ANEMONE.copy(alpha = alpha), Offset(x0 - 12f * d, base - 9f * d), Size(24f * d, 12f * d))
            repeat(11) { k ->
                val angle = (-60f + 12f * k) * PI.toFloat() / 180f
                val length = (14f + 6f * hash(k)) * d
                val sway = sin((t * 1.6f + k * 0.8f + p.phase) * (1f + p.shake * 3f)) * 3f * (1f + p.shake * 2.5f) * d
                val from = Offset(x0 + sin(angle) * 9f * d, base - 7f * d)
                val to = Offset(x0 + sin(angle) * (9f * d + length * 0.7f) + sway, base - 7f * d - length)
                drawLine(ANEMONE.copy(alpha = alpha), from, to, strokeWidth = 2.2f * d, cap = StrokeCap.Round)
                drawCircle(ANEMONE_TIP.copy(alpha = alpha), 1.8f * d, to)
            }
        }
    }
}

/** 点中的动物名：深色小牌配浅字，浮在它上方，停一会儿再淡出。 */
internal fun DrawScope.drawToast(toast: Toast, now: Float, surface: Float, bottom: Float, tide: TideColors, measurer: TextMeasurer, style: TextStyle, d: Float) {
    val age = max(0f, now - toast.bornAt)
    val alpha = ((TOAST_HOLD + TOAST_FADE - age) / TOAST_FADE).coerceIn(0f, 1f)
    if (alpha <= 0f) return
    val layout = measurer.measure(toast.name, style)
    val padX = 9f * d
    val padY = 4f * d
    val width = layout.size.width + 2f * padX
    val height = layout.size.height + 2f * padY
    val cx = (toast.who.x * size.width).coerceIn(width / 2f + 8f * d, size.width - width / 2f - 8f * d)
    val cy = surface + toast.who.y * (bottom - surface) - 28f * d - min(age, 1f) * 8f * d
    drawRoundRect(Color(0xFF082A38).copy(alpha = 0.6f * alpha), Offset(cx - width / 2f, cy - height / 2f), Size(width, height), CornerRadius(height / 2f))
    drawText(layout, color = tide.onWater, topLeft = Offset(cx - layout.size.width / 2f, cy - layout.size.height / 2f), alpha = alpha)
}
