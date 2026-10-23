package com.opentv.app.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.opentv.app.apps.InstalledApp
import com.opentv.app.ui.components.FocusableSurface
import com.opentv.app.ui.components.TvButton
import com.opentv.app.ui.components.TvListItem
import com.opentv.app.ui.theme.OpenTvColors

/** Tarjeta «+» al final de la fila de aplicaciones: abre el selector de apps de la fila. */
@Composable
fun AddAppCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusableSurface(onClick = onClick, modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "+",
                style = MaterialTheme.typography.displayMedium,
                color = OpenTvColors.Primary,
            )
            Text(
                text = "Agregar o quitar",
                style = MaterialTheme.typography.labelLarge,
                color = OpenTvColors.OnBackground,
            )
        }
    }
}

/**
 * Lista de todas las apps instaladas para fijar o sacar de la fila principal. Cada toque alterna una
 * app y la lista se actualiza al instante.
 */
@Composable
fun AppPickerDialog(
    apps: List<InstalledApp>,
    pinned: Set<String>,
    onToggle: (InstalledApp) -> Unit,
    onDismiss: () -> Unit,
) {
    val doneFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { doneFocus.requestFocus() }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .fillMaxHeight(0.94f)
                .background(OpenTvColors.Background, RoundedCornerShape(24.dp)),
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Apps de tu fila principal",
                    style = MaterialTheme.typography.headlineSmall,
                    color = OpenTvColors.OnBackground,
                )
                Text(
                    text = "Tocá una app para agregarla o quitarla. ${pinned.size} en la fila.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OpenTvColors.Muted,
                )
                if (apps.isEmpty()) {
                    Text(
                        text = "No se encontraron aplicaciones que se puedan abrir.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = OpenTvColors.Muted,
                    )
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(apps, key = { it.packageName }) { app ->
                        val isPinned = app.packageName in pinned
                        TvListItem(
                            title = (if (isPinned) "★  " else "") + app.label,
                            subtitle = if (isPinned) "En tu fila · tocá para quitar" else "Tocá para agregar",
                            onClick = { onToggle(app) },
                        )
                    }
                }
                TvButton(
                    text = "Listo",
                    onClick = onDismiss,
                    modifier = Modifier.focusRequester(doneFocus),
                )
            }
        }
    }
}
