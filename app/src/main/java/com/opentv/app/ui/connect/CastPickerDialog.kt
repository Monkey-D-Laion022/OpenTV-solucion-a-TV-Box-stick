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
import androidx.compose.runtime.remember
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
import com.opentv.app.ui.components.TvButton
import com.opentv.app.ui.components.TvListItem
import com.opentv.app.ui.theme.OpenTvColors

/**
 * Selector propio de TV: lista los Google Cast y los DLNA de la red. Al tocar uno, OpenTV lo
 * elige como salida: con Cast abre la sesión en ese momento. Desde entonces, lo que se reproduzca
 * en OpenTV se envía a ese TV. No pide ningún enlace.
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
    val selected by viewModel.outputTarget.collectAsStateWithLifecycle()

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
                    .fillMaxWidth()
                    .fillMaxHeight()
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
                Text(
                    text = selected?.let { "Salida elegida: ${it.name} (${it.protocol})" }
                        ?: "Tocá un TV para usarlo como salida. Lo que reproduzcas en OpenTV se verá ahí.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected != null) OpenTvColors.Success else OpenTvColors.Muted,
                )

                // La lista se desplaza; el mensaje y los botones quedan fijos y siempre a la vista.
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
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
                                subtitle = subtitleOf(target, isSelected = target.key == selected?.key),
                                onClick = { viewModel.selectTarget(target) },
                            )
                        }
                    }
                    state.dlna.error?.let { TvListItem(title = it, subtitle = null) }
                }

                val status = when {
                    state.busy -> state.message ?: "Conectando…"
                    else -> state.message
                }
                if (status != null) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodyLarge,
                        color = OpenTvColors.Warning,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvButton(
                        text = if (state.dlna.scanning) "Buscando…" else "Buscar de nuevo",
                        onClick = viewModel::scanDlna,
                        horizontalPadding = 20.dp,
                        modifier = Modifier.focusRequester(firstFocus),
                    )
                    TvButton(text = "Dejar de transmitir", onClick = viewModel::clearTarget, horizontalPadding = 20.dp)
                    TvButton(text = "Cerrar", onClick = onDismiss, horizontalPadding = 20.dp)
                }
            }
        }
    }
}

private fun subtitleOf(target: TvTarget, isSelected: Boolean): String {
    val tail = if (isSelected) "salida elegida" else "tocá para conectar"
    return when (target) {
        is TvTarget.CastTarget -> "Google Cast · $tail"
        is TvTarget.DlnaTarget -> {
            val extra = listOf(target.device.manufacturer, target.device.modelName)
                .filter { it.isNotBlank() }
                .joinToString(" ")
            if (extra.isBlank()) "DLNA · $tail" else "DLNA · $extra · $tail"
        }
    }
}

private fun emptyText(onLocalNetwork: Boolean, searching: Boolean): String = when {
    !onLocalNetwork -> "Conectá el teléfono a la misma red Wi‑Fi que el TV y tocá «Buscar de nuevo»."
    searching -> "Buscando TV en tu red…"
    else -> "No apareció ningún TV. Encendelo, verificá que esté en la misma red Wi‑Fi y tocá " +
        "«Buscar de nuevo». Si el TV no es Cast ni DLNA, usá la transmisión del sistema en «Conectar TV»."
}
