package dev.hair.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hair.app.data.Night
import dev.hair.app.data.SceneSettings
import dev.hair.app.data.SleepStage
import dev.hair.app.data.Weather
import dev.hair.app.ui.theme.DmSans
import dev.hair.app.ui.theme.TideColors
import dev.hair.app.ui.theme.TideDisplay
import dev.hair.app.ui.theme.inSeason
import dev.hair.app.ui.theme.tide
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.max

private val LIP = 28.dp
private val LABEL_MIN_LAYER = 22.dp
private val SURFACE_HIGH_BELOW_STATUS_BAR = 214.dp
private const val SURFACE_TRAVEL = 0.28f
private const val RIPPLE_MS = 900
private const val TEXT_FADE_DP = 140

/** 水面上的珊瑚浮标：只在需要标出偏离时出现。 */
class BuoyNote(val title: String, val detail: String)

/**
 * 今日页的水景：睡眠时长决定水位，睡眠阶段是分层的水体，天气决定天空，[scene] 的季节决定水色和水面上下的景物，
 * 水里有按 [scene] 的环境养的鱼、虾、小鱼群和水草；点水里的动物会有反应，点空处会引来附近的鱼。
 * 画到 [modifier] 给的高度的底边为止，底部 [LIP] 画成抽屉的圆角上沿，下面接指标行。
 * [animateIn] 为 true 时水位从底部涨起、数字从 0 滚起；之后数据变化从当前位置接着走。
 */
