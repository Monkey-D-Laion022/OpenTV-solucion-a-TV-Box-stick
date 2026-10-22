package com.opentv.app.ui.settings

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.opentv.app.ui.components.SafeArea
import com.opentv.app.ui.components.TvButton
import com.opentv.app.ui.components.TvListItem
import com.opentv.app.ui.theme.OpenTvColors

/**
 * Funciones avanzadas que no van en la pantalla principal. Aquí vive la entrada manual de enlaces;
 * más adelante se sumarán los complementos (add-ons).
 */
@Composable
fun ToolsScreen(onOpenPlayer: () -> Unit, onBack: () -> Unit) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    SafeArea {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Herramientas",
                style = MaterialTheme.typography.headlineMedium,
                color = OpenTvColors.OnBackground,
            )
            TvListItem(
                title = "Reproducir un enlace directo",
                subtitle = "Pegá la dirección de un video (http:// o https://). Si elegiste un TV como salida, se envía allí.",
                onClick = onOpenPlayer,
                modifier = Modifier.focusRequester(firstFocus),
            )
            Text(
                text = "Los complementos de Stremio se agregarán acá.",
                style = MaterialTheme.typography.bodyMedium,
                color = OpenTvColors.Muted,
            )
            TvButton(text = "Volver", onClick = onBack, modifier = Modifier.padding(top = 12.dp))
        }
    }
}
