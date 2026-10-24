package dev.hair.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.Composable
import dev.hair.app.data.Weather
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val SunColor = Color(0xFFFFB300)
private val CloudFront = Color(0xFF9AA5B1)
private val CloudBack = Color(0xFFC3CCD6)

private const val SUN_RAYS = 8

/** 用画布画的天气图标（没有图标库）：晴 → 少云 → 多云 → 阴，云越来越多、太阳越来越淡。 */
@Composable
fun WeatherGlyph(weather: Weather, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        when (weather) {
            Weather.SUNNY -> drawSun(Offset(s / 2, s / 2), s * 0.2f, SunColor)
            Weather.FAIR -> {
                drawSun(Offset(s * 0.38f, s * 0.38f), s * 0.17f, SunColor)
                drawCloud(Offset(s * 0.3f, s * 0.42f), s * 0.62f, CloudFront)
            }
            Weather.CLOUDY -> {
                drawSun(Offset(s * 0.34f, s * 0.34f), s * 0.13f, SunColor.copy(alpha = 0.55f))
                drawCloud(Offset(s * 0.12f, s * 0.36f), s * 0.76f, CloudFront)
            }
            Weather.OVERCAST -> {
                drawCloud(Offset(s * 0.28f, s * 0.14f), s * 0.6f, CloudBack)
                drawCloud(Offset(s * 0.1f, s * 0.34f), s * 0.78f, CloudFront)
            }
        }
    }
}

private fun DrawScope.drawSun(center: Offset, radius: Float, color: Color) {
    drawCircle(color, radius, center)
    repeat(SUN_RAYS) { i ->
        val angle = 2 * PI * i / SUN_RAYS
        val dx = cos(angle).toFloat()
        val dy = sin(angle).toFloat()
        drawLine(
            color,
            Offset(center.x + dx * radius * 1.45f, center.y + dy * radius * 1.45f),
            Offset(center.x + dx * radius * 1.9f, center.y + dy * radius * 1.9f),
            strokeWidth = radius * 0.24f,
            cap = StrokeCap.Round,
        )
    }
}

/** 三个圆加一块圆角底，[origin] 是左上角，[width] 是云的宽度。 */
private fun DrawScope.drawCloud(origin: Offset, width: Float, color: Color) {
    val h = width * 0.5f
    drawCircle(color, h * 0.4f, Offset(origin.x + width * 0.3f, origin.y + h * 0.5f))
    drawCircle(color, h * 0.55f, Offset(origin.x + width * 0.55f, origin.y + h * 0.38f))
    drawRoundRect(color, Offset(origin.x, origin.y + h * 0.45f), Size(width, h * 0.5f), CornerRadius(h * 0.25f))
}

/** 探索页每个入口的小图形；颜色跟数据走：睡眠浅海、活动赭金、心脏珊瑚。 */
enum class ExploreGlyph { LAB, PLANT, SKY, ART, PULSE }

@Composable
fun ExploreGlyphView(glyph: ExploreGlyph, modifier: Modifier = Modifier) {
    val sleep = MaterialTheme.colorScheme.secondary
    val steps = MaterialTheme.colorScheme.primary
    val heart = MaterialTheme.colorScheme.tertiary
    Canvas(modifier) {
        val s = size.minDimension
        val line = s * 0.07f
        when (glyph) {
            // 做了和没做：两根高低不同的柱
            ExploreGlyph.LAB -> {
                drawRoundRect(sleep, Offset(s * 0.2f, s * 0.15f), Size(s * 0.24f, s * 0.7f), CornerRadius(s * 0.12f))
                drawRoundRect(sleep.copy(alpha = 0.4f), Offset(s * 0.56f, s * 0.4f), Size(s * 0.24f, s * 0.45f), CornerRadius(s * 0.12f))
            }
            // 一株苗：茎、两片叶、顶上一点赭金
            ExploreGlyph.PLANT -> {
                drawLine(sleep, Offset(s * 0.5f, s * 0.9f), Offset(s * 0.5f, s * 0.3f), strokeWidth = line, cap = StrokeCap.Round)
                drawOval(sleep, Offset(s * 0.5f, s * 0.42f), Size(s * 0.3f, s * 0.16f))
                drawOval(sleep, Offset(s * 0.2f, s * 0.58f), Size(s * 0.3f, s * 0.16f))
                drawCircle(steps, s * 0.09f, Offset(s * 0.5f, s * 0.2f))
            }
            // 三颗连线的星，最亮的一颗偏金
            ExploreGlyph.SKY -> {
                val a = Offset(s * 0.18f, s * 0.62f)
                val b = Offset(s * 0.5f, s * 0.3f)
                val c = Offset(s * 0.84f, s * 0.58f)
                drawLine(sleep.copy(alpha = 0.4f), a, b, strokeWidth = line * 0.5f)
                drawLine(sleep.copy(alpha = 0.4f), b, c, strokeWidth = line * 0.5f)
                drawCircle(sleep, s * 0.07f, a)
                drawCircle(steps, s * 0.12f, b)
                drawCircle(sleep, s * 0.09f, c)
            }
            // 一圈辐条，睡眠和步数交替
            ExploreGlyph.ART -> {
                val center = Offset(s / 2, s / 2)
                repeat(12) { i ->
                    val angle = 2 * PI * i / 12
                    val inner = s * 0.16f
                    val outer = s * (if (i % 2 == 0) 0.46f else 0.36f)
                    drawLine(
                        if (i % 2 == 0) sleep else steps,
                        Offset(center.x + cos(angle).toFloat() * inner, center.y + sin(angle).toFloat() * inner),
                        Offset(center.x + cos(angle).toFloat() * outer, center.y + sin(angle).toFloat() * outer),
                        strokeWidth = line,
                        cap = StrokeCap.Round,
                    )
                }
            }
            // 一次心跳
            ExploreGlyph.PULSE -> {
                val path = Path().apply {
                    moveTo(s * 0.04f, s * 0.55f)
                    lineTo(s * 0.3f, s * 0.55f)
                    lineTo(s * 0.4f, s * 0.2f)
                    lineTo(s * 0.54f, s * 0.86f)
                    lineTo(s * 0.64f, s * 0.42f)
                    lineTo(s * 0.7f, s * 0.55f)
                    lineTo(s * 0.96f, s * 0.55f)
                }
                drawPath(path, heart, style = Stroke(line, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}
