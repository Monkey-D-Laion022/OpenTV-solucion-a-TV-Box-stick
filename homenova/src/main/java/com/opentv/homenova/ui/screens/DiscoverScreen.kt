package com.opentv.homenova.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opentv.homenova.core.MediaEntry
import com.opentv.homenova.ui.components.ContentRow
import com.opentv.homenova.ui.theme.LocalNovaPalette

@Composable
fun DiscoverScreen(
    catalogue: List<MediaEntry>,
    onPlay: (MediaEntry) -> Unit,
    onDetails: (MediaEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalNovaPalette.current
    val state = rememberLazyListState()

    LazyColumn(
        state = state,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 42.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(key = "header") {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp),
            ) {
                Text(
                    text = "Descubrir",
                    style = MaterialTheme.typography.displayMedium,
                    color = palette.textPrimary,
                )
                Text(
                    text = "Recomendaciones, estrenos y géneros para explorar sin salir del sofá.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary,
                )
            }
        }

        item(key = "latest") {
            ContentRow(
                title = "Recién llegados",
                subtitle = "Estrenos y nuevas temporadas",
                items = catalogue.sortedByDescending { it.year }.take(10),
                onSelect = onPlay,
                onLongSelect = onDetails,
            )
        }

        item(key = "scifi") {
            ContentRow(
                title = "Ciencia ficción",
                items = catalogue.filter { "Ciencia ficción" in it.genres },
                onSelect = onPlay,
                onLongSelect = onDetails,
            )
        }

        item(key = "docs") {
            ContentRow(
                title = "Documentales que abren ventanas",
                items = catalogue.filter { "Documental" in it.genres },
                onSelect = onPlay,
                onLongSelect = onDetails,
            )
        }

        item(key = "family") {
            ContentRow(
                title = "Para toda la familia",
                items = catalogue.filter { it.rating == "TP" || it.rating == "7+" },
                onSelect = onPlay,
                onLongSelect = onDetails,
            )
        }

        item(key = "mystery") {
            ContentRow(
                title = "Misterio y suspense",
                items = catalogue.filter { "Misterio" in it.genres || "Suspense" in it.genres },
                onSelect = onPlay,
                onLongSelect = onDetails,
            )
        }

        item(key = "drama") {
            ContentRow(
                title = "Dramas para una noche larga",
                items = catalogue.filter { "Drama" in it.genres },
                onSelect = onPlay,
                onLongSelect = onDetails,
            )
        }
    }
}
