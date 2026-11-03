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
    val auto by viewModel.auto.collectAsStateWithLifecycle()
    val calibration by viewModel.calibration.collectAsStateWithLifecycle()
    val measuring by viewModel.measuring.collectAsStateWithLifecycle()
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
    val lastStop by viewModel.lastStop.collectAsStateWithLifecycle()
    val soloTv by viewModel.soloTv.collectAsStateWithLifecycle()
    val localAudio by viewModel.localAudio.collectAsStateWithLifecycle()
    val pump by viewModel.pump.collectAsStateWithLifecycle()
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
                IptvState.Idle -> Text(
                    "Detenido." + (lastStop?.let { "\nÚltima parada: $it" } ?: ""),
                    style = MaterialTheme.typography.bodyLarge, color = OpenTvColors.OnBackground,
                )
                IptvState.Starting -> Text("Iniciando…", style = MaterialTheme.typography.bodyLarge, color = OpenTvColors.OnBackground)
                is IptvState.Failed -> Text(s.message, style = MaterialTheme.typography.bodyLarge, color = OpenTvColors.Warning)
                is IptvState.Live -> {
                    Text("Emitiendo · pedido al codificador: ${s.preset.label}", style = MaterialTheme.typography.bodyLarge, color = OpenTvColors.Success)
                    if (s.pageUrl != null) {
                        Text("Navegador del televisor (lo más simple): escribí esta dirección.", style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.OnBackground)
                        Text(s.pageUrl, style = MaterialTheme.typography.titleMedium, color = OpenTvColors.Success)
                    }
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
                        "Codificador: ${s.codec.ifBlank { "no determinado" }} · MP4 progresivo (prueba): ${s.mp4Url}",
                        style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.Muted,
                    )
                    Text(
                        "Equipos conectados: ${s.clients} · segmentos publicados: ${s.segments}",
                        style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.OnBackground,
                    )
                    Text(
                        (if (s.healthy) "Estado verificado: " else "ATENCIÓN: ") + s.health,
                        style = MaterialTheme.typography.bodyLarge, color = if (s.healthy) OpenTvColors.Success else OpenTvColors.Warning,
                    )
                    val captured = s.capturedFps?.let { "%.0f".format(it) } ?: "no medible (captura directa)"
                    val rendered = s.renderedFps?.let { "%.0f".format(it) } ?: "no aplica (captura directa)"
                    Text(
                        "Solicitado: ${s.preset.width}×${s.preset.height} a ${s.preset.fps} fps, ${s.preset.bitrateBps / 1000} kbit/s" +
                            (if (s.profileApplied) "." else " (el codificador no aceptó el perfil pedido y eligió el suyo).") + "\n" +
                            "Resolución capturada y codificada: ${s.preset.width}×${s.preset.height} (mismo tamaño en la pantalla virtual y el codificador).\n" +
                            "FPS capturados (cambios reales en pantalla): $captured · entregados al codificador a ritmo fijo: $rendered.\n" +
                            "FPS codificados (medidos): ${"%.0f".format(s.encodedFps)} · último cuadro hace ${s.msSinceFrame} ms.\n" +
                            "Bitrate producido: ${s.producedKbps} kbit/s · entregado a los equipos: ${s.deliveredKbps} kbit/s.\n" +
                            "FPS recibidos y refresco del televisor: no disponibles (el televisor no los informa; son cosas distintas: cuadros por segundo no es lo mismo que Hz).\n" +
                            if (s.withAudio) "Audio: ${s.audio.label}" else "Sin pista de audio",
                        style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.OnBackground,
                    )
                    if (s.pumpActive) Text(
                        "Gobernador de FPS: objetivo ${s.targetFps} fps de ${s.preset.fps} pedidos · causa observada: ${s.bottleneck}." +
                            (s.governorNote?.let { "\n$it" } ?: "") +
                            "\nEstado térmico de Android: ${s.thermal ?: "no disponible"}.",
                        style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.OnBackground,
                    )
                    if (s.fpsWarning != null) Text(s.fpsWarning, style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.Warning)
                    Text(
                        "Página para navegadores viejos (TV): escribí http://" + s.hlsUrl.substringAfter("http://").substringBefore("/") + "/ " +
                            "(solo con dirección corta). " +
                            if (s.diag.isEmpty()) "Todavía ningún navegador informó." else "El navegador informó:\n" + s.diag.takeLast(4).joinToString("\n"),
                        style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.Muted,
                    )
                    if (s.note != null) Text(s.note, style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.Warning)
                    if (s.audio == IptvAudio.CAPTURING_NO_SOUND || s.audio == IptvAudio.CAPTURING_ZEROS) {
                        Text(
                            "Se captura audio pero no se detecta sonido. Si algo está sonando en el teléfono y sigue así, la app de origen bloquea la captura (Android no permite capturarla).",
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
                    TvButton(
                        text = if (auto) "Calidad: automática (mejor medida)" else "Calidad: ${preset.shortLabel}",
                        onClick = { if (auto) viewModel.toggleAuto() else viewModel.cyclePreset() }, horizontalPadding = 20.dp,
                    )
                    if (!auto) TvButton(text = "Volver a calidad automática", onClick = viewModel::toggleAuto, horizontalPadding = 20.dp)
                    TvButton(
                        text = if (measuring) "Midiendo… (unos segundos)" else "Medir mi teléfono (1080p60/720p60)",
                        onClick = { if (!measuring) viewModel.measure() }, horizontalPadding = 20.dp,
                    )
                    Text(
                        if (calibration.isEmpty()) "Sin medición: la calidad automática usa la elegida a mano hasta que midas."
                        else "Última medición (codificación real, sin pantalla):\n" + calibration.joinToString("\n"),
                        style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.Muted,
                    )
                    TvButton(text = "Dirección: ${access.label}", onClick = viewModel::cycleAccess, horizontalPadding = 20.dp)
                    TvButton(
                        text = if (pump) "Ritmo fijo de cuadros: activado (recomendado)" else "Ritmo fijo de cuadros: desactivado (captura directa)",
                        onClick = viewModel::togglePump, horizontalPadding = 20.dp,
                    )
                }
                TvButton(
                    text = if (soloTv) "Audio solo en TV: activado (experimental)" else "Audio solo en TV: desactivado",
                    onClick = viewModel::toggleSoloTv, horizontalPadding = 20.dp,
                )
                Text(
                    (if (running || soloTv) localAudio + "\n" else "") +
                        "Android no ofrece una forma universal de separar el sonido del teléfono del que se captura. OpenTV prueba bajar el volumen " +
                        "del teléfono y mide si la captura sigue recibiendo sonido; si se apaga, lo restaura solo. Con auriculares o Bluetooth, o si la " +
                        "app de origen bloquea la captura, puede no ser posible. Confirmá de oído que el televisor suena y el teléfono no.",
                    style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.Muted,
                )
                TvButton(text = "Volver", onClick = onBack, horizontalPadding = 20.dp)
            }
        }
    }
}
