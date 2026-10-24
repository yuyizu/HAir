package dev.hair.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.hair.app.R
import dev.hair.app.data.Season

// 「潮汐」：睡眠是水位，其余指标沉在抽屉里。深海 → 浅海 → 天光，珊瑚只给偏离。
// primary = 活动/步数（日光的赭金），secondary = 睡眠（浅海），tertiary = 偏离与心脏（珊瑚，文字用的深一档），
// surfaceVariant = 轨道与分隔线。background 同时是抽屉的颜色。
private val LightColors = lightColorScheme(
    primary = Color(0xFF8F5C0E),
    onPrimary = Color.White,
    secondary = Color(0xFF1B6F80),
    onSecondary = Color.White,
    tertiary = Color(0xFFBE402A),
    background = Color(0xFFF4F9F8),
    onBackground = Color(0xFF0E2A36),
    surface = Color.White,
    onSurface = Color(0xFF0E2A36),
    surfaceVariant = Color(0xFFDAE8E5),
    onSurfaceVariant = Color(0xFF58747C),
    outlineVariant = Color(0xFFDAE8E5),
    error = Color(0xFFBE402A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE0A84A),
    onPrimary = Color(0xFF1A1200),
    secondary = Color(0xFF5FC3D4),
    onSecondary = Color(0xFF00222A),
    tertiary = Color(0xFFFF8F72),
    background = Color(0xFF0C1B21),
    onBackground = Color(0xFFE6F2F0),
    surface = Color(0xFF132830),
    onSurface = Color(0xFFE6F2F0),
    surfaceVariant = Color(0xFF1F3A44),
    onSurfaceVariant = Color(0xFF8FB0B6),
    outlineVariant = Color(0xFF1F3A44),
    error = Color(0xFFFF8F72),
)

/** 水景专用色：天空、各层水体、水面以上的文字。深色模式下天空换成夜空。 */
@Immutable
data class TideColors(
    val skyTop: Color,
    val skyBottom: Color,
    /** 阴天时天空向它靠拢。 */
    val overcast: Color,
    /** 带透明度：日光在天空里的亮度。深色模式下是月光。 */
    val sun: Color,
    val cloud: Color,
    val shallow: Color,
    val light: Color,
    val lightDeep: Color,
    val rem: Color,
    val deep: Color,
    val awake: Color,
    val glint: Color,
    /** 水面以上的大数字和文字。 */
    val skyInk: Color,
    /** 落在水里的文字。 */
    val onWater: Color,
    /** 浮标和涟漪，只表示偏离。 */
    val coral: Color,
    /**
     * 睡眠分期在页面底色上的图表色，四季一致。越深的睡眠越醒目（浅色模式更深，深色模式更亮）；
     * 清醒是一圈空心环，所以只有环的描边色。每个色对页面底色的对比度 ≥ 3:1。
     */
    val stageAwake: Color,
    val stageLight: Color,
    val stageRem: Color,
    val stageDeep: Color,
)

private val LightTide = TideColors(
    skyTop = Color(0xFFCBE8E4),
    skyBottom = Color(0xFFEDF7F4),
    overcast = Color(0xFFAEBFC4),
    sun = Color(0xE6FFF3C2),
    cloud = Color.White,
    shallow = Color(0xFF7CC6C8),
    light = Color(0xFF1F7A8C),
    lightDeep = Color(0xFF1B7185),
    rem = Color(0xFF16596D),
    deep = Color(0xFF0E3B4F),
    awake = Color(0xFFBFE3E0),
    glint = Color.White,
    skyInk = Color(0xFF0E3B4F),
    onWater = Color(0xFFEAF7F5),
    coral = Color(0xFFFF7A59),
    stageAwake = Color(0xFF58747C),
    stageLight = Color(0xFF4396A7),
    stageRem = Color(0xFF1F7A8C),
    stageDeep = Color(0xFF0E3B4F),
)

private val DarkTide = TideColors(
    skyTop = Color(0xFF071720),
    skyBottom = Color(0xFF14404E),
    overcast = Color(0xFF2A3F48),
    sun = Color(0x80DCEBF0),
    cloud = Color(0xFF3B5A66),
    shallow = Color(0xFF2C8795),
    light = Color(0xFF17606F),
    lightDeep = Color(0xFF125263),
    rem = Color(0xFF0E4152),
    deep = Color(0xFF082A38),
    awake = Color(0xFF5FB3B8),
    glint = Color(0xFFCFE8EC),
    skyInk = Color(0xFFE6F2F0),
    onWater = Color(0xFFEAF7F5),
    coral = Color(0xFFFF8F72),
    stageAwake = Color(0xFF8FB0B6),
    stageLight = Color(0xFF33909F),
    stageRem = Color(0xFF4FB6C8),
    stageDeep = Color(0xFF9BE3EE),
)

