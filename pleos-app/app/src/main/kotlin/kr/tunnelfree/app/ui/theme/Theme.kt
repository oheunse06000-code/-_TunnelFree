package kr.tunnelfree.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

data class TfPalette(
    val Background: Color,
    val Surface: Color,
    val SurfaceHigh: Color,
    val Primary: Color,
    val OnPrimary: Color,
    val Calm: Color,
    val Warning: Color,
    val Danger: Color,
    val TextPrimary: Color,
    val TextSecondary: Color,
    val Tunnel: Color,
    val Road: Color,
    val Route: Color,
    val isDark: Boolean,
)

val DarkPalette = TfPalette(
    Background = Color(0xFF101418),
    Surface = Color(0xFF1B2128),
    SurfaceHigh = Color(0xFF252D36),
    Primary = Color(0xFF4FC3F7),
    OnPrimary = Color(0xFF00242F),
    Calm = Color(0xFF7ED6A5),
    Warning = Color(0xFFFFB74D),
    Danger = Color(0xFFEF5350),
    TextPrimary = Color(0xFFF2F5F7),
    TextSecondary = Color(0xFFA3AEB8),
    Tunnel = Color(0xFFEF5350),
    Road = Color(0xFF3A4652),
    Route = Color(0xFF4FC3F7),
    isDark = true,
)

val LightPalette = TfPalette(
    Background = Color(0xFFF4F6F8),
    Surface = Color(0xFFFFFFFF),
    SurfaceHigh = Color(0xFFE6EAEE),
    Primary = Color(0xFF0277BD),
    OnPrimary = Color(0xFFFFFFFF),
    Calm = Color(0xFF2E7D4F),
    Warning = Color(0xFFB26A00),
    Danger = Color(0xFFC62828),
    TextPrimary = Color(0xFF14191E),
    TextSecondary = Color(0xFF55606B),
    Tunnel = Color(0xFFD32F2F),
    Road = Color(0xFFB0BAC4),
    Route = Color(0xFF0277BD),
    isDark = false,
)

val LocalTfPalette = staticCompositionLocalOf { DarkPalette }

object TfTheme {
    val colors: TfPalette
        @Composable get() = LocalTfPalette.current
}

private fun schemeOf(p: TfPalette) = if (p.isDark) darkColorScheme(
    primary = p.Primary, onPrimary = p.OnPrimary, secondary = p.Calm,
    background = p.Background, surface = p.Surface, surfaceVariant = p.SurfaceHigh,
    onBackground = p.TextPrimary, onSurface = p.TextPrimary, onSurfaceVariant = p.TextSecondary, error = p.Danger,
) else lightColorScheme(
    primary = p.Primary, onPrimary = p.OnPrimary, secondary = p.Calm,
    background = p.Background, surface = p.Surface, surfaceVariant = p.SurfaceHigh,
    onBackground = p.TextPrimary, onSurface = p.TextPrimary, onSurfaceVariant = p.TextSecondary, error = p.Danger,
)

private val typography = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 18.sp),
    bodyMedium = TextStyle(fontSize = 16.sp),
    labelLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun TunnelFreeTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    CompositionLocalProvider(LocalTfPalette provides palette) {
        MaterialTheme(colorScheme = schemeOf(palette), typography = typography, content = content)
    }
}
