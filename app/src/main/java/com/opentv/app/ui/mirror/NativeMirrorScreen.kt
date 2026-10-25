package com.opentv.app.ui.mirror

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.opentv.app.core.BuildInfo
import com.opentv.app.display.NativeMirrorState
import com.opentv.app.display.ShizukuShell
import com.opentv.app.ui.components.SafeArea
import com.opentv.app.ui.components.TvButton
import com.opentv.app.ui.theme.OpenTvColors

/**
 * Espejo nativo 16:9: OpenTV fuerza la resolución del teléfono con Shizuku y abre la transmisión del sistema
 * (Miracast / pantalla inalámbrica); la conexión con el TV la crea y la corta Android.
 */
@Composable
fun NativeMirrorScreen(
    onBack: () -> Unit,
    onOpenGuide: () -> Unit,
    viewModel: NativeMirrorViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val shizuku by viewModel.shizuku.collectAsStateWithLifecycle()
    val pending by viewModel.resizePending.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    val context = androidx.compose.ui.platform.LocalContext.current
    val forced = state as? NativeMirrorState.Forced
    val active = state is NativeMirrorState.Applying || forced != null

    SafeArea {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Espejo nativo 16:9", style = MaterialTheme.typography.headlineMedium, color = OpenTvColors.OnBackground)
            Text(
                text = "Dos pasos manuales: 1) «Forzar 16:9» (solo el tamaño; la densidad no se toca para no reacomodar los íconos) " +
                    "y 2) «Abrir transmisión» cuando quieras; ahí tocás tu TV. La resolución queda forzada en todo el teléfono " +
                    "hasta que toques «Restaurar». Para que no haya franjas, usá el teléfono y las apps en horizontal.",
                style = MaterialTheme.typography.bodyMedium,
                color = OpenTvColors.Muted,
            )
            Text(
                text = "Versión instalada: ${BuildInfo.LABEL}",
                style = MaterialTheme.typography.bodyMedium,
                color = OpenTvColors.Muted,
            )
            Text(
                text = "Shizuku: ${shizuku.message}",
                style = MaterialTheme.typography.bodyLarge,
                color = if (shizuku == ShizukuShell.Status.READY) OpenTvColors.Success else OpenTvColors.Warning,
            )
            Text(
                text = when (val s = state) {
                    is NativeMirrorState.Idle -> s.note ?: "Estado: detenido."
                    NativeMirrorState.Applying -> "Aplicando 16:9 al teléfono…"
                    is NativeMirrorState.Forced -> buildString {
                        append("Resolución forzada (${s.size}); se mantiene hasta que la restaures. ")
                        append(if (s.tvConnected) "Hay una pantalla externa conectada." else "Todavía no hay pantalla externa conectada.")
                        s.panelNote?.let { append("\n").append(it) }
                    }
                    is NativeMirrorState.Failed -> s.message
                },
                style = MaterialTheme.typography.bodyLarge,
                color = if (state is NativeMirrorState.Failed) OpenTvColors.Warning else OpenTvColors.OnBackground,
            )
            if (pending) {
                Text(
                    text = "La resolución del teléfono está modificada por OpenTV. Si no vuelve sola, tocá «Restaurar resolución».",
                    style = MaterialTheme.typography.bodyLarge,
                    color = OpenTvColors.Warning,
                )
            }
            if (!viewModel.castAvailable || !viewModel.wifiDirect) {
                Text(
                    text = "Este teléfono no parece tener transmisión Miracast del sistema. Alternativa: «Proyectar OpenTV en el TV» " +
                        "(DLNA propio) o «Espejo por navegador» (desde el inicio): funcionan con cualquier teléfono con Wi-Fi.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = OpenTvColors.Warning,
                )
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = OpenTvColors.Warning) }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (forced != null) {
                    TvButton(
                        text = "Abrir transmisión",
                        onClick = { viewModel.openPanel(context) },
                        horizontalPadding = 20.dp,
                        modifier = Modifier.focusRequester(firstFocus),
                    )
                    TvButton(text = "Restaurar", onClick = viewModel::stop, horizontalPadding = 20.dp)
                } else {
                    TvButton(
                        text = "Forzar 16:9",
                        onClick = viewModel::force,
                        horizontalPadding = 20.dp,
                        modifier = Modifier.focusRequester(firstFocus),
                    )
                }
                if (shizuku == ShizukuShell.Status.NEEDS_PERMISSION) {
                    TvButton(text = "Permiso de Shizuku", onClick = viewModel::requestPermission, horizontalPadding = 20.dp)
                }
                if (shizuku != ShizukuShell.Status.READY) {
                    TvButton(text = "Cómo activar Shizuku", onClick = onOpenGuide, horizontalPadding = 20.dp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvButton(text = "Restaurar resolución", onClick = viewModel::restore, horizontalPadding = 20.dp)
                TvButton(text = "Volver", onClick = onBack, horizontalPadding = 20.dp)
            }
        }
    }
}
