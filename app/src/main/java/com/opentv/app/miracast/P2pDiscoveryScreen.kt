package com.opentv.app.miracast

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.opentv.app.ui.components.SafeArea
import com.opentv.app.ui.components.TvButton
import com.opentv.app.ui.theme.OpenTvColors

/**
 * Etapa A de Miracast: descubrimiento Wi-Fi Direct. Solo muestra lo que Android informa. No conecta, no transmite
 * y no cambia ningún ajuste. Nada arranca solo: la búsqueda empieza al pulsar el botón.
 */
@Composable
fun P2pDiscoveryScreen(
    onBack: () -> Unit,
    viewModel: P2pDiscoveryViewModel = viewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.start() else viewModel.permissionDenied()
    }
    val begin: () -> Unit = {
        if (!P2pPolicy.canRun(Build.VERSION.SDK_INT) || viewModel.hasPermission()) {
            viewModel.start()
        } else {
            launcher.launch(P2pPolicy.NEARBY_PERMISSION)
        }
    }
    DisposableEffect(Unit) { onDispose { viewModel.stop() } }

    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    SafeArea {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Descubrimiento Wi-Fi Direct (prueba)", style = MaterialTheme.typography.headlineMedium, color = OpenTvColors.OnBackground)
            Text(
                "Busca dispositivos Wi-Fi Direct cercanos (por ejemplo tu televisor LG) y muestra lo que Android permite leer. " +
                    "NO conecta, NO forma grupos, NO transmite y NO cambia la resolución ni ningún ajuste. " +
                    "No comprueba formatos ni 60 FPS: solo si el televisor se ve. Encendé el televisor con Miracast activo antes de buscar.",
                style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.Muted,
            )
            Text(
                "Permiso: dispositivos Wi-Fi cercanos (Android 13 o superior). Android también exige tener activada la ubicación del teléfono " +
                    "para Wi-Fi Direct; OpenTV no usa tu ubicación.",
                style = MaterialTheme.typography.bodySmall, color = OpenTvColors.Muted,
            )

            when (ui.phase) {
                P2pPhase.SEARCHING -> TvButton(text = "Detener búsqueda", onClick = viewModel::stop, horizontalPadding = 20.dp, modifier = Modifier.focusRequester(firstFocus))
                P2pPhase.BLOCKED_OLD_ANDROID, P2pPhase.UNSUPPORTED -> TvButton(text = "Intentar de nuevo", onClick = begin, horizontalPadding = 20.dp, modifier = Modifier.focusRequester(firstFocus))
                else -> TvButton(
                    text = "Buscar dispositivos (${P2pPolicy.DISCOVERY_SECONDS} s)",
                    onClick = begin,
                    horizontalPadding = 20.dp,
                    modifier = Modifier.focusRequester(firstFocus),
                )
            }

            Text("Estado", style = MaterialTheme.typography.titleLarge, color = OpenTvColors.OnBackground)
            Text(P2pText.conclusion(ui), style = MaterialTheme.typography.bodyLarge, color = OpenTvColors.OnBackground)
            Text(
                "Wi-Fi Direct según el sistema: ${ui.p2pEnabled?.let { if (it) "habilitado" else "deshabilitado" } ?: P2pText.NA} · " +
                    "ubicación del teléfono: ${ui.locationOn?.let { if (it) "activada" else "desactivada" } ?: P2pText.NA}",
                style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.Muted,
            )

            if (ui.peers.isNotEmpty()) {
                Text("Dispositivos vistos (${ui.peers.size})", style = MaterialTheme.typography.titleLarge, color = OpenTvColors.OnBackground)
                for (peer in ui.peers) {
                    Text(
                        P2pText.peerLines(peer).joinToString("\n"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (P2pText.possibleLg(peer.name)) OpenTvColors.Success else OpenTvColors.OnBackground,
                    )
                }
            }

            if (ui.log.isNotEmpty()) {
                Text("Registro de la prueba (solo en pantalla, no se guarda)", style = MaterialTheme.typography.titleLarge, color = OpenTvColors.OnBackground)
                Text(ui.log.joinToString("\n"), style = MaterialTheme.typography.bodySmall, color = OpenTvColors.Muted)
            }

            TvButton(text = "Volver", onClick = onBack, horizontalPadding = 20.dp)
        }
    }
}
