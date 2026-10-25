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
    viewModel: NativeMirrorViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val shizuku by viewModel.shizuku.collectAsStateWithLifecycle()
    val pending by viewModel.resizePending.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    val active = state is NativeMirrorState.Applying ||
        state is NativeMirrorState.WaitingForTv ||
        state is NativeMirrorState.Mirroring

    SafeArea {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Espejo nativo 16:9", style = MaterialTheme.typography.headlineMedium, color = OpenTvColors.OnBackground)
            Text(
                text = "OpenTV pone el teléfono en 16:9 con Shizuku y abre la transmisión de pantalla del sistema; ahí " +
                    "tocás tu TV. La resolución queda forzada en todo el teléfono hasta que toques «Detener y restaurar» " +
                    "(o «Restaurar resolución»). Para que no haya franjas, usá el teléfono y las apps en horizontal.",
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
                    is NativeMirrorState.WaitingForTv ->
                        "Resolución forzada (${s.size}) y se mantiene hasta que la restaures. Elegí tu TV en el panel del sistema; después podés salir de OpenTV y abrir cualquier app."
                    is NativeMirrorState.Mirroring -> "Transmitiendo (${s.size}). La resolución sigue forzada: volvé acá y tocá «Detener y restaurar» cuando termines."
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
            message?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = OpenTvColors.Warning) }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (active) {
                    TvButton(
                        text = "Detener y restaurar",
                        onClick = viewModel::stop,
                        horizontalPadding = 20.dp,
                        modifier = Modifier.focusRequester(firstFocus),
                    )
                } else {
                    TvButton(
                        text = "Forzar 16:9 y abrir transmisión",
                        onClick = viewModel::start,
                        horizontalPadding = 20.dp,
                        modifier = Modifier.focusRequester(firstFocus),
                    )
                }
                if (shizuku == ShizukuShell.Status.NEEDS_PERMISSION) {
                    TvButton(text = "Permiso de Shizuku", onClick = viewModel::requestPermission, horizontalPadding = 20.dp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvButton(text = "Restaurar resolución", onClick = viewModel::restore, horizontalPadding = 20.dp)
                TvButton(text = "Volver", onClick = onBack, horizontalPadding = 20.dp)
            }
        }
    }
}
