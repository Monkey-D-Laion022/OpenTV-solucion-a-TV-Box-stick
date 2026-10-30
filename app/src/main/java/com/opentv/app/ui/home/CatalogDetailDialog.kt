package com.opentv.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.opentv.app.catalog.CatalogItem
import com.opentv.app.catalog.CatalogText
import com.opentv.app.ui.components.TvButton
import com.opentv.app.ui.theme.OpenTvColors

/**
 * Ficha de un título del catálogo. Dice con claridad que es solo información: no hay botón de reproducir porque OpenTV
 * todavía no tiene ninguna fuente de reproducción configurada para el catálogo.
 */
@Composable
fun CatalogDetailDialog(item: CatalogItem, onDismiss: () -> Unit) {
    val closeFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LaunchedEffect(Unit) { runCatching { closeFocus.requestFocus() } }
        Column(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .background(OpenTvColors.Surface, RoundedCornerShape(22.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "${CatalogText.INFO_ONLY} · TMDB",
                style = MaterialTheme.typography.labelLarge,
                color = OpenTvColors.Primary,
            )
            Text(text = item.title, style = MaterialTheme.typography.headlineMedium, color = OpenTvColors.OnBackground)
            Text(text = CatalogText.metaLine(item), style = MaterialTheme.typography.titleMedium, color = OpenTvColors.Muted)
            Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                Text(text = CatalogText.overviewOrNote(item), style = MaterialTheme.typography.bodyLarge, color = OpenTvColors.OnBackground)
            }
            Text(text = CatalogText.NOT_PLAYABLE, style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.Warning)
            TvButton(text = "Cerrar", onClick = onDismiss, modifier = Modifier.focusRequester(closeFocus))
        }
    }
}
