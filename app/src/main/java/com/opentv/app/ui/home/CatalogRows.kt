package com.opentv.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.opentv.app.catalog.CatalogItem
import com.opentv.app.catalog.CatalogText
import com.opentv.app.catalog.CatalogUiState
import com.opentv.app.catalog.CatalogViewModel
import com.opentv.app.ui.components.TvButton
import com.opentv.app.ui.theme.OpenTvColors
import com.opentv.app.ui.tv.ActionCard
import com.opentv.app.ui.tv.PosterCard
import com.opentv.app.ui.tv.TvMetrics

/**
 * Filas del catálogo del Inicio (datos de TMDB). Cada póster está rotulado «Solo info»: ver un título acá NO significa que
 * OpenTV pueda reproducirlo. Si falta la clave, o falla la red, se dice con claridad; si hay datos guardados se siguen mostrando.
 */
@Composable
fun CatalogRows(
    state: CatalogUiState,
    metrics: TvMetrics,
    viewModel: CatalogViewModel,
    focusFor: (String) -> Modifier,
    onItemFocused: (key: String, item: CatalogItem) -> Unit,
    onItemClick: (CatalogItem) -> Unit,
    onOpenSettings: () -> Unit,
) {
    if (!state.tmdbConfigured) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Catálogo", style = MaterialTheme.typography.titleLarge, color = OpenTvColors.OnBackground)
            Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)) {
                ActionCard(
                    title = "Catálogo sin configurar",
                    subtitle = "Agregá tu clave de TMDB en Ajustes › Catálogo",
                    onClick = onOpenSettings,
                    width = metrics.actionCardWidthDp.dp,
                    height = metrics.actionCardHeightDp.dp,
                    modifier = focusFor("catalog-setup"),
                )
            }
        }
        return
    }

    for (row in state.rows) {
        if (row.items.isEmpty()) continue
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(row.kind.title, style = MaterialTheme.typography.titleLarge, color = OpenTvColors.OnBackground)
                Text(
                    text = "${CatalogText.INFO_ONLY} · TMDB",
                    style = MaterialTheme.typography.labelLarge,
                    color = OpenTvColors.Muted,
                )
            }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
            ) {
                items(row.items, key = { "${row.kind.id}:${it.kind.name}:${it.id}" }) { item ->
                    val key = "poster:${row.kind.id}:${item.kind.name}:${item.id}"
                    val widthPx = with(LocalDensity.current) { metrics.posterWidthDp.dp.roundToPx() }
                    val image by produceState<ImageBitmap?>(initialValue = null, item.id, item.kind, widthPx) {
                        value = viewModel.poster(item, widthPx)
                    }
                    PosterCard(
                        title = item.title,
                        image = image,
                        width = metrics.posterWidthDp.dp,
                        height = metrics.posterHeightDp.dp,
                        infoLabel = "Solo info",
                        ratingLabel = CatalogText.ratingLabel(item.rating),
                        onClick = { onItemClick(item) },
                        onFocus = { onItemFocused(key, item) },
                        modifier = focusFor(key),
                    )
                }
            }
        }
    }

    when {
        state.loading && !state.hasContent ->
            Text("Cargando catálogo…", style = MaterialTheme.typography.bodyLarge, color = OpenTvColors.Muted)
        !state.hasContent && state.problem == null ->
            Text("El catálogo está vacío: TMDB no devolvió títulos.", style = MaterialTheme.typography.bodyLarge, color = OpenTvColors.Muted)
    }
    val problem = state.problem
    if (problem != null) {
        Text(
            text = (if (state.hasContent) "Mostrando datos guardados. " else "") + CatalogText.problem(problem, state.problemDetail),
            style = MaterialTheme.typography.bodyMedium,
            color = OpenTvColors.Warning,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(horizontal = 6.dp)) {
            TvButton(text = "Reintentar", onClick = { viewModel.refresh(force = true) }, horizontalPadding = 20.dp, modifier = focusFor("catalog-retry"))
            TvButton(text = "Ajustes de catálogo", onClick = onOpenSettings, horizontalPadding = 20.dp)
        }
    }
}
