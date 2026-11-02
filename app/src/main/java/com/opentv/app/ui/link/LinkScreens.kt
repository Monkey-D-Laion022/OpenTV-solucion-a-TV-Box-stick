package com.opentv.app.ui.link

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.ui.PlayerView
import com.opentv.app.link.LinkAddress
import com.opentv.app.link.LinkFound
import com.opentv.app.link.LinkNsd
import com.opentv.app.link.LinkReceiverController
import com.opentv.app.link.LinkReceiverState
import com.opentv.app.link.LinkSenderState
import com.opentv.app.link.TsDataSource
import com.opentv.app.ui.components.SafeArea
import com.opentv.app.ui.components.TvButton
import com.opentv.app.ui.theme.OpenTvColors
import kotlinx.coroutines.delay

/** Emisor OpenTV Link: la pantalla del teléfono, cifrada, hacia otro equipo con OpenTV. */
@Composable
fun LinkSendScreen(onBack: () -> Unit, vm: LinkSendViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val preset by vm.preset.collectAsStateWithLifecycle()
    val context = LocalContext.current
    fun audioGranted() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) vm.start(result.resultCode, data, audioGranted())
    }
    fun launchCapture() {
        launcher.launch(context.getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
    }
    val audioPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { launchCapture() }
    val active = state is LinkSenderState.Waiting || state is LinkSenderState.Streaming || state is LinkSenderState.ConfirmCode || state is LinkSenderState.Starting

    SafeArea {
        Column(Modifier.fillMaxSize().padding(8.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("OpenTV Link · emitir", style = MaterialTheme.typography.headlineMedium, color = OpenTvColors.OnBackground)
            Text(
                "Envía la pantalla (y el sonido del teléfono, si concedés el permiso de audio y la app de origen lo permite) a otro equipo con OpenTV, " +
                    "cifrada de punta a punta. La primera vez se empareja comparando un código de 6 dígitos en las dos pantallas. " +
                    "Sin probar todavía en equipos reales: la lista de pruebas dice qué observar.",
                style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.Muted,
            )
            when (val s = state) {
                LinkSenderState.Idle -> Text("Detenido.", color = OpenTvColors.OnBackground)
                LinkSenderState.Starting -> Text("Iniciando…", color = OpenTvColors.OnBackground)
                is LinkSenderState.Failed -> Text(s.message, style = MaterialTheme.typography.bodyLarge, color = OpenTvColors.Warning)
                is LinkSenderState.Waiting -> {
                    Text("Esperando un receptor. En el otro equipo: OpenTV › Conexiones › Recibir con OpenTV Link.", color = OpenTvColors.Success)
                    Text("Dirección para escribir a mano: ${s.address}", style = MaterialTheme.typography.titleMedium, color = OpenTvColors.OnBackground)
                    Text(s.announce, style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.Muted)
                    Text(
                        if (s.paired.isEmpty()) "Equipos emparejados: ninguno." else "Equipos emparejados: " + s.paired.joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.Muted,
                    )
                }
                is LinkSenderState.ConfirmCode -> {
                    Text("Emparejamiento: confirmá que este código es IGUAL al que muestra el otro equipo.", color = OpenTvColors.OnBackground)
                    Text(s.code, style = MaterialTheme.typography.displayMedium, color = OpenTvColors.Success)
                }
                is LinkSenderState.Streaming -> {
                    Text("Transmitiendo a ${s.peerName} · cifrado AES‑256‑GCM", style = MaterialTheme.typography.bodyLarge, color = OpenTvColors.Success)
                    Text(
                        "Pedido/configurado: ${s.preset.width}×${s.preset.height} a ${s.preset.fps} fps, ${s.preset.bitrateBps / 1000} kbit/s.\n" +
                            "Codificados (medidos): ${"%.0f".format(s.encodedFps)} fps · producidos ${s.producedKbps} kbit/s · bitrate actual del control ${s.currentBitrateKbps} kbit/s.\n" +
                            "Receptor informa: búfer ${s.receiverBufferMs} ms · cuadros descartados ${s.receiverDropped} · cola de envío ${s.queuedKb} KB.\n" +
                            "Audio: ${s.audio}\nCodificador: ${s.codec.ifBlank { "no determinado" }}\n" +
                            "FPS que muestra el receptor: no se puede medir desde aquí.",
                        style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.OnBackground,
                    )
                    if (s.note != null) Text(s.note, style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.Warning)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val confirm = state as? LinkSenderState.ConfirmCode
                if (confirm != null) {
                    TvButton(text = "Los códigos coinciden", onClick = { vm.controller.confirmCode(true) }, horizontalPadding = 20.dp)
                    TvButton(text = "No coinciden: cancelar", onClick = { vm.controller.confirmCode(false) }, horizontalPadding = 20.dp)
                }
                if (active) {
                    TvButton(text = "Detener la transmisión", onClick = { vm.controller.stop() }, horizontalPadding = 20.dp)
                } else {
                    TvButton(
                        text = "Iniciar emisor",
                        onClick = { if (audioGranted()) launchCapture() else audioPermission.launch(Manifest.permission.RECORD_AUDIO) },
                        horizontalPadding = 20.dp,
                    )
                    TvButton(text = "Calidad: ${preset.shortLabel}", onClick = { vm.controller.cyclePreset() }, horizontalPadding = 20.dp)
                }
                TvButton(text = "Volver", onClick = onBack, horizontalPadding = 20.dp)
            }
        }
    }
}

/** Receptor OpenTV Link: se conecta a un emisor, se empareja y reproduce lo que llega. */
@OptIn(UnstableApi::class)
@Composable
fun LinkReceiveScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val controller = remember { LinkReceiverController.get(context) }
    val state by controller.state.collectAsStateWithLifecycle()
    val sessions by controller.sessions.collectAsStateWithLifecycle()
    var host by remember { mutableStateOf(controller.lastSender) }
    var found by remember { mutableStateOf<List<LinkFound>>(emptyList()) }
    var status by remember { mutableStateOf("") }
    val nsd = remember { LinkNsd(context) }
    val exo = remember {
        ExoPlayer.Builder(context)
            .setLoadControl(
                DefaultLoadControl.Builder().setBufferDurationsMs(1_000, 3_000, 500, 1_000).build(),
            ).build()
    }
    var stats by remember { mutableStateOf(0 to 0L) }

    DisposableEffect(Unit) {
        controller.playerStats = { stats }
        nsd.discover(
            onFound = { f -> found = (found.filter { it.advert.deviceId != f.advert.deviceId } + f) },
            onStatus = { status = it },
        )
        onDispose {
            nsd.stopDiscovery()
            controller.playerStats = { 0 to 0L }
            exo.release()
        }
    }
    // Cada conexión con video prepara el reproductor de nuevo con la tubería nueva.
    LaunchedEffect(sessions) {
        if (sessions > 0) {
            val factory = DataSource.Factory { TsDataSource { controller.source } }
            val extractors = DefaultExtractorsFactory().setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS)
            exo.setMediaSource(ProgressiveMediaSource.Factory(factory, extractors).createMediaSource(MediaItem.fromUri("opentv://link")))
            exo.prepare()
            exo.playWhenReady = true
        }
    }
    // ExoPlayer solo se consulta desde el hilo principal: se copia aquí lo que el hilo de red necesita informar.
    LaunchedEffect(Unit) {
        while (true) {
            val dropped = exo.videoDecoderCounters?.let { it.ensureUpdated(); it.droppedBufferCount.toLong() } ?: 0L
            stats = exo.totalBufferedDuration.toInt() to dropped
            delay(500)
        }
    }

    SafeArea {
        Box(Modifier.fillMaxSize()) {
            if (state is LinkReceiverState.Receiving) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { c -> PlayerView(c).apply { player = exo; useController = false } },
                )
            }
            Column(Modifier.fillMaxSize().padding(8.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val s = state
                if (s !is LinkReceiverState.Receiving) {
                    Text("OpenTV Link · recibir", style = MaterialTheme.typography.headlineMedium, color = OpenTvColors.OnBackground)
                    Text(
                        "Mostrá en este equipo lo que emite otro teléfono con OpenTV. Elegí un emisor encontrado en la red o escribí su dirección. " +
                            "La primera vez usá «Emparejar» y comparen el código de 6 dígitos; después «Conectar».",
                        style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.Muted,
                    )
                }
                when (s) {
                    LinkReceiverState.Idle -> Text(status.ifBlank { "Sin conexión." }, color = OpenTvColors.Muted)
                    is LinkReceiverState.Connecting -> Text("Conectando con ${s.host} (intento ${s.attempt})…", color = OpenTvColors.OnBackground)
                    is LinkReceiverState.ConfirmCode -> {
                        Text("Confirmá que este código es IGUAL al del emisor:", color = OpenTvColors.OnBackground)
                        Text(s.code, style = MaterialTheme.typography.displayMedium, color = OpenTvColors.Success)
                    }
                    is LinkReceiverState.Failed -> Text(s.message, style = MaterialTheme.typography.bodyLarge, color = OpenTvColors.Warning)
                    is LinkReceiverState.Receiving -> Text(
                        "${s.senderName} · ${s.offer.width}×${s.offer.height} pedido a ${s.offer.fps} fps · recibiendo ${s.kbps} kbit/s · desbordes: ${s.overflows}",
                        style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.Success,
                    )
                }
                if (s is LinkReceiverState.Idle || s is LinkReceiverState.Failed) {
                    for (f in found) {
                        TvButton(
                            text = "${f.advert.name} (${f.host}:${f.advert.port})",
                            onClick = { host = "${f.host}:${f.advert.port}" }, horizontalPadding = 20.dp,
                        )
                    }
                    OutlinedTextField(
                        value = host, onValueChange = { host = it }, singleLine = true,
                        label = { Text("Dirección del emisor (IP:puerto)") }, modifier = Modifier.fillMaxWidth(),
                    )
                    val parsed = LinkAddress.parse(host)
                    TvButton(text = "Emparejar (primera vez)", onClick = { parsed?.let { controller.connect(it.first, it.second, true) } }, horizontalPadding = 20.dp)
                    TvButton(text = "Conectar (ya emparejado)", onClick = { parsed?.let { controller.connect(it.first, it.second, false) } }, horizontalPadding = 20.dp)
                    if (parsed == null && host.isNotBlank()) Text("Escribí la dirección como 192.168.1.100:8765", color = OpenTvColors.Warning)
                    val peers = controller.peers
                    Text(if (peers.isEmpty()) "Emisores emparejados: ninguno." else "Emisores emparejados: " + peers.joinToString(", ") { it.name }, color = OpenTvColors.Muted)
                }
                if (s is LinkReceiverState.ConfirmCode) {
                    TvButton(text = "Los códigos coinciden", onClick = { controller.confirmCode(true) }, horizontalPadding = 20.dp)
                    TvButton(text = "No coinciden: cancelar", onClick = { controller.confirmCode(false) }, horizontalPadding = 20.dp)
                }
                if (s is LinkReceiverState.Connecting || s is LinkReceiverState.Receiving || s is LinkReceiverState.ConfirmCode) {
                    TvButton(text = "Desconectar", onClick = { controller.disconnect() }, horizontalPadding = 20.dp)
                }
                TvButton(text = "Volver", onClick = onBack, horizontalPadding = 20.dp)
                Box(Modifier.height(1.dp))
            }
        }
    }
}
