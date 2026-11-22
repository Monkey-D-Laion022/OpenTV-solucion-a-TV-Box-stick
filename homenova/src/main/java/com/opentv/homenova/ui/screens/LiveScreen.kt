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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.opentv.homenova.core.MediaEntry
import com.opentv.homenova.ui.components.ClockDisplay
import com.opentv.homenova.ui.components.FocusableCard
import com.opentv.homenova.ui.components.Pill
import com.opentv.homenova.ui.components.PosterArt
import com.opentv.homenova.ui.theme.LocalNovaPalette

@Composable
fun LiveScreen(
    channels: List<MediaEntry>,
    onPlay: (MediaEntry) -> Unit,
    clock24h: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = LocalNovaPalette.current
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 40.dp, end = 40.dp, top = 2.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier
                            .width(8.dp)
                            .height(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(palette.danger),
                    )
                    Text(
                        text = "En vivo",
                        style = MaterialTheme.typography.displayMedium,
                        color = palette.textPrimary,
                    )
                }
                Text(
                    text = "${channels.size} canales · guía de programación simulada",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary,
                )
            }
            ClockDisplay(clock24h = clock24h)
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = rememberLazyGridState(),
            contentPadding = PaddingValues(start = 40.dp, end = 40.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(channels, key = { it.id }) { channel ->
                LiveChannelCard(entry = channel, onSelect = { onPlay(channel) })
            }
        }
    }
}

@Composable
private fun LiveChannelCard(entry: MediaEntry, onSelect: () -> Unit) {
    val palette = LocalNovaPalette.current
    FocusableCard(
        onSelect = onSelect,
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp),
        shape = RoundedCornerShape(18.dp),
        focusedScale = 1.04f,
        background = palette.surface,
    ) {
        Box(Modifier.fillMaxSize()) {
            PosterArt(
                seed = entry.paletteSeed,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xCC08090B)),
                            startY = 60f,
                        ),
                    ),
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Pill(text = "EN VIVO", color = palette.danger, filled = true)
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = entry.liveProgram ?: "Programación en directo",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = entry.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Ver ahora",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.textPrimary,
                )
            }
        }
    }
}
