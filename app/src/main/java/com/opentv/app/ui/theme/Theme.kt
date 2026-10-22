package com.opentv.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val OpenTvColorScheme = darkColorScheme(
    primary = OpenTvColors.Primary,
    onPrimary = OpenTvColors.Background,
    background = OpenTvColors.Background,
    onBackground = OpenTvColors.OnBackground,
    surface = OpenTvColors.Surface,
    onSurface = OpenTvColors.OnBackground,
)

@Composable
fun OpenTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OpenTvColorScheme,
        typography = OpenTvTypography,
        content = content,
    )
}
