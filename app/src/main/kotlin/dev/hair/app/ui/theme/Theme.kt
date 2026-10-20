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

// primary = 步数环，secondary = 睡眠环，surfaceVariant = 环的底轨
private val LightColors = lightColorScheme(
    primary = Color(0xFFFF5A3C),
    onPrimary = Color.White,
    secondary = Color(0xFF5B6CFF),
    onSecondary = Color.White,
    tertiary = Color(0xFF9B5DE5),
    background = Color(0xFFFAF8F5),
    onBackground = Color(0xFF1F2328),
    surface = Color.White,
    onSurface = Color(0xFF1F2328),
    surfaceVariant = Color(0xFFEFEAE3),
    onSurfaceVariant = Color(0xFF6B7280),
    outlineVariant = Color(0xFFEAE4DC),
    error = Color(0xFFC62828),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF7A5F),
    onPrimary = Color(0xFF1F2328),
    secondary = Color(0xFF8C98FF),
    onSecondary = Color(0xFF1F2328),
    tertiary = Color(0xFFC08CF5),
    background = Color(0xFF121214),
    onBackground = Color(0xFFF2F2F3),
    surface = Color(0xFF1C1C1F),
    onSurface = Color(0xFFF2F2F3),
    surfaceVariant = Color(0xFF2A2A2E),
    onSurfaceVariant = Color(0xFFA0A4AB),
    outlineVariant = Color(0xFF2F2F34),
    error = Color(0xFFFF8A80),
)

// 数字用等宽字形，刷新时不会左右跳动
private val HairTypography = Typography(
    headlineMedium = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun HairTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = HairTypography,
        content = content,
    )
}
