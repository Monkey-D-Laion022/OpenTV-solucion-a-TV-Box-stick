package com.opentv.app.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration

/** Medidas para la configuración actual (ancho, alto y escala de letra reales). */
@Composable
fun rememberTvMetrics(): TvMetrics {
    val c = LocalConfiguration.current
    return remember(c.screenWidthDp, c.screenHeightDp, c.fontScale) {
        TvLayout.metrics(c.screenWidthDp, c.screenHeightDp, c.fontScale)
    }
}