@Composable
fun TideScene(
    night: Night?,
    goalMin: Long,
    weather: Weather?,
    scene: SceneSettings,
    today: LocalDate,
    dateText: String,
    buoy: BuoyNote?,
    animateIn: Boolean,
    onOpenNight: (LocalDate) -> Unit,
    onOpenForecast: () -> Unit,
    /** 本页列表已滚出的像素；水面以上的文字随它淡出，免得滑到状态栏图标下面。 */
    scrollPx: () -> Float,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val season = scene.seasonOn(today)
    val dark = isSystemInDarkTheme()
    val sea = MaterialTheme.tide.inSeason(season)
    val look = remember(season) { seasonLook(season) }
    val species = remember(scene.habitat, scene.species) { speciesFor(scene.habitat, scene.species) }
    val aquarium = remember(species, look) { Aquarium(species, scene.habitat, look) }
    val scenery = remember { SceneryState() }
    val clock = rememberSceneClock { dt ->
        aquarium.step(dt)
        scenery.step(dt)
    }
    val sounds = remember { SceneSounds() }
    DisposableEffect(Unit) { onDispose { sounds.release() } }
    fun play(cue: Cue) {
        if (scene.sound) sounds.play(cue.sound, cue.rate)
    }
    val measurer = rememberTextMeasurer()
    val toastStyle = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp)
    val toast = aquarium.toast
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(((TOAST_HOLD + TOAST_FADE) * 1000).toLong())
            aquarium.toast = null
        }
    }
    val shape = remember(night, goalMin) { night?.let { tide(it, goalMin) } }
    val targetLevel = shape?.level ?: 0f
    val level = remember { Animatable(if (animateIn) 0f else targetLevel) }
    LaunchedEffect(targetLevel) { level.animateTo(targetLevel, spring(dampingRatio = 1f, stiffness = 170f)) }
    val minutes = animatedCount(night?.asleepMin, animateIn)

    val ripple = remember { Animatable(1f) }
    var tap by remember { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()

    val description = (night?.let { n ->
        "${sleepCaption(n.date, today)} ${formatMinutes(n.asleepMin)}" +
            (shape?.layers.orEmpty().joinToString("") { "，${stageLabel(it.stage)} ${formatMinutes(it.minutes)}" })
    } ?: "没有昨夜的睡眠") + "。水里有${species.joinToString("、") { it.name }}"

    BoxWithConstraints(modifier.fillMaxWidth().semantics { contentDescription = description }) {
        val density = LocalDensity.current
        val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val surfaceHigh = statusBar + SURFACE_HIGH_BELOW_STATUS_BAR
        val surfaceLow = surfaceHigh + maxHeight * SURFACE_TRAVEL
        val lipTop = maxHeight - LIP
        // 各处在绘制和布局阶段读 level，水位变化时只重绘、不重组
        fun surfaceDp(): Float = surfaceLow.value - (surfaceLow.value - surfaceHigh.value) * level.value

        Canvas(
            Modifier.fillMaxSize().pointerInput(aquarium, scene.sound, season) {
                detectTapGestures { offset ->
                    tap = offset
                    scope.launch {
                        ripple.snapTo(0f)
                        ripple.animateTo(1f, tween(RIPPLE_MS, easing = LinearOutSlowInEasing))
                    }
                    val d = density.density
                    val top = surfaceDp() * d
                    val bottom = lipTop.value * d
                    if (offset.y > top && bottom > top) {
                        // 点中动物或水草按它的声音；点在空水里是一滴水，音调随左右位置略有高低
                        val hit = aquarium.touch(offset.x / size.width, ((offset.y - top) / (bottom - top)).coerceAtMost(1f), size.width / d, (bottom - top) / d, clock())
                        play(hit?.cue ?: Cue(SceneSound.DROP, 0.9f + 0.3f * offset.x / size.width))
                    } else {
                        val part = sceneryPartAt(season, offset.x / d, offset.y / d, size.width / d, top / d, statusBar.value)
                        if (part == SceneryPart.BRANCH) scenery.branchShake = 1f
                        play(sceneryCue(season, part))
                    }
                }
            },
        ) {
            val d = density.density
            val t = clock()
            val surface = surfaceDp() * d
            val depth = lipTop.value * d - surface
            fun line(top: Float, layer: Int) = WaterLine(top, (if (layer == 0) 4f else 3f) * d * look.waveAmplitude, 0.6f + layer * 1.3f + t * look.waveSpeed, d)
            val surfaceLine = line(surface, 0)
            drawSky(sea, weather, surface, d, statusBar.value)
            drawSeasonSky(season, sea, dark, t, scenery, surfaceLine, statusBar.value, d)
            val spans = layerSpans(shape?.layers.orEmpty(), surface, depth)
            if (spans.isEmpty()) {
                drawPath(wave(surfaceLine), shallowBrush(sea, surface, lipTop.value * d))
            } else {
                spans.forEachIndexed { i, s ->
                    val bottom = if (i == spans.lastIndex) lipTop.value * d else s.bottom
                    drawPath(wave(line(s.top, i)), layerBrush(sea, s.stage, s.top, bottom))
                }
            }
            if (weather == Weather.SUNNY || weather == Weather.FAIR) drawGlints(sea, size.width * 0.78f, surface, d)
            drawAquarium(aquarium, look, t, surface, lipTop.value * d, dark, d)
            drawSeasonSurface(season, dark, t, surfaceLine, d)
            aquarium.toast?.let { drawToast(it, t, surface, lipTop.value * d, sea, measurer, toastStyle, d) }
            drawPath(wave(surfaceLine, closeToBottom = false), sea.glint.copy(alpha = 0.6f), style = Stroke(1.5f * d))
            if (buoy != null) drawBuoy(sea, size.width - 58f * d, surface, d)
            if (ripple.value < 1f) drawRipple(sea, tap, surface, ripple.value, d)
            drawDrawerLip(colors.background, colors.outlineVariant, lipTop.value * d, d)
        }

        Column(
            Modifier
                .graphicsLayer { alpha = (1f - scrollPx() / TEXT_FADE_DP.dp.toPx()).coerceIn(0f, 1f) }
                .padding(top = statusBar + 8.dp, start = 24.dp, end = 24.dp),
        ) {
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(dateText, style = MaterialTheme.typography.bodyMedium, color = sea.skyInk.copy(alpha = 0.8f))
                Text(
                    "身体天气 ${weather?.let(::weatherLabel) ?: "积累中"} ›",
                    style = MaterialTheme.typography.bodyMedium,
                    color = sea.skyInk.copy(alpha = 0.8f),
                    modifier = Modifier.heightIn(min = 48.dp).clickable(onClick = onOpenForecast).padding(vertical = 12.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                minutes?.let(::formatHm) ?: "—",
                style = TideDisplay,
                color = sea.skyInk,
                modifier = Modifier.offset(x = (-6).dp),
            )
            Text(
                if (night != null) "${sleepCaption(night.date, today)} ›" else "还没有昨夜的睡眠",
                style = MaterialTheme.typography.bodyMedium,
                color = sea.skyInk,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .then(if (night != null) Modifier.clickable { onOpenNight(night.date) } else Modifier)
                    .padding(vertical = 12.dp),
            )
        }

        val labelSpans = layerSpans(shape?.layers.orEmpty(), 0f, 1f)
        shape?.layers?.forEachIndexed { i, layer ->
            val span = labelSpans[i]
            if (layer.stage == SleepStage.AWAKE) return@forEachIndexed
            Row(
                Modifier.offset {
                    val depthDp = lipTop.value - surfaceDp()
                    val top = surfaceDp() + depthDp * span.top
                    val height = depthDp * (span.bottom - span.top)
                    if (height < LABEL_MIN_LAYER.value) return@offset IntOffset(-100_000, 0)
                    // 浅睡层上缘是浅色水，字放在中段以下才读得清；最底的深睡层上移，离开抽屉的圆角
                    val anchor = when (layer.stage) {
                        SleepStage.LIGHT -> 0.55f
                        SleepStage.DEEP -> 0.4f
                        else -> 0.5f
                    }
                    IntOffset(24.dp.roundToPx(), ((top + height * anchor).dp - 9.dp).roundToPx())
                },
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(stageLabel(layer.stage), fontSize = 13.sp, color = sea.onWater)
                Spacer(Modifier.width(10.dp))
                Text(formatHm(layer.minutes), fontFamily = DmSans, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = sea.onWater)
            }
        }

        if (buoy != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(end = 24.dp)
                    .offset { IntOffset(0, (surfaceDp().dp - 56.dp).roundToPx()) },
                horizontalAlignment = Alignment.End,
            ) {
                Text(buoy.title, style = MaterialTheme.typography.labelLarge, color = colors.tertiary, textAlign = TextAlign.End)
                Text(buoy.detail, style = MaterialTheme.typography.bodySmall, color = sea.skyInk.copy(alpha = 0.8f), textAlign = TextAlign.End)
            }
        }
    }
}

