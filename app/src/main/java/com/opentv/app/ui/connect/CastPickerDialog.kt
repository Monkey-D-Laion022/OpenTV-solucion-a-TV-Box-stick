package com.opentv.app.ui.connect

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.opentv.app.connect.ConnectViewModel
import com.opentv.app.connect.TvTarget
import com.opentv.app.connect.TvTargets
import com.opentv.app.ui.components.SearchField
import com.opentv.app.ui.components.TvButton
import com.opentv.app.ui.components.TvListItem
import com.opentv.app.ui.theme.OpenTvColors

/**
 * Selector propio de TV: lista los Google Cast y los DLNA que aparecen en la red y, al elegir uno,
 * le envía el enlace directamente desde OpenTV, sin pasar por el menú «Emitir» de Android.
 *
 * @param manageDiscovery si es `true` la búsqueda se inicia al abrir y se corta al cerrar (úsese
 * cuando la pantalla que lo muestra no administra ya esa búsqueda).
 */
@Composable
fun CastPickerDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConnectViewModel = viewModel(),
    manageDiscovery: Boolean = true,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var link by rememberSaveable { mutableStateOf(viewModel.initialLink) }

    if (manageDiscovery) {
        DisposableEffect(Unit) {
            viewModel.onScreenStarted()
            viewModel.scanDlna()
            onDispose { viewModel.onScreenStopped() }
        }
    } else {
        LaunchedEffect(Unit) { viewModel.scanDlna() }
    }

    val targets = TvTargets.build(state.castDevices, state.dlna.devices)
    val firstFocus = remember { FocusRequester() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // El foco inicial se pide dentro del Dialog: es otra ventana y fuera de ella no existe.
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { firstFocus.requestFocus() }
        }
        Box(
            modifier = modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.94f)
                .background(OpenTvColors.Background, RoundedCornerShape(24.dp)),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CastIcon(size = 30.dp)
                    Text(
                        text = "Transmitir a un TV",
                        style = MaterialTheme.typography.headlineSmall,
                        color = OpenTvColors.OnBackground,
                    )
                }
                SearchField(
                    value = link,
                    onValueChange = { link = it; viewModel.setLink(it) },
                    placeholder = "Enlace directo al video (http:// o https://)",
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Elegí un TV: el enlace se le envía y el TV lo reproduce por su cuenta, en su " +
                        "propia resolución. DLNA admite solo enlaces de tu red local.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OpenTvColors.Muted,
                )

                state.message?.let { TvListItem(title = it, subtitle = null) }
                state.dlna.error?.let { TvListItem(title = it, subtitle = null) }

                if (state.busy) {
                    Text("Enviando…", style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.Muted)
                }

                if (targets.isEmpty()) {
                    Text(
                        text = emptyText(
                            onLocalNetwork = state.onLocalNetwork,
                            searching = state.dlna.scanning || !state.dlna.scanned,
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = OpenTvColors.Muted,
                    )
                } else {
                    Text(
                        text = if (state.dlna.scanning) "TV detectados (sigue buscando…)" else "TV detectados",
                        style = MaterialTheme.typography.titleMedium,
                        color = OpenTvColors.OnBackground,
                    )
                    targets.forEach { target ->
                        TvListItem(
                            title = target.name,
                            subtitle = subtitleOf(target),
                            onClick = { viewModel.send(target, link) },
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvButton(
                        text = if (state.dlna.scanning) "Buscando…" else "Buscar de nuevo",
                        onClick = viewModel::scanDlna,
                        horizontalPadding = 20.dp,
                        modifier = Modifier.focusRequester(firstFocus),
                    )
                    TvButton(text = "Detener / desconectar", onClick = viewModel::stopAll, horizontalPadding = 20.dp)
                    TvButton(text = "Cerrar", onClick = onDismiss, horizontalPadding = 20.dp)
                }
            }
        }
    }
}

private fun subtitleOf(target: TvTarget): String = when (target) {
    is TvTarget.CastTarget -> "Google Cast · tocá para enviar"
    is TvTarget.DlnaTarget -> {
        val extra = listOf(target.device.manufacturer, target.device.modelName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        if (extra.isBlank()) "DLNA · tocá para enviar" else "DLNA · $extra · tocá para enviar"
    }
}

private fun emptyText(onLocalNetwork: Boolean, searching: Boolean): String = when {
    !onLocalNetwork -> "Conectá el teléfono a la misma red Wi‑Fi que el TV y tocá «Buscar de nuevo»."
    searching -> "Buscando TV en tu red…"
    else -> "No apareció ningún TV. Encendelo, verificá que esté en la misma red Wi‑Fi y tocá " +
        "«Buscar de nuevo». Si el TV no es Cast ni DLNA, usá la transmisión del sistema en «Conectar TV»."
}
