package com.opentv.app.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.opentv.app.apps.InstalledApp
import com.opentv.app.apps.LaunchUiState
import com.opentv.app.ui.components.TvButton
import com.opentv.app.ui.theme.OpenTvColors

/** Muestra el error del último intento de abrir una app, si lo hay. */
@Composable
fun LaunchMessage(state: LaunchUiState, modifier: Modifier = Modifier) {
    val message = state.message ?: return
    Text(
        text = message,
        style = MaterialTheme.typography.bodyLarge,
        color = OpenTvColors.Warning,
        modifier = modifier,
    )
}

/**
 * Aviso previo para apps que declaran orientación solo vertical. OpenTV no puede cambiarla;
 * el usuario decide si la abre igual o cancela y sigue en el launcher.
 */
@Composable
fun PortraitWarningDialog(
    app: InstalledApp,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val confirmFocus = remember { FocusRequester() }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // El efecto va DENTRO del diálogo: así corre cuando su contenido ya está compuesto y el
        // FocusRequester existe. Si aun así no estuviera listo, no se cierra la app por eso.
        LaunchedEffect(app.packageName) { runCatching { confirmFocus.requestFocus() } }

        Column(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .background(OpenTvColors.Surface, RoundedCornerShape(20.dp))
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.titleLarge,
                color = OpenTvColors.OnBackground,
            )
            Text(
                text = "Esta app está diseñada solo para orientación vertical. OpenTV no puede " +
                    "obligarla a abrirse en horizontal: puede verse angosta o con franjas.",
                style = MaterialTheme.typography.bodyLarge,
                color = OpenTvColors.Muted,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(top = 6.dp),
            ) {
                TvButton(
                    text = "Abrir igual",
                    onClick = onConfirm,
                    modifier = Modifier.focusRequester(confirmFocus),
                )
                TvButton(text = "Cancelar", onClick = onDismiss)
            }
        }
    }
}