/** 一层水在 [top]..[bottom]（同一单位）之间。 */
private class Span(val stage: SleepStage, val top: Float, val bottom: Float)

/** 把各层按占比铺进 [surface] 起 [depth] 深的水体。 */
private fun layerSpans(layers: List<TideLayer>, surface: Float, depth: Float): List<Span> {
    var cumulative = 0f
    return layers.map { layer ->
        val top = surface + depth * cumulative
        cumulative += layer.fraction
        Span(layer.stage, top, surface + depth * cumulative)
    }
}

private fun shallowBrush(tide: TideColors, top: Float, bottom: Float) = Brush.verticalGradient(
    0f to tide.shallow, 0.42f to tide.light, 1f to tide.lightDeep, startY = top, endY = bottom,
)

private fun layerBrush(tide: TideColors, stage: SleepStage, top: Float, bottom: Float): Brush = when (stage) {
    SleepStage.AWAKE -> SolidColor(tide.awake)
    SleepStage.LIGHT -> shallowBrush(tide, top, bottom)
    SleepStage.REM -> SolidColor(tide.rem)
    SleepStage.DEEP -> SolidColor(tide.deep)
}

/** 水面（或层的上沿）沿 [line] 起伏的一条浅浪，[closeToBottom] 时封口到画布底，用来填色。 */
private fun DrawScope.wave(line: WaterLine, closeToBottom: Boolean = true): Path {
    val step = 6f * line.density
    val path = Path()
    var x = -step
    path.moveTo(x, line.y(x))
    while (x < size.width + step) {
        x += step
        path.lineTo(x, line.y(x))
    }
    if (closeToBottom) {
        path.lineTo(size.width + step, size.height)
        path.lineTo(-step, size.height)
        path.close()
    }
    return path
}

private fun DrawScope.drawSky(tide: TideColors, weather: Weather?, surface: Float, d: Float, statusBar: Float) {
    val overcast = when (weather) {
        Weather.CLOUDY -> 0.45f
        Weather.OVERCAST -> 0.85f
        else -> 0f
    }
    drawRect(
        Brush.verticalGradient(listOf(lerp(tide.skyTop, tide.overcast, overcast), lerp(tide.skyBottom, tide.overcast, overcast * 0.6f)), startY = 0f, endY = surface),
    )
    val sunX = size.width * 0.78f
    val sunY = (statusBar + 64f) * d
    val sb = statusBar
    when (weather) {
        Weather.SUNNY -> drawSun(tide, sunX, sunY, 170f * d, 1f)
        Weather.FAIR -> {
            drawSun(tide, sunX, sunY, 140f * d, 1f)
            val cx = size.width * 0.74f
            softCloud(tide.cloud, cx - 26f * d, (sb + 64f) * d, 44f * d, 15f * d, 0.85f)
            softCloud(tide.cloud, cx, (sb + 52f) * d, 34f * d, 18f * d, 0.85f)
            softCloud(tide.cloud, cx + 26f * d, (sb + 66f) * d, 40f * d, 13f * d, 0.85f)
        }
        Weather.CLOUDY -> {
            drawSun(tide, sunX, sunY, 120f * d, 0.35f)
            softCloud(tide.cloud, size.width * 0.25f, (sb + 26f) * d, 130f * d, 30f * d, 0.55f)
            softCloud(tide.cloud, size.width * 0.78f, (sb + 56f) * d, 140f * d, 34f * d, 0.55f)
            softCloud(tide.cloud, size.width * 0.50f, (sb + 12f) * d, 120f * d, 26f * d, 0.45f)
            softCloud(tide.cloud, size.width * 0.90f, (sb + 110f) * d, 110f * d, 26f * d, 0.40f)
        }
        Weather.OVERCAST -> {
            softCloud(tide.cloud, size.width * 0.20f, (sb + 20f) * d, 190f * d, 40f * d, 0.50f)
            softCloud(tide.cloud, size.width * 0.70f, (sb + 34f) * d, 210f * d, 46f * d, 0.50f)
            softCloud(tide.cloud, size.width * 0.45f, (sb + 86f) * d, 240f * d, 50f * d, 0.40f)
            softCloud(tide.cloud, size.width * 0.90f, (sb + 120f) * d, 150f * d, 36f * d, 0.40f)
            softCloud(tide.cloud, size.width * 0.15f, (sb + 130f) * d, 150f * d, 36f * d, 0.35f)
        }
        null -> Unit
    }
}

