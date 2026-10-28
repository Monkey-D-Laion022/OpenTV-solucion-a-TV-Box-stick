package com.opentv.app.miracast

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.opentv.app.ui.components.SafeArea
import com.opentv.app.ui.components.TvButton
import com.opentv.app.ui.theme.OpenTvColors

/**
 * Etapa B: diagnóstico de la negociación Miracast/WFD. Nada arranca solo: se busca al pulsar Buscar y solo se intenta
 * conectar tras elegir un dispositivo Y confirmar en el diálogo. Se puede cancelar en todo momento.
 */
@Composable
fun WfdDiagnosticScreen(
    onBack: () -> Unit,
    viewModel: WfdDiagnosticViewModel = viewModel(),
) {
    val discovery by viewModel.discoveryUi.collectAsStateWithLifecycle()
    val session by viewModel.sessionUi.collectAsStateWithLifecycle()
    var toConfirm by remember { mutableStateOf<P2pPeer?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startDiscovery() else viewModel.permissionDenied()
    }
    val beginDiscovery: () -> Unit = {
        if (!P2pPolicy.canRun(Build.VERSION.SDK_INT) || viewModel.hasPermission()) {
            viewModel.startDiscovery()
        } else {
            launcher.launch(P2pPolicy.NEARBY_PERMISSION)
        }
    }
    DisposableEffect(Unit) { onDispose { viewModel.cancelTest(); viewModel.stopDiscovery() } }

    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    toConfirm?.let { peer ->
        ConfirmDialog(
            peer = peer,
            onConfirm = {
                toConfirm = null
                viewModel.startTest(peer)
            },
            onDismiss = { toConfirm = null },
        )
    }

    SafeArea {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Diagnóstico Wi-Fi Display (WFD)", style = MaterialTheme.typography.headlineMedium, color = OpenTvColors.OnBackground)
            Text(
                "Etapa B. Intenta, con un dispositivo que elijas y confirmes, formar un grupo Wi-Fi Direct y leer las capacidades de video que " +
                    "el televisor anuncia por RTSP (M1–M3). NO transmite video, NO anuncia a OpenTV como fuente Wi-Fi Display (Android lo reserva " +
                    "a apps del sistema) y NO prueba los 60 FPS: solo averigua hasta qué fase se puede llegar y qué dice el televisor. " +
                    "Descubrir, conectar, negociar, transmitir y lograr 60 FPS son etapas distintas.",
                style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.Muted,
            )

            // ---- 1. Descubrimiento ----
            Text("1 · Buscar dispositivos", style = MaterialTheme.typography.titleLarge, color = OpenTvColors.OnBackground)
            if (discovery.phase == P2pPhase.SEARCHING) {
                TvButton("Detener búsqueda", viewModel::stopDiscovery, horizontalPadding = 20.dp, modifier = Modifier.focusRequester(firstFocus))
            } else {
                TvButton(
                    "Buscar dispositivos (${P2pPolicy.DISCOVERY_SECONDS} s)",
                    onClick = beginDiscovery,
                    horizontalPadding = 20.dp,
                    modifier = Modifier.focusRequester(firstFocus),
                )
            }
            Text(P2pText.conclusion(discovery), style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.Muted)

            if (discovery.peers.isNotEmpty()) {
                Text("Dispositivos vistos (${discovery.peers.size})", style = MaterialTheme.typography.titleMedium, color = OpenTvColors.OnBackground)
                for (peer in discovery.peers) {
                    Text(
                        P2pText.peerLines(peer).joinToString("\n"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (P2pText.possibleLg(peer.name)) OpenTvColors.Success else OpenTvColors.OnBackground,
                    )
                    val canTest = !session.running && peer.address != null && peer.statusCode == 3
                    if (canTest) {
                        TvButton("Probar negociación con «${peer.name.ifBlank { "sin nombre" }}» ${P2pText.shortId(peer.address)}", { toConfirm = peer }, horizontalPadding = 20.dp)
                    } else if (peer.statusCode != 3) {
                        Text("No se puede probar ahora: estado ${P2pText.status(peer.statusCode)}.", style = MaterialTheme.typography.bodySmall, color = OpenTvColors.Warning)
                    }
                }
            }

            // ---- 2. Prueba ----
            if (session.started || session.running) {
                Text("2 · Prueba de negociación", style = MaterialTheme.typography.titleLarge, color = OpenTvColors.OnBackground)
                if (session.running) {
                    Text(
                        "En curso con «${session.observation.peerName}». Si es tu televisor, mirá su pantalla y aceptá el pedido de conexión con el control remoto.",
                        style = MaterialTheme.typography.bodyLarge, color = OpenTvColors.Warning,
                    )
                    TvButton("Cancelar prueba", viewModel::cancelTest, horizontalPadding = 20.dp)
                }
                Section("Fases (resultado de cada una)") {
                    if (session.steps.isEmpty()) Text("Todavía sin fases registradas.", color = OpenTvColors.Muted, style = MaterialTheme.typography.bodyMedium)
                    session.steps.forEach { Text(WfdDiagnosticText.stepLine(it), color = stepColor(it.outcome), style = MaterialTheme.typography.bodyMedium) }
                }
                val received = WfdDiagnosticText.received(session.observation)
                Section("Recibido del televisor (datos reales, sin interpretar)") {
                    if (received.isEmpty()) Text("Nada recibido todavía.", color = OpenTvColors.Muted, style = MaterialTheme.typography.bodyMedium)
                    received.forEach { Text(it, color = OpenTvColors.OnBackground, style = MaterialTheme.typography.bodyMedium) }
                }
                val decoded = WfdDiagnosticText.decoded(session.observation)
                if (decoded.isNotEmpty()) {
                    Section("Decodificado por OpenTV (tabla de AOSP aplicada al texto recibido)") {
                        decoded.forEach { Text(it, color = OpenTvColors.OnBackground, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
                Section("Inferencias de OpenTV (hipótesis, no datos del televisor)") {
                    WfdDiagnosticText.inferences(session.observation).forEach { Text(it, color = OpenTvColors.Warning, style = MaterialTheme.typography.bodyMedium) }
                }
                if (session.log.isNotEmpty()) {
                    Section("Registro (solo en pantalla, no se guarda)") {
                        Text(session.log.joinToString("\n"), color = OpenTvColors.Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            TvButton("Volver", onBack, horizontalPadding = 20.dp)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().background(OpenTvColors.Surface, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = OpenTvColors.Primary)
        content()
    }
}

private fun stepColor(o: StepOutcome) = when (o) {
    StepOutcome.OK -> OpenTvColors.Success
    StepOutcome.SKIPPED, StepOutcome.CANCELLED -> OpenTvColors.Muted
    StepOutcome.FAILED, StepOutcome.TIMEOUT -> OpenTvColors.Warning
}

@Composable
private fun ConfirmDialog(peer: P2pPeer, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val cancelFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Foco inicial en «Cancelar»: enviar la solicitud exige una acción deliberada.
        LaunchedEffect(Unit) { runCatching { cancelFocus.requestFocus() } }
        Column(
            modifier = Modifier.fillMaxWidth(0.7f).background(OpenTvColors.Surface, RoundedCornerShape(20.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("¿Enviar una solicitud de conexión?", style = MaterialTheme.typography.titleLarge, color = OpenTvColors.OnBackground)
            Text(
                "Dispositivo: «${peer.name.ifBlank { "sin nombre" }}» · identificador ${P2pText.shortId(peer.address)}",
                style = MaterialTheme.typography.bodyLarge, color = OpenTvColors.OnBackground,
            )
            Text(
                "• Solo se conecta si el dispositivo acepta. Si es tu televisor, vas a ver un pedido en su pantalla: aceptalo con el control remoto.\n" +
                    "• Si en tu televisor NO aparece ningún pedido, cancelá: puede ser el de otra persona.\n" +
                    "• No se cambia ningún ajuste del televisor ni de tu red del hogar. Android puede interrumpir un momento el Wi-Fi del teléfono " +
                    "durante la prueba (no verificado en este teléfono).\n" +
                    "• Dura como máximo un minuto y podés cancelar cuando quieras.",
                style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.Muted,
            )
            TvButton("Cancelar", onDismiss, horizontalPadding = 20.dp, modifier = Modifier.focusRequester(cancelFocus))
            TvButton("Enviar solicitud", onConfirm, horizontalPadding = 20.dp)
        }
    }
}
