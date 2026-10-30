package com.opentv.app.ui.connections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.opentv.app.ui.components.StatusChip
import com.opentv.app.ui.components.TvListItem
import com.opentv.app.ui.home.HomeViewModel
import com.opentv.app.ui.shell.EntryAction
import com.opentv.app.ui.shell.NavEntry
import com.opentv.app.ui.shell.NavCatalog
import com.opentv.app.ui.theme.OpenTvColors

/**
 * Conexiones, separando lo que Android/TV llaman cosas distintas: descubrir un dispositivo, conectarse a él, reproducir
 * contenido en él y duplicar la pantalla. Un dispositivo simplemente detectado NUNCA se muestra como conectado:
 * el estado de arriba solo refleja la pantalla externa y la salida elegida, que son datos reales del sistema.
 * Todos los accesos abren las pantallas que ya existían (mismos destinos).
 */
@Composable
fun ConnectionsScreen(onNavigate: (String) -> Unit, viewModel: HomeViewModel = viewModel()) {
    val context = LocalContext.current
    val externalDisplay by viewModel.externalDisplay.collectAsStateWithLifecycle()
    val outputTarget by viewModel.outputTarget.collectAsStateWithLifecycle()

    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
    }

    fun open(entry: NavEntry) {
        val route = entry.route
        if (route != null) onNavigate(route) else if (entry.action == EntryAction.NATIVE_CAST) viewModel.openNativeCast(context)
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Conexiones", style = MaterialTheme.typography.headlineMedium, color = OpenTvColors.OnBackground)

        val ext = externalDisplay?.name
        StatusChip(
            text = if (ext == null) "Pantalla externa: ninguna" else "Pantalla externa: $ext",
            dotColor = if (ext == null) OpenTvColors.Muted else OpenTvColors.Success,
        )
        val out = outputTarget?.name
        StatusChip(
            text = if (out == null) "Salida elegida: ninguna" else "Salida elegida: $out",
            dotColor = if (out == null) OpenTvColors.Muted else OpenTvColors.Success,
        )
        Text(
            "Detectar un dispositivo no es conectarse, conectarse no es reproducir y reproducir no es duplicar la pantalla. " +
                "Cada grupo de abajo hace una sola de esas cosas.",
            style = MaterialTheme.typography.bodyMedium,
            color = OpenTvColors.Muted,
        )

        Group("Duplicar la pantalla del teléfono (Miracast / Cast)")
        NavCatalog.mirroring.forEachIndexed { i, e ->
            TvListItem(e.title, e.subtitle, onClick = { open(e) }, modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier)
        }

        Group("Emisión IPTV: la pantalla como canal en vivo")
        NavCatalog.iptv.forEach { e -> TvListItem(e.title, e.subtitle, onClick = { open(e) }) }

        Group("Transmisión OpenTV (protocolo propio)")
        NavCatalog.opentvLink.forEach { e -> TvListItem(e.title, e.subtitle, onClick = { open(e) }) }

        Group("Dispositivos encontrados y envío de contenido (DLNA)")
        NavCatalog.playback.forEach { e -> TvListItem(e.title, e.subtitle, onClick = { open(e) }) }

        Group("Diagnóstico de conexiones (solo prueban, no transmiten)")
        NavCatalog.connectionDiagnostics.forEach { e -> TvListItem(e.title, e.subtitle, onClick = { open(e) }) }
        Text(
            "El diagnóstico de la etapa B lee lo que el televisor anuncia por Wi-Fi Display. Que funcione o no no demuestra " +
                "nada sobre los 60 FPS; esa transmisión todavía no existe en OpenTV.",
            style = MaterialTheme.typography.bodyMedium,
            color = OpenTvColors.Muted,
        )
    }
}

@Composable
private fun Group(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, color = OpenTvColors.Primary, modifier = Modifier.padding(top = 6.dp))
}
