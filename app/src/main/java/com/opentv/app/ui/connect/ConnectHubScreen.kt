package com.opentv.app.ui.connect

import android.app.Activity
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.opentv.app.connect.ConnectMode
import com.opentv.app.connect.ConnectModes
import com.opentv.app.connect.ConnectViewModel
import com.opentv.app.connect.TvTarget
import com.opentv.app.connect.TvTargets
import com.opentv.app.display.NativeMirrorState
import com.opentv.app.display.ShizukuShell
import com.opentv.app.navigation.Routes
import com.opentv.app.stream.LocalChannel
import com.opentv.app.stream.PlaybackAudioCapture
import com.opentv.app.stream.ProjectionState
import com.opentv.app.ui.components.SafeArea
import com.opentv.app.ui.components.TvButton
import com.opentv.app.ui.components.TvListItem
import com.opentv.app.ui.mirror.NativeMirrorViewModel
import com.opentv.app.ui.theme.OpenTvColors

/**
 * Central de conexiones: 16:9, emisión nativa del sistema, los TV que aparecen en la red con SUS modos, y las
 * alternativas para equipos sin emisión nativa. Reúne lo que antes estaba repartido en varias pantallas.
 */
@Composable
fun ConnectHubScreen(
    onBack: () -> Unit,
    onNavigate: (route: String) -> Unit,
    viewModel: ConnectViewModel = viewModel(),
    nativeViewModel: NativeMirrorViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val projection by viewModel.projection.collectAsStateWithLifecycle()
    val outputTarget by viewModel.outputTarget.collectAsStateWithLifecycle()
    val native by nativeViewModel.state.collectAsStateWithLifecycle()
    val shizuku by nativeViewModel.shizuku.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lockRotation by viewModel.options.lockRotation.collectAsStateWithLifecycle()
    val muteLocal by viewModel.options.muteLocal.collectAsStateWithLifecycle()
    val rate by viewModel.options.rate.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        viewModel.onScreenStarted()
        viewModel.scanDlna()
        nativeViewModel.refresh()
        onDispose { viewModel.onScreenStopped() }
    }

    val captureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) viewModel.startDlnaMirror(result.resultCode, data) else viewModel.mirrorDenied()
    }
    val launchCapture = {
        val manager = context.getSystemService(MediaProjectionManager::class.java)
        captureLauncher.launch(manager.createScreenCaptureIntent())
    }
    // Sin el permiso de audio el espejo sigue, pero sin sonido (se avisa en el estado).
    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { launchCapture() }
    val startMirror = {
        if (PlaybackAudioCapture.supported() && !PlaybackAudioCapture.hasPermission(context)) {
            audioLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        } else {
            launchCapture()
        }
    }

    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    val targets = TvTargets.build(state.castDevices, state.dlna.devices)
    val forced = native is NativeMirrorState.Forced
    val busy169 = native is NativeMirrorState.Applying

    SafeArea {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Transmitir a TV", style = MaterialTheme.typography.headlineMedium, color = OpenTvColors.OnBackground)

            // Estado de lo que está transmitiendo ahora.
            when (val p = projection) {
                ProjectionState.Idle -> Unit
                ProjectionState.Starting -> Text("Iniciando la transmisión…", style = MaterialTheme.typography.bodyLarge, color = OpenTvColors.Warning)
                is ProjectionState.Streaming -> {
                    Text("Transmitiendo a ${p.deviceName}.", style = MaterialTheme.typography.bodyLarge, color = OpenTvColors.Success)
                    if (p.deviceName == LocalChannel.NAME) {
                        Text(
                            "En el TV, abrí esta dirección en VLC o en un reproductor de red: ${p.url}\n" +
                                "Para apps de IPTV (lista M3U): ${p.url.substringBeforeLast('/')}/lista.m3u",
                            style = MaterialTheme.typography.bodyLarge, color = OpenTvColors.OnBackground,
                        )
                    }
                    TvButton(text = "Detener transmisión", onClick = viewModel::stopProjection, horizontalPadding = 20.dp)
                }
                is ProjectionState.Failed -> Text(p.message, style = MaterialTheme.typography.bodyLarge, color = OpenTvColors.Warning)
            }
            outputTarget?.let {
                Text("Salida del reproductor: ${it.name} (${it.protocol})", style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.Muted)
            }

            // 1) 16:9 y emisión nativa: lo más fluido (Miracast / Cast / Smart View del sistema).
            Text("Emisión nativa del teléfono", style = MaterialTheme.typography.titleMedium, color = OpenTvColors.OnBackground)
            Text(
                "Lo más fluido si el teléfono y el TV la tienen. Para que no haya franjas negras: primero 16:9, después la emisión.",
                style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.Muted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvButton(
                    text = when {
                        busy169 -> "Aplicando 16:9…"
                        forced -> "Restaurar 16:9"
                        else -> "Forzar 16:9"
                    },
                    onClick = {
                        when {
                            busy169 -> Unit
                            forced -> nativeViewModel.stop()
                            shizuku == ShizukuShell.Status.READY -> nativeViewModel.force()
                            else -> onNavigate(Routes.SHIZUKU_GUIDE) // el asistente dice qué falta y cómo hacerlo
                        }
                    },
                    horizontalPadding = 20.dp,
                    modifier = Modifier.focusRequester(firstFocus),
                )
                TvButton(
                    text = "Emisión nativa (Cast / Smart View / Miracast)",
                    onClick = { viewModel.openSystemCast(context) },
                    horizontalPadding = 20.dp,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvButton(
                    text = if (lockRotation) "Rotación: fija horizontal" else "Rotación: libre (prueba)",
                    onClick = viewModel.options::toggleLockRotation,
                    horizontalPadding = 16.dp,
                )
                TvButton(text = "Fluidez DLNA: ${rate.label}", onClick = viewModel.options::cycleRate, horizontalPadding = 16.dp)
                TvButton(
                    text = if (muteLocal) "Silenciar teléfono: sí" else "Silenciar teléfono: no",
                    onClick = viewModel.options::toggleMuteLocal,
                    horizontalPadding = 16.dp,
                )
            }
            Text(
                "Rotación fija (la de la versión que andaba estable): se aplica al forzar 16:9. Libre deja la interfaz arriba con una franja negra abajo. Fluidez: con 60 cuadros/s algunos TV viejos no abren el video; " +
                    "si pasa, volvé a 30. Silenciar el teléfono es experimental y puede dejar mudo también al TV.",
                style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.Muted,
            )
            if (shizuku != ShizukuShell.Status.READY && !forced) {
                Text(
                    "El 16:9 usa Shizuku y todavía no está lista: tocá «Forzar 16:9» y el asistente te guía paso a paso.",
                    style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.Warning,
                )
            }
            (native as? NativeMirrorState.Failed)?.let {
                Text(it.message, style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.Warning)
            }
            if (forced) {
                Text(
                    "16:9 forzado: seguirá así hasta que lo restaures (ponelo en horizontal para evitar franjas).",
                    style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.Success,
                )
            }

            // 2) Dispositivos de la red, cada uno con sus modos.
            Text(
                text = if (state.dlna.scanning) "Dispositivos en tu red (buscando…)" else "Dispositivos en tu red",
                style = MaterialTheme.typography.titleMedium, color = OpenTvColors.OnBackground,
            )
            if (targets.isEmpty()) {
                Text(
                    text = when {
                        !state.onLocalNetwork -> "Conectá el teléfono a la misma red Wi‑Fi que el TV."
                        state.dlna.scanning || !state.dlna.scanned -> "Buscando TV en tu red…"
                        else -> "No apareció ningún TV. Encendelo, verificá que esté en el mismo Wi‑Fi y buscá de nuevo."
                    },
                    style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.Muted,
                )
            }
            state.dlna.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.Warning) }
            ConnectModes.audioNote(Build.VERSION.SDK_INT)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.Warning)
            }
            targets.forEach { target ->
                TvListItem(title = target.name, subtitle = ConnectModes.summary(target))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ConnectModes.forTarget(target).forEach { mode ->
                        TvButton(
                            text = mode.title,
                            onClick = {
                                when (mode) {
                                    ConnectMode.DLNA_MIRROR -> {
                                        val device = (target as TvTarget.DlnaTarget).device
                                        viewModel.prepareMirror(device)
                                        startMirror()
                                    }
                                    ConnectMode.DLNA_PROJECT -> {
                                        viewModel.selectTarget(target)
                                        onNavigate(Routes.REMOTE)
                                    }
                                    ConnectMode.CAST_OUTPUT -> viewModel.selectTarget(target)
                                }
                            },
                            horizontalPadding = 16.dp,
                        )
                    }
                }
            }
            state.message?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = OpenTvColors.Warning) }
            TvButton(
                text = if (state.dlna.scanning) "Buscando…" else "Buscar de nuevo",
                onClick = viewModel::scanDlna,
                horizontalPadding = 20.dp,
            )

            // 3) Alternativas para teléfonos o TV sin emisión nativa.
            Text("Sin emisión nativa", style = MaterialTheme.typography.titleMedium, color = OpenTvColors.OnBackground)
            ConnectModes.WITHOUT_NATIVE_CAST.forEachIndexed { i, text ->
                Text("${i + 1}. $text", style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.Muted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvButton(text = "Espejo por navegador", onClick = { onNavigate(Routes.BROWSER) }, horizontalPadding = 20.dp)
                TvButton(
                    text = "Canal local (VLC / IPTV)",
                    onClick = {
                        viewModel.prepareLocalChannel()
                        startMirror()
                    },
                    horizontalPadding = 20.dp,
                )
                TvButton(text = "Guías y diagnóstico", onClick = { onNavigate(Routes.CONNECT) }, horizontalPadding = 20.dp)
                TvButton(text = "Volver", onClick = onBack, horizontalPadding = 20.dp)
            }
        }
    }
}
