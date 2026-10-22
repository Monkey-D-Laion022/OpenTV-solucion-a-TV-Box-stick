package com.opentv.app.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.opentv.app.apps.AppListLogic
import com.opentv.app.apps.AppsViewModel
import com.opentv.app.ui.components.SafeArea
import com.opentv.app.ui.components.SearchField
import com.opentv.app.ui.components.TvButton
import com.opentv.app.ui.theme.OpenTvColors

@Composable
fun AppsScreen(
    onBack: () -> Unit,
    viewModel: AppsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val launchState by viewModel.appLaunch.state.collectAsStateWithLifecycle()

    // Se vuelve a leer la lista cada vez que la pantalla se hace visible (apps nuevas o borradas).
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.refresh() }

    val visibleApps = remember(state.allApps, state.query, state.sortMode, state.favorites) {
        AppListLogic.apply(state.allApps, state.query, state.sortMode, state.favorites)
    }

    val firstTileFocus = remember { FocusRequester() }
    LaunchedEffect(state.isLoading) {
        if (!state.isLoading && visibleApps.isNotEmpty()) {
            // Los ítems de una grilla perezosa se miden después de componer: se espera un cuadro.
            withFrameNanos { }
            runCatching { firstTileFocus.requestFocus() }
        }
    }

    launchState.pendingWarning?.let { app ->
        PortraitWarningDialog(
            app = app,
            onConfirm = viewModel.appLaunch::confirmWarning,
            onDismiss = viewModel.appLaunch::dismissWarning,
        )
    }

    SafeArea {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Aplicaciones",
                style = MaterialTheme.typography.headlineMedium,
                color = OpenTvColors.OnBackground,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = "Buscar aplicación",
                    modifier = Modifier.weight(1f),
                )
                TvButton(
                    text = "Orden",
                    onClick = viewModel::cycleSortMode,
                    horizontalPadding = 20.dp,
                )
                TvButton(
                    text = "Actualizar",
                    onClick = viewModel::refresh,
                    horizontalPadding = 20.dp,
                )
                TvButton(text = "Volver", onClick = onBack, horizontalPadding = 20.dp)
            }

            Text(
                text = "${visibleApps.size} apps · Orden: ${state.sortMode.label} · " +
                    "Mantené pulsada una app (o Menú/F) para marcarla ★ favorita",
                style = MaterialTheme.typography.bodyMedium,
                color = OpenTvColors.Muted,
            )
            LaunchMessage(state = launchState)

            when {
                state.isLoading && state.allApps.isEmpty() -> StatusText("Cargando aplicaciones…")
                state.loadError != null -> StatusText(state.loadError.orEmpty())
                state.allApps.isEmpty() ->
                    StatusText("No se encontraron aplicaciones que se puedan abrir.")
                visibleApps.isEmpty() -> StatusText("Sin resultados para «${state.query.trim()}».")
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    itemsIndexed(visibleApps, key = { _, app -> app.packageName }) { index, app ->
                        AppTile(
                            app = app,
                            isFavorite = app.packageName in state.favorites,
                            loadIcon = viewModel.iconLoader::load,
                            onClick = { viewModel.appLaunch.onAppClick(app) },
                            onToggleFavorite = { viewModel.toggleFavorite(app) },
                            modifier = Modifier
                                .height(148.dp)
                                .then(
                                    if (index == 0) Modifier.focusRequester(firstTileFocus) else Modifier,
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = OpenTvColors.Muted,
        modifier = Modifier.padding(top = 8.dp),
    )
}
