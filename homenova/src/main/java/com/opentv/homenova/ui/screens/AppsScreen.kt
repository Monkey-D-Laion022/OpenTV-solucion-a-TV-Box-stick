package com.opentv.homenova.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opentv.homenova.core.AppEntry
import com.opentv.homenova.ui.components.AppGridTile
import com.opentv.homenova.ui.components.DialogAction
import com.opentv.homenova.ui.components.EmptyState
import com.opentv.homenova.ui.components.FocusableCard
import com.opentv.homenova.ui.components.IconAction
import com.opentv.homenova.ui.components.NovaDialog
import com.opentv.homenova.ui.theme.LocalNovaPalette

private enum class AppFilter(val label: String) {
    ALL("Todas"),
    FAVOURITES("Favoritas"),
    GAMES("Juegos"),
    SYSTEM("Sistema"),
}

private enum class AppSort(val label: String) {
    ALPHABETICAL("A–Z"),
    RECENT("Recientes"),
}

@Composable
fun AppsScreen(
    apps: List<AppEntry>,
    favourites: Set<String>,
    hiddenApps: Set<String>,
    gridColumns: Int,
    onLaunch: (AppEntry) -> Unit,
    onToggleFavourite: (AppEntry) -> Unit,
    onToggleHidden: (AppEntry) -> Unit,
    onOpenInfo: (AppEntry) -> Unit,
    onUninstall: (AppEntry) -> Unit,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalNovaPalette.current
    var filter by rememberSaveable { mutableStateOf(AppFilter.ALL.name) }
    var sort by rememberSaveable { mutableStateOf(AppSort.ALPHABETICAL.name) }
    var selected by remember { mutableStateOf<AppEntry?>(null) }
    val activeFilter = AppFilter.valueOf(filter)
    val activeSort = AppSort.valueOf(sort)

    val visible = remember(apps, activeFilter, activeSort, favourites) {
        val filtered = when (activeFilter) {
            AppFilter.ALL -> apps
            AppFilter.FAVOURITES -> apps.filter { it.packageName in favourites }
            AppFilter.GAMES -> apps.filter { it.isGame }
            AppFilter.SYSTEM -> apps.filter { it.isSystem }
        }
        when (activeSort) {
            AppSort.ALPHABETICAL -> filtered.sortedBy { it.label.lowercase() }
            AppSort.RECENT -> filtered.sortedByDescending { it.updatedAt }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 40.dp, end = 40.dp, top = 2.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Aplicaciones",
                    style = MaterialTheme.typography.displayMedium,
                    color = palette.textPrimary,
                )
                Text(
                    text = "${apps.size} apps instaladas · ${favourites.size} favoritas",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary,
                )
            }
            IconAction(
                icon = Icons.Rounded.Search,
                label = "Buscar aplicaciones",
                onSelect = onOpenSearch,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppFilter.entries.forEach { option ->
                FilterChip(
                    label = option.label,
                    selected = activeFilter == option,
                    onSelect = { filter = option.name },
                )
            }
            Spacer(Modifier.weight(1f))
            FilterChip(
                label = "Orden: ${activeSort.label}",
                selected = false,
                onSelect = {
                    sort = if (activeSort == AppSort.ALPHABETICAL) {
                        AppSort.RECENT.name
                    } else {
                        AppSort.ALPHABETICAL.name
                    }
                },
            )
        }

        Spacer(Modifier.height(18.dp))

        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Rounded.Apps,
                    title = "No hay aplicaciones en este filtro",
                    body = "Prueba con otro filtro o instala nuevas apps desde la tienda del dispositivo.",
                    actionLabel = "Ver todas",
                    onAction = { filter = AppFilter.ALL.name },
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns.coerceIn(4, 8)),
                state = rememberLazyGridState(),
                contentPadding = PaddingValues(start = 40.dp, end = 40.dp, bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(visible, key = { it.packageName }) { app ->
                    AppGridTile(
                        app = app,
                        isFavourite = app.packageName in favourites,
                        isHidden = app.packageName in hiddenApps,
                        onSelect = { onLaunch(app) },
                        onLongSelect = { selected = app },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    selected?.let { app ->
        NovaDialog(
            title = app.label,
            message = app.packageName,
            onDismiss = { selected = null },
            actions = listOf(
                DialogAction(
                    label = "Abrir",
                    icon = Icons.Rounded.PlayArrow,
                    primary = true,
                    onClick = {
                        selected = null
                        onLaunch(app)
                    },
                ),
                DialogAction(
                    label = if (app.packageName in favourites) "Quitar de favoritas" else "Añadir a favoritas",
                    icon = if (app.packageName in favourites) Icons.Rounded.StarBorder else Icons.Rounded.Star,
                    onClick = {
                        selected = null
                        onToggleFavourite(app)
                    },
                ),
                DialogAction(
                    label = if (app.packageName in hiddenApps) "Mostrar en Aplicaciones" else "Ocultar de Aplicaciones",
                    icon = Icons.Rounded.VisibilityOff,
                    onClick = {
                        selected = null
                        onToggleHidden(app)
                    },
                ),
                DialogAction(
                    label = "Información de la app",
                    icon = Icons.Rounded.Info,
                    onClick = {
                        selected = null
                        onOpenInfo(app)
                    },
                ),
                DialogAction(
                    label = "Desinstalar",
                    icon = Icons.Rounded.Delete,
                    destructive = true,
                    onClick = {
                        selected = null
                        onUninstall(app)
                    },
                ),
            ),
        )
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val palette = LocalNovaPalette.current
    FocusableCard(
        onSelect = onSelect,
        shape = RoundedCornerShape(50),
        selected = selected,
        focusedScale = 1.06f,
        background = if (selected) {
            palette.accent.copy(alpha = 0.18f)
        } else {
            Color.White.copy(alpha = 0.055f)
        },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            color = if (selected) palette.accent else palette.textSecondary,
            modifier = Modifier.padding(horizontal = 17.dp, vertical = 9.dp),
        )
    }
}
