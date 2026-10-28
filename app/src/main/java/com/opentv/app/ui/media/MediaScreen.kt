package com.opentv.app.ui.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.opentv.app.navigation.PendingFeature
import com.opentv.app.navigation.Routes
import com.opentv.app.ui.components.PendingBadge
import com.opentv.app.ui.components.StatusChip
import com.opentv.app.ui.components.TvCard
import com.opentv.app.ui.home.HomeViewModel
import com.opentv.app.ui.theme.OpenTvColors

/**
 * Multimedia: solo lo que existe. «Reproducir un enlace directo» abre el reproductor real; «Multimedia local» está
 * marcado como pendiente. No hay catálogo, historial ni carátulas: se dice con claridad en lugar de simularlos.
 */
@Composable
fun MediaScreen(onNavigate: (String) -> Unit, viewModel: HomeViewModel = viewModel()) {
    val outputTarget by viewModel.outputTarget.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Multimedia", style = MaterialTheme.typography.headlineMedium, color = OpenTvColors.OnBackground)
        val name = outputTarget?.name
        StatusChip(
            text = if (name == null) "Salida: este teléfono (ningún TV elegido)" else "Salida: $name",
            dotColor = if (name == null) OpenTvColors.Muted else OpenTvColors.Success,
        )

        Text("Disponible", style = MaterialTheme.typography.titleLarge, color = OpenTvColors.OnBackground)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(6.dp)) {
            item {
                TvCard(
                    title = "Reproducir un enlace directo",
                    subtitle = "Pegá la dirección de un video (http:// o https://)",
                    onClick = { onNavigate(Routes.PLAYER) },
                    modifier = Modifier.width(280.dp),
                )
            }
            item {
                TvCard(
                    title = "Herramientas",
                    subtitle = "Funciones avanzadas",
                    onClick = { onNavigate(Routes.TOOLS) },
                    modifier = Modifier.width(280.dp),
                )
            }
        }

        Text("Pendiente", style = MaterialTheme.typography.titleLarge, color = OpenTvColors.OnBackground)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(6.dp)) {
            item {
                TvCard(
                    title = "Multimedia local",
                    subtitle = "Videos, fotos y música del teléfono",
                    badge = "Pendiente",
                    onClick = { onNavigate(Routes.pending(PendingFeature.LOCAL_MEDIA)) },
                    modifier = Modifier.width(280.dp),
                )
            }
        }
        Text(
            "Todavía no hay catálogo, historial de reproducción ni complementos: cuando existan van a aparecer acá. " +
                "Mientras tanto no se muestran contenidos de ejemplo.",
            style = MaterialTheme.typography.bodyMedium,
            color = OpenTvColors.Muted,
        )
    }
}
