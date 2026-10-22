package com.opentv.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.opentv.app.ui.theme.OpenTvColors

/** Margen de seguridad (overscan) como fracción de cada lado; algunos TV recortan los bordes. */
private const val OVERSCAN_FRACTION = 0.05f

/**
 * Contenedor de cada pantalla. El fondo ocupa TODA la pantalla (sin franjas negras ni márgenes
 * artificiales); solo el contenido respeta el margen de seguridad y la muesca de la cámara.
 */
@Composable
fun SafeArea(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val configuration = LocalConfiguration.current
    val horizontal = (configuration.screenWidthDp * OVERSCAN_FRACTION).dp
    val vertical = (configuration.screenHeightDp * OVERSCAN_FRACTION).dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OpenTvColors.Background)
            .windowInsetsPadding(WindowInsets.displayCutout)
            .padding(horizontal = horizontal, vertical = vertical),
        content = content,
    )
}
