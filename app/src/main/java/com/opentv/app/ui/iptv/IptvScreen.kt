package com.opentv.app.ui.iptv

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.media.projection.MediaProjectionManager
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.opentv.app.iptv.IptvAudio
import com.opentv.app.iptv.IptvState
import com.opentv.app.ui.components.SafeArea
import com.opentv.app.ui.components.TvButton
import com.opentv.app.ui.theme.OpenTvColors

/**
 * Emisión IPTV: el teléfono publica su pantalla como un canal en vivo (HLS) y una lista M3U que se carga en una app
 * IPTV del televisor. Compatibilidad con cada app/TV: sin comprobar; por eso hay una lista de pruebas.
 */
@Composable
fun IptvScreen(onBack: () -> Unit, viewModel: IptvViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val preset by viewModel.preset.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val access by viewModel.access.collectAsStateWithLifecycle()
    fun audioGranted() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) viewModel.start(result.resultCode, data, audioGranted())
    }
    fun launchCapture() {
        val manager = context.getSystemService(MediaProjectionManager::class.java)
        launcher.launch(manager.createScreenCaptureIntent())
    }
    // Si el usuario niega el permiso se emite igual, con pista en silencio (la pantalla lo indica).
    val audioPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { launchCapture() }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    val running = state is IptvState.Live || state is IptvState.Starting

    SafeArea {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Emisión IPTV: la pantalla como canal", style = MaterialTheme.typography.headlineMedium, color = OpenTvColors.OnBackground)
            Text(
                "El teléfono captura su pantalla, la codifica en H.264 y la publica como un canal en vivo (HLS) en tu red Wi‑Fi. " +
                    "Cargás la lista M3U en una app IPTV del televisor y elegís el canal. Es una emisión por HTTP sin cifrar: " +
                    "mientras esté activa, cualquier equipo de tu Wi‑Fi que tenga la dirección puede verla. " +
                    "Todavía NO está comprobado con tu LG 42LB5800 ni con SmartOne IPTV; la lista de pruebas sirve justamente para eso. " +
                    "El sonido del teléfono se captura solo si concedés el permiso de audio (Android lo llama «grabar audio»; OpenTV no usa el micrófono: captura lo que suena en el teléfono). Algunas apps bloquean esa captura y entonces llega silencio: la pantalla lo avisa. " +
                    "No la uses junto con el espejo por navegador (Android permite una sola captura de pantalla a la vez).",
                style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.Muted,
            )
            when (val s = state) {
                IptvState.Idle -> Text("Detenido.", style = MaterialTheme.typography.bodyLarge, color = OpenTvColors.OnBackground)
                IptvState.Starting -> Text("Iniciando…", style = MaterialTheme.typography.bodyLarge, color = OpenTvColors.OnBackground)
                is IptvState.Failed -> Text(s.message, style = MaterialTheme.typography.bodyLarge, color = OpenTvColors.Warning)
                is IptvState.Live -> {
                    Text("Emitiendo · pedido al codificador: ${s.preset.label}", style = MaterialTheme.typography.bodyLarge, color = OpenTvColors.Success)
                    Text("Lista M3U (cargala en la app IPTV):", style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.OnBackground)
                    Text(s.m3uUrl, style = MaterialTheme.typography.titleMedium, color = OpenTvColors.Success)
                    Text("Canal HLS directo:", style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.OnBackground)
                    Text(s.hlsUrl, style = MaterialTheme.typography.titleMedium, color = OpenTvColors.Success)
                    Text(
                        "Retraso teórico de HLS: unos ${s.preset.theoreticalLatencySeconds} s (no medido). " +
                            "Si el televisor no abre el canal HLS, probá la lista de pruebas (incluye TS directo): ${s.compatM3uUrl}",
                        style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.Muted,
                    )
                    Text(
                        "Equipos conectados: ${s.clients} · segmentos publicados: ${s.segments} · ${s.kbPerSecond} KB/s" +
                            if (s.withAudio) " · audio: ${s.audio.label}" else " · sin pista de audio",
                        style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.OnBackground,
                    )
                    if (s.note != null) Text(s.note, style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.Warning)
                    if (s.audio == IptvAudio.CAPTURING_NO_SOUND) {
                        Text(
                            "Se captura audio pero no se detecta sonido: si reproducís algo y sigue así, la app de origen bloquea la captura.",
                            style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.Warning,
                        )
                    }
                    Text(
                        if (s.externalRequests > 0) {
                            "Llegaron ${s.externalRequests} pedidos desde ${s.externalClients} equipo(s) distintos del teléfono. Último: ${s.lastExternal}"
                        } else {
                            "Ningún OTRO equipo llegó todavía: abrir la dirección en este teléfono NO prueba que el televisor pueda verla."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (s.externalRequests > 0) OpenTvColors.Success else OpenTvColors.Warning,
                    )
                    Text(
                        if (s.requests.isEmpty()) {
                            "Todavía no llegó ningún pedido. Si la app IPTV dice que no puede abrir la dirección, revisá que el " +
                                "teléfono y el televisor estén en la misma red y que el router no aísle los equipos."
                        } else {
                            "Últimos pedidos recibidos:\n" + s.requests.takeLast(6).joinToString("\n")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (s.requests.isEmpty()) OpenTvColors.Warning else OpenTvColors.Muted,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (running) {
                    TvButton(text = "Detener la emisión", onClick = viewModel::stop, horizontalPadding = 20.dp, modifier = Modifier.focusRequester(firstFocus))
                    val live = state as? IptvState.Live
                    if (live != null) {
                        TvButton(text = "Copiar lista M3U", onClick = { clipboard.setText(AnnotatedString(live.m3uUrl)) }, horizontalPadding = 20.dp)
                        TvButton(text = "Copiar canal HLS", onClick = { clipboard.setText(AnnotatedString(live.hlsUrl)) }, horizontalPadding = 20.dp)
                        TvButton(text = "Copiar lista de pruebas", onClick = { clipboard.setText(AnnotatedString(live.compatM3uUrl)) }, horizontalPadding = 20.dp)
                    }
                } else {
                    TvButton(
                        text = "Iniciar emisión IPTV",
                        onClick = { if (audioGranted()) launchCapture() else audioPermission.launch(Manifest.permission.RECORD_AUDIO) },
                        horizontalPadding = 20.dp,
                        modifier = Modifier.focusRequester(firstFocus),
                    )
                    TvButton(text = "Calidad: ${preset.shortLabel}", onClick = viewModel::cyclePreset, horizontalPadding = 20.dp)
                    TvButton(text = "Dirección: ${access.label}", onClick = viewModel::cycleAccess, horizontalPadding = 20.dp)
                }
                TvButton(text = "Volver", onClick = onBack, horizontalPadding = 20.dp)
            }
        }
    }
}
