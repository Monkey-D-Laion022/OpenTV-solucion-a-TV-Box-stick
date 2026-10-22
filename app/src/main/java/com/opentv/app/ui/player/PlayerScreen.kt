package com.opentv.app.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.opentv.app.player.PlayerViewModel
import com.opentv.app.player.VideoFit
import com.opentv.app.ui.components.SafeArea
import com.opentv.app.ui.components.SearchField
import com.opentv.app.ui.components.TvButton
import com.opentv.app.ui.theme.OpenTvColors

@OptIn(UnstableApi::class)
private fun VideoFit.resizeMode(): Int = when (this) {
    VideoFit.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    VideoFit.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    VideoFit.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
}

/**
 * Reproductor de enlaces directos (mp4, mkv, HLS, DASH…) con Media3. Los controles del propio
 * reproductor responden al D-pad; el botón «Ajuste» cambia cómo se acomoda el video.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var link by rememberSaveable { mutableStateOf("") }

    // Al salir de pantalla (otra app, apagar el celular) se pausa; no se sigue gastando batería.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.pause() }

    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { playFocus.requestFocus() } }

    SafeArea {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchField(
                    value = link,
                    onValueChange = { link = it },
                    placeholder = "Enlace directo (http:// o https://)",
                    modifier = Modifier.weight(1f),
                )
                TvButton(
                    text = "Reproducir",
                    onClick = { viewModel.play(link) },
                    horizontalPadding = 20.dp,
                    modifier = Modifier.focusRequester(playFocus),
                )
                TvButton(
                    text = "Ajuste: ${state.fit.label}",
                    onClick = viewModel::cycleFit,
                    horizontalPadding = 20.dp,
                )
                TvButton(text = "Volver", onClick = onBack, horizontalPadding = 20.dp)
            }

            val error = state.error
            when {
                error != null -> Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OpenTvColors.Warning,
                )
                state.currentUrl == null -> Text(
                    text = "Pegá un enlace directo a un video o transmisión. ${state.fit.description}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OpenTvColors.Muted,
                )
                else -> Text(
                    text = "Ajuste «${state.fit.label}»: ${state.fit.description}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OpenTvColors.Muted,
                )
            }

            if (state.currentUrl != null) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    factory = { context ->
                        PlayerView(context).apply {
                            useController = true
                            keepScreenOn = true
                            player = viewModel.player()
                        }
                    },
                    update = { view -> view.resizeMode = state.fit.resizeMode() },
                    onRelease = { view -> view.player = null },
                )
            }
        }
    }
}
