package com.opentv.app.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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

/**
 * Marco de las pantallas rediseñadas. Igual que `SafeArea`, pero con un margen menor ([TvLayout.MARGIN_FRACTION]).
 * `SafeArea` no se modifica porque las pantallas de Shizuku y de espejo nativo lo usan.
 */
@Composable
fun TvSafeArea(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val configuration = LocalConfiguration.current
    val horizontal = (configuration.screenWidthDp * TvLayout.MARGIN_FRACTION).dp
    val vertical = (configuration.screenHeightDp * TvLayout.MARGIN_FRACTION).dp
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OpenTvColors.Background)
            .windowInsetsPadding(WindowInsets.displayCutout)
            .padding(horizontal = horizontal, vertical = vertical),
        content = content,
    )
}