// 季节只换天空和浅层水体的色；睡眠阶段的深浅层（rem、deep）在四季保持一致，上面的文字在每一季都读得清。
private val LightSeasons = mapOf(
    Season.SPRING to LightTide.copy(
        skyTop = Color(0xFFD4EBDD), skyBottom = Color(0xFFF1F8EC), sun = Color(0xE6FFF1B8),
        shallow = Color(0xFF86CFBC), light = Color(0xFF1D7C76), lightDeep = Color(0xFF1B7678),
    ),
    Season.SUMMER to LightTide.copy(
        skyTop = Color(0xFFBCE0F0), skyBottom = Color(0xFFE6F5F7), sun = Color(0xF2FFEDA6),
        shallow = Color(0xFF5FC3D0), light = Color(0xFF177A92), lightDeep = Color(0xFF12708A),
    ),
    Season.AUTUMN to LightTide.copy(
        skyTop = Color(0xFFE8DDC6), skyBottom = Color(0xFFF7EFDF), sun = Color(0xE6FFC978), cloud = Color(0xFFFFF6E6),
        shallow = Color(0xFF8FC2B3), light = Color(0xFF2D7A7E), lightDeep = Color(0xFF266E76), rem = Color(0xFF1A5A68),
    ),
    Season.WINTER to LightTide.copy(
        skyTop = Color(0xFFC3D2DE), skyBottom = Color(0xFFE7EEF3), sun = Color(0xB0F4F7FA),
        shallow = Color(0xFF92BBD0), light = Color(0xFF2B6F8E), lightDeep = Color(0xFF245F80), rem = Color(0xFF184D68), deep = Color(0xFF0E3A52),
    ),
)

private val DarkSeasons = mapOf(
    Season.SPRING to DarkTide.copy(skyTop = Color(0xFF0A1D1D), skyBottom = Color(0xFF1A4A48), shallow = Color(0xFF2F8F86), light = Color(0xFF17675F), lightDeep = Color(0xFF125A58)),
    Season.SUMMER to DarkTide.copy(skyTop = Color(0xFF081A2E), skyBottom = Color(0xFF1A4766), shallow = Color(0xFF2A8CA3), light = Color(0xFF17627C), lightDeep = Color(0xFF125470)),
    Season.AUTUMN to DarkTide.copy(skyTop = Color(0xFF1A1810), skyBottom = Color(0xFF4A3F2C), shallow = Color(0xFF2F8483), light = Color(0xFF1A5F66), lightDeep = Color(0xFF155159)),
    Season.WINTER to DarkTide.copy(skyTop = Color(0xFF0A1620), skyBottom = Color(0xFF2A4458), shallow = Color(0xFF3A7F9B), light = Color(0xFF1B5A78), lightDeep = Color(0xFF154C68), rem = Color(0xFF0E3F58)),
)

/** [season] 季的水景色；本身必须是 [LightTide]/[DarkTide] 之一（HairTheme 提供的）。 */
fun TideColors.inSeason(season: Season): TideColors = (if (this === DarkTide) DarkSeasons else LightSeasons).getValue(season)

private val LocalTide = staticCompositionLocalOf { LightTide }

val MaterialTheme.tide: TideColors
    @Composable @ReadOnlyComposable get() = LocalTide.current

@OptIn(ExperimentalTextApi::class)
private fun dmSans(weight: Int) = Font(
    R.font.dm_sans,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** 数字和拉丁字母用 DM Sans（可变字体，带等宽数字）；汉字自动回退到系统字体。 */
val DmSans = FontFamily(dmSans(200), dmSans(300), dmSans(400), dmSans(500), dmSans(600), dmSans(700))

/** 水面上方的大数字：极细、字距收紧，刷新时数字等宽不跳动。 */
val TideDisplay = TextStyle(
    fontFamily = DmSans,
    fontWeight = FontWeight(200),
    fontSize = 112.sp,
    lineHeight = 112.sp,
    letterSpacing = (-0.045).em,
    fontFeatureSettings = "tnum",
)

/** 页面标题：写在天空页眉上，比水面大数字轻一档。 */
val TideTitle = TextStyle(
    fontFamily = DmSans,
    fontWeight = FontWeight(300),
    fontSize = 34.sp,
    lineHeight = 40.sp,
    letterSpacing = (-0.02).em,
)

/** 列表和详情里的大数字：和水面上的大数字同一族，只是小一档。 */
val TideNumeral = TextStyle(
    fontFamily = DmSans,
    fontWeight = FontWeight(200),
    fontSize = 48.sp,
    lineHeight = 52.sp,
    letterSpacing = (-0.03).em,
    fontFeatureSettings = "tnum",
)

private fun Typography.withFamily(f: FontFamily) = copy(
    displayLarge = displayLarge.copy(fontFamily = f),
    displayMedium = displayMedium.copy(fontFamily = f),
    displaySmall = displaySmall.copy(fontFamily = f),
    headlineLarge = headlineLarge.copy(fontFamily = f),
    headlineMedium = headlineMedium.copy(fontFamily = f),
    headlineSmall = headlineSmall.copy(fontFamily = f),
    titleLarge = titleLarge.copy(fontFamily = f),
    titleMedium = titleMedium.copy(fontFamily = f),
    titleSmall = titleSmall.copy(fontFamily = f),
    bodyLarge = bodyLarge.copy(fontFamily = f),
    bodyMedium = bodyMedium.copy(fontFamily = f),
    bodySmall = bodySmall.copy(fontFamily = f),
    labelLarge = labelLarge.copy(fontFamily = f),
    labelMedium = labelMedium.copy(fontFamily = f),
    labelSmall = labelSmall.copy(fontFamily = f),
)

private val HairTypography = Typography(
    headlineLarge = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Light),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Normal, fontFeatureSettings = "tnum"),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
).withFamily(DmSans)

@Composable
fun HairTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTide provides if (darkTheme) DarkTide else LightTide) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = HairTypography,
            content = content,
        )
    }
}

/** 可点击文字的强调色：浅海（睡眠色）。 */
@Composable
fun linkColor(): Color = MaterialTheme.colorScheme.secondary