private fun DrawScope.drawSun(tide: TideColors, x: Float, y: Float, radius: Float, strength: Float) {
    drawCircle(
        Brush.radialGradient(listOf(tide.sun.copy(alpha = tide.sun.alpha * strength), Color.Transparent), center = Offset(x, y), radius = radius),
        radius = radius,
        center = Offset(x, y),
    )
}

/** 柔边的云：一团径向渐变压成椭圆（[rx]×[ry]），边缘渐隐。 */
private fun DrawScope.softCloud(color: Color, cx: Float, cy: Float, rx: Float, ry: Float, alpha: Float) {
    val center = Offset(cx, cy)
    val a = color.alpha * alpha
    withTransform({ scale(1f, ry / rx, pivot = center) }) {
        drawCircle(
            Brush.radialGradient(0f to color.copy(alpha = a), 0.55f to color.copy(alpha = a * 0.55f), 1f to Color.Transparent, center = center, radius = rx),
            radius = rx,
            center = center,
        )
    }
}

/** 日光落在水面上的几道反光。 */
private fun DrawScope.drawGlints(tide: TideColors, x: Float, surface: Float, d: Float) {
    listOf(floatArrayOf(-18f, 12f, 44f), floatArrayOf(0f, 20f, 30f), floatArrayOf(-30f, 27f, 22f), floatArrayOf(14f, 33f, 36f), floatArrayOf(-8f, 42f, 18f))
        .forEach { (dx, dy, w) ->
            drawRoundRect(
                tide.glint.copy(alpha = 0.38f),
                topLeft = Offset(x + dx * d, surface + dy * d),
                size = Size(w * d, 1.6f * d),
                cornerRadius = CornerRadius(0.8f * d),
            )
        }
}

private fun DrawScope.drawBuoy(tide: TideColors, x: Float, surface: Float, d: Float) {
    drawOval(tide.coral.copy(alpha = 0.32f), Offset(x - 34f * d, surface - 6f * d), Size(68f * d, 12f * d), style = Stroke(1.4f * d))
    drawOval(tide.coral.copy(alpha = 0.7f), Offset(x - 19f * d, surface - 3.6f * d), Size(38f * d, 7.2f * d), style = Stroke(1.4f * d))
    drawCircle(tide.coral, radius = 7f * d, center = Offset(x, surface - 6f * d))
}

/** 点击处向外扩散的两圈涟漪；点在水面以上时落在水面上。 */
private fun DrawScope.drawRipple(tide: TideColors, tap: Offset, surface: Float, progress: Float, d: Float) {
    val cy = max(tap.y, surface + 6f * d)
    repeat(2) { k ->
        val p = (progress - k * 0.16f).coerceIn(0f, 1f)
        if (p <= 0f) return@repeat
        val rx = p * 120f * d
        drawOval(
            tide.glint.copy(alpha = (1f - p) * 0.7f),
            topLeft = Offset(tap.x - rx, cy - rx * 0.2f),
            size = Size(rx * 2f, rx * 0.4f),
            style = Stroke(1.5f * d),
        )
    }
}

/** 抽屉的圆角上沿，以及上沿正中的小把手。 */
private fun DrawScope.drawDrawerLip(drawer: Color, handle: Color, lipTop: Float, d: Float) {
    drawRoundRect(drawer, Offset(0f, lipTop), Size(size.width, size.height - lipTop + 2f * LIP.value * d), CornerRadius(LIP.value * d))
    drawRoundRect(handle, Offset(size.width / 2f - 19f * d, lipTop + 10f * d), Size(38f * d, 4f * d), CornerRadius(2f * d))
}
