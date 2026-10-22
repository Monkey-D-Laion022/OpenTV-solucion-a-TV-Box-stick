package com.opentv.app.ui.pending

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.opentv.app.navigation.PendingFeature
import com.opentv.app.ui.components.PendingBadge
import com.opentv.app.ui.components.SafeArea
import com.opentv.app.ui.components.TvButton
import com.opentv.app.ui.theme.OpenTvColors

/** Destino honesto de una función aún no implementada. Ofrece volver, nada más. */
@Composable
fun PendingScreen(feature: PendingFeature?, onBack: () -> Unit) {
    val backFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { backFocus.requestFocus() } }

    SafeArea {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = feature?.title ?: "Función desconocida",
                style = MaterialTheme.typography.headlineLarge,
                color = OpenTvColors.OnBackground,
            )
            if (feature != null) {
                PendingBadge("Pendiente · Fase ${feature.phase}")
                Text(
                    text = "Esta función todavía no está implementada.",
                    style = MaterialTheme.typography.titleMedium,
                    color = OpenTvColors.OnBackground,
                )
                Text(
                    text = "Plan: ${feature.plan}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = OpenTvColors.Muted,
                )
            } else {
                Text(
                    text = "No se encontró el destino solicitado.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = OpenTvColors.Muted,
                )
            }
            TvButton(
                text = "Volver",
                onClick = onBack,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .focusRequester(backFocus),
            )
        }
    }
}
