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
import androidx.media3.ui.PlayerView
import com.opentv.app.player.PlayerViewModel
import com.opentv.app.ui.components.SafeArea
import com.opentv.app.ui.components.SearchField
import com.opentv.app.ui.components.TvButton
import com.opentv.app.ui.connect.CastButton
import com.opentv.app.ui.connect.CastPickerDialog
import com.opentv.app.ui.theme.OpenTvColors

/**
 * Reproductor de enlaces directos (mp4, mkv, HLS, DASH…) con Media3. Los controles del propio
 * reproductor responden al D-pad. Con una pantalla externa conectada se puede «Ver en el TV»:
 * el video se dibuja allí a pantalla completa y el celular queda como control remoto.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val externalDisplay by viewModel.externalDisplay.collectAsStateWithLifecycle()
    var link by rememberSaveable { mutableStateOf(viewModel.initialLink) }
    var castPickerOpen by remember { mutableStateOf(false) }
    if (castPickerOpen) CastPickerDialog(onDismiss = { castPickerOpen = false })

    // Al salir de pantalla (otra app, apagar el celular) se pausa; no se sigue gastando batería.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.pause() }

    // Si se desconecta la pantalla externa, el video vuelve solo al celular.
    LaunchedEffect(externalDisplay) {
        if (externalDisplay == null && state.onExternal) viewModel.setOnExternal(false)
    }

    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { playFocus.requestFocus() } }

    val presenting = state.onExternal && state.currentUrl != null
    ExternalPresentation(
        active = presenting,
        display = if (presenting) viewModel.externalDisplayObject() else null,
        player = viewModel::player,
        fit = state.fit,
        onFailed = viewModel::reportError,
    )

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
                    onValueChange = { link = it; viewModel.setLink(it) },
                    placeholder = "Enlace directo (http:// o https://)",
                    modifier = Modifier.weight(1f),
                )
                TvButton(
                    text = "Reproducir",
                    onClick = { viewModel.play(link) },
                    horizontalPadding = 20.dp,
                    modifier = Modifier.focusRequester(playFocus),
                )
                TvButton(text = "Volver", onClick = onBack, horizontalPadding = 20.dp)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TvButton(
                    text = "Ajuste: ${state.fit.label}",
                    onClick = viewModel::cycleFit,
                    horizontalPadding = 20.dp,
                )
                CastButton(onClick = { castPickerOpen = true })
                if (state.currentUrl != null && (externalDisplay != null || state.onExternal)) {
                    TvButton(
                        text = if (state.onExternal) "Volver al celular" else "Ver en la pantalla externa",
                        onClick = { viewModel.setOnExternal(!state.onExternal) },
                        horizontalPadding = 20.dp,
                    )
                }
                if (state.onExternal) {
                    TvButton(
                        text = if (state.paused) "Reanudar" else "Pausar",
                        onClick = viewModel::togglePause,
                        horizontalPadding = 20.dp,
                    )
                }
            }

            val error = state.error
            when {
                error != null -> Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OpenTvColors.Warning,
                )
                presenting -> Text(
                    text = "El video se ve en la pantalla externa. Ajuste «${state.fit.label}»: " +
                        state.fit.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OpenTvColors.Muted,
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

            if (state.currentUrl != null && !state.onExternal) {
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
