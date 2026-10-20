package dev.hair.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// 配色参照 iOS 健康 app：分组灰底 + 白卡，类别色只用在标题和图形上。
// primary = 活动/步数（橙），secondary = 睡眠（青），tertiary = 心脏（红），surfaceVariant = 环的底轨
private val LightColors = lightColorScheme(
    primary = Color(0xFFD96F00),
    onPrimary = Color.White,
    secondary = Color(0xFF138DA3),
    onSecondary = Color.White,
    tertiary = Color(0xFFE0234D),
    background = Color(0xFFF2F2F7),
    onBackground = Color(0xFF1C1C1E),
    surface = Color.White,
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFE5E5EA),
    onSurfaceVariant = Color(0xFF6E6E73),
    outlineVariant = Color(0xFFD1D1D6),
    error = Color(0xFFFF3B30),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF9F0A),
    onPrimary = Color.Black,
    secondary = Color(0xFF40C8E0),
    onSecondary = Color.Black,
    tertiary = Color(0xFFFF375F),
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF1C1C1E),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF38383A),
    onSurfaceVariant = Color(0xFF8E8E93),
    outlineVariant = Color(0xFF38383A),
    error = Color(0xFFFF453A),
)

// 数字用等宽字形，刷新时不会左右跳动
private val HairTypography = Typography(
    headlineLarge = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun HairTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = HairTypography,
        content = content,
    )
}

/** 可点击文字的强调色（iOS 系统蓝）。 */
@Composable
fun linkColor(): Color = if (isSystemInDarkTheme()) Color(0xFF0A84FF) else Color(0xFF007AFF)
