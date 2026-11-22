package com.opentv.homenova.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val NovaInk = Color(0xFF0B0C0E)
val NovaSurface = Color(0xFF14161A)
val NovaSurfaceElevated = Color(0xFF1B1E24)
val NovaOutline = Color(0xFF2B3038)
val NovaTextPrimary = Color(0xFFF4F6F9)
val NovaTextSecondary = Color(0xFF9BA3B0)
val NovaTextMuted = Color(0xFF6C7480)
val NovaDanger = Color(0xFFFF6B6B)
val NovaSuccess = Color(0xFF6EE7A8)

/** Selectable accent palettes, persisted by key. */
enum class AccentStyle(
    val key: String,
    val label: String,
    val primary: Color,
    val secondary: Color,
) {
    AURORA("aurora", "Aurora", Color(0xFF6EE7F9), Color(0xFFA78BFA)),
    SUNSET("sunset", "Atardecer", Color(0xFFFFA36C), Color(0xFFFF6B9A)),
    MINT("mint", "Menta", Color(0xFF7DF0C4), Color(0xFF6EC7FF)),
    AMBER("amber", "Ámbar", Color(0xFFFFD166), Color(0xFFFF8C61)),
}

fun accentStyleFor(key: String): AccentStyle =
    AccentStyle.entries.firstOrNull { it.key == key } ?: AccentStyle.AURORA

data class NovaPalette(
    val accent: Color,
    val accentSecondary: Color,
    val ink: Color = NovaInk,
    val surface: Color = NovaSurface,
    val surfaceElevated: Color = NovaSurfaceElevated,
    val outline: Color = NovaOutline,
    val textPrimary: Color = NovaTextPrimary,
    val textSecondary: Color = NovaTextSecondary,
    val textMuted: Color = NovaTextMuted,
    val danger: Color = NovaDanger,
    val success: Color = NovaSuccess,
)

val LocalNovaPalette = staticCompositionLocalOf {
    NovaPalette(AccentStyle.AURORA.primary, AccentStyle.AURORA.secondary)
}

/** Whether focus moves play a short system tick. Toggled from Settings. */
val LocalDpadSounds = androidx.compose.runtime.compositionLocalOf { true }

private val NovaTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 46.sp,
        lineHeight = 50.sp,
        letterSpacing = (-0.8).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.4).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 27.sp,
        lineHeight = 33.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 25.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
)

private val NovaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun NovaTheme(
    accent: AccentStyle = AccentStyle.AURORA,
    content: @Composable () -> Unit,
) {
    val palette = NovaPalette(
        accent = accent.primary,
        accentSecondary = accent.secondary,
    )
    val colorScheme = darkColorScheme(
        primary = palette.accent,
        onPrimary = Color(0xFF041014),
        secondary = palette.accentSecondary,
        onSecondary = Color(0xFF0B0C0E),
        background = palette.ink,
        onBackground = palette.textPrimary,
        surface = palette.surface,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.surfaceElevated,
        onSurfaceVariant = palette.textSecondary,
        outline = palette.outline,
        error = palette.danger,
    )

    CompositionLocalProvider(LocalNovaPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = NovaTypography,
            shapes = NovaShapes,
            content = content,
        )
    }
}
