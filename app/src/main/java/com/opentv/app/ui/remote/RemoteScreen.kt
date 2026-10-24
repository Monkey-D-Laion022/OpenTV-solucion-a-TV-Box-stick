package com.opentv.app.ui.remote

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.opentv.app.stream.Direction
import com.opentv.app.core.BuildInfo
import com.opentv.app.stream.ProjectionState
import com.opentv.app.ui.apps.PortraitWarningDialog
import com.opentv.app.ui.components.SafeArea
import com.opentv.app.ui.components.TvButton
import com.opentv.app.ui.connect.CastPickerDialog
import com.opentv.app.ui.theme.OpenTvColors

private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Control remoto táctil de la proyección: arranca/detiene el flujo hacia el TV y mueve el cursor de
 * la interfaz que se ve allí.
 */
@Composable
fun RemoteScreen(
    onBack: () -> Unit,
    viewModel: RemoteViewModel = viewModel(),
) {
    val activity = LocalContext.current.findActivity()
    val projection by viewModel.projection.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val testPattern by viewModel.testPattern.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val target by viewModel.outputTarget.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val launchState by viewModel.appLaunch.state.collectAsStateWithLifecycle()
    var pickerOpen by remember { mutableStateOf(false) }

    if (pickerOpen) CastPickerDialog(onDismiss = { pickerOpen = false })
    launchState.pendingWarning?.let { app ->
        PortraitWarningDialog(
            app = app,
            onConfirm = viewModel.appLaunch::confirmWarning,
            onDismiss = viewModel.appLaunch::dismissWarning,
        )
    }

    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    val streaming = projection is ProjectionState.Streaming
    val busy = projection is ProjectionState.Starting

    SafeArea {
        Row(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Proyectar OpenTV en el TV",
                    style = MaterialTheme.typography.headlineMedium,
                    color = OpenTvColors.OnBackground,
                )
                Text(
                    text = "OpenTV dibuja su propia pantalla 16:9 de 1080p, la convierte en video y se la envía al TV por " +
                        "Wi‑Fi (DLNA), sin cable y sin la función «Emitir» de Android. Llega con unos segundos de " +
                        "retraso. Las apps que abras se ven en el teléfono, no en el TV.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OpenTvColors.Muted,
                )
                Text(
                    text = "TV elegido: " + (target?.let { "${it.name} (${it.protocol})" } ?: "ninguno"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (target != null) OpenTvColors.Success else OpenTvColors.Muted,
                )
                Text(
                    text = when (val p = projection) {
                        ProjectionState.Idle -> "Estado: detenida."
                        ProjectionState.Starting -> "Estado: iniciando…"
                        is ProjectionState.Streaming -> "Estado: proyectando en ${p.deviceName}. Mirá el TV y navegá con los botones."
                        is ProjectionState.Failed -> p.message
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (projection is ProjectionState.Failed) OpenTvColors.Warning else OpenTvColors.OnBackground,
                )
                Text(
                    text = "Versión instalada: ${BuildInfo.LABEL}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OpenTvColors.Muted,
                )
                if (stats.phase.isNotEmpty()) {
                    Text(
                        text = stats.describe(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = OpenTvColors.Muted,
                    )
                }
                if (!streaming && !busy) {
                    Text(
                        text = "Prueba sin interfaz: ${if (testPattern) "ACTIVADA (se envía una barra que se mueve)" else "desactivada"}. " +
                            "Formato de video: ${profile.label}. Si el TV dice «archivo no compatible» o queda en negro, " +
                            "detené la proyección, tocá «Cambiar formato» y probá de nuevo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OpenTvColors.Muted,
                    )
                }
                message?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = OpenTvColors.Warning) }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvButton(
                        text = "Elegir TV",
                        onClick = { pickerOpen = true },
                        horizontalPadding = 20.dp,
                        modifier = Modifier.focusRequester(firstFocus),
                    )
                    if (streaming || busy) {
                        TvButton(text = "Detener proyección", onClick = viewModel::stop, horizontalPadding = 20.dp)
                    } else {
                        TvButton(
                            text = "Iniciar proyección",
                            onClick = { activity?.let(viewModel::start) },
                            horizontalPadding = 20.dp,
                        )
                    }
                    if (!streaming && !busy) {
                        TvButton(text = "Cambiar formato", onClick = viewModel::cycleProfile, horizontalPadding = 20.dp)
                    }
                    TvButton(text = "Volver", onClick = onBack, horizontalPadding = 20.dp)
                }
                if (!streaming && !busy) {
                    TvButton(text = "Prueba sin interfaz", onClick = viewModel::toggleTestPattern, horizontalPadding = 20.dp)
                }
                if (stats.phase.isNotEmpty()) {
                    TvButton(
                        text = "Copiar diagnóstico",
                        onClick = { clipboard.setText(AnnotatedString(stats.describe())) },
                        horizontalPadding = 20.dp,
                    )
                }
            }

            // Cruceta táctil: mueve el cursor de la interfaz del TV.
            Column(
                modifier = Modifier.width(300.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Control remoto", style = MaterialTheme.typography.titleMedium, color = OpenTvColors.OnBackground)
                TvButton(text = "↑", onClick = { viewModel.press(Direction.UP) }, modifier = Modifier.width(96.dp), horizontalPadding = 0.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvButton(text = "←", onClick = { viewModel.press(Direction.LEFT) }, modifier = Modifier.width(96.dp), horizontalPadding = 0.dp)
                    TvButton(text = "OK", onClick = viewModel::ok, modifier = Modifier.width(96.dp), horizontalPadding = 0.dp)
                    TvButton(text = "→", onClick = { viewModel.press(Direction.RIGHT) }, modifier = Modifier.width(96.dp), horizontalPadding = 0.dp)
                }
                TvButton(text = "↓", onClick = { viewModel.press(Direction.DOWN) }, modifier = Modifier.width(96.dp), horizontalPadding = 0.dp)
                TvButton(text = "Inicio", onClick = viewModel::back, modifier = Modifier.width(150.dp), horizontalPadding = 0.dp)
            }
        }
    }
}
