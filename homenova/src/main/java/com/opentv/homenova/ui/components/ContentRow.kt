package com.opentv.homenova.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opentv.homenova.core.AppEntry
import com.opentv.homenova.core.MediaEntry

@Composable
fun ContentRow(
    title: String,
    items: List<MediaEntry>,
    onSelect: (MediaEntry) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onLongSelect: ((MediaEntry) -> Unit)? = null,
) {
    if (items.isEmpty()) return
    val state = rememberLazyListState()
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            title = title,
            subtitle = subtitle,
            actionLabel = actionLabel,
            onAction = onAction,
            modifier = Modifier.padding(start = 40.dp, end = 48.dp, bottom = 12.dp),
        )
        LazyRow(
            state = state,
            contentPadding = PaddingValues(start = 40.dp, end = 48.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(items.size, key = { items[it].id }) { index ->
                val entry = items[index]
                MediaCard(
                    entry = entry,
                    onSelect = { onSelect(entry) },
                    onLongSelect = onLongSelect?.let { { it(entry) } },
                )
            }
        }
    }
}

@Composable
fun AppRow(
    title: String,
    apps: List<AppEntry>,
    onSelect: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
    favourites: Set<String> = emptySet(),
    onLongSelect: ((AppEntry) -> Unit)? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    if (apps.isEmpty()) return
    val state = rememberLazyListState()
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            title = title,
            actionLabel = actionLabel,
            onAction = onAction,
            modifier = Modifier.padding(start = 40.dp, end = 48.dp, bottom = 10.dp),
        )
        LazyRow(
            state = state,
            contentPadding = PaddingValues(start = 40.dp, end = 48.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(apps.size, key = { apps[it].packageName }) { index ->
                val app = apps[index]
                AppIconTile(
                    app = app,
                    onSelect = { onSelect(app) },
                    onLongSelect = onLongSelect?.let { { it(app) } },
                    isFavourite = app.packageName in favourites,
                )
            }
        }
    }
}
