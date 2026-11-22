package com.opentv.homenova.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opentv.homenova.core.MediaEntry
import com.opentv.homenova.ui.theme.LocalNovaPalette
import kotlinx.coroutines.delay

/**
 * Featured hero with a slow auto-rotation. Rotation pauses whenever the hero
 * owns focus; the arrow buttons let a D-pad user browse manually.
 */
@Composable
fun FeaturedHero(
    items: List<MediaEntry>,
    onPlay: (MediaEntry) -> Unit,
    onDetails: (MediaEntry) -> Unit,
    modifier: Modifier = Modifier,
    initialFocusRequester: FocusRequester? = null,
) {
    if (items.isEmpty()) return
    val palette = LocalNovaPalette.current
    var index by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    val safeIndex = index.coerceIn(items.indices)
    val current = items[safeIndex]

    LaunchedEffect(paused, items.size) {
        if (paused || items.size < 2) return@LaunchedEffect
        while (true) {
            delay(9_000)
            index = (index + 1) % items.size
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(312.dp)
            .clip(RoundedCornerShape(26.dp))
            .onFocusChanged { paused = it.hasFocus },
    ) {
        Crossfade(targetState = current, animationSpec = tween(420), label = "hero") { entry ->
            Box(Modifier.fillMaxSize()) {
                PosterArt(
                    seed = entry.paletteSeed,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xF20A0B0D),
                                    Color(0xC80A0B0D),
                                    Color.Transparent,
                                ),
                                startX = 0f,
                                endX = 1200f,
                            ),
                        ),
                )
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 42.dp, end = 34.dp, top = 34.dp, bottom = 30.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(0.62f),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            entry.badge?.let { Pill(text = it, filled = true) }
                            Pill(text = "ORIGINAL", color = palette.accentSecondary)
                        }
                        Text(
                            text = entry.title,
                            style = MaterialTheme.typography.displayLarge.copy(fontSize = 40.sp),
                            color = palette.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = entry.year.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.textSecondary,
                            )
                            Text("·", color = palette.textMuted)
                            Pill(text = entry.rating, color = palette.textSecondary)
                            Text("·", color = palette.textMuted)
                            Text(
                                text = if (entry.isLive) "EN VIVO" else "${entry.durationMinutes} min",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (entry.isLive) palette.danger else palette.textSecondary,
                            )
                            Text("·", color = palette.textMuted)
                            Text(
                                text = entry.genres.joinToString(" / "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.textSecondary,
                                maxLines = 1,
                            )
                        }
                        Text(
                            text = entry.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.78f),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(600.dp),
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            NovaButton(
                                label = "Reproducir",
                                icon = Icons.Rounded.PlayArrow,
                                primary = true,
                                onSelect = { onPlay(entry) },
                                modifier = if (initialFocusRequester != null) {
                                    Modifier.focusRequester(initialFocusRequester)
                                } else {
                                    Modifier
                                },
                            )
                            NovaButton(
                                label = "Más información",
                                icon = Icons.Rounded.Info,
                                primary = false,
                                onSelect = { onDetails(entry) },
                            )
                        }
                    }
                }

                // Manual paging, anchored to the bottom-right corner.
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 30.dp, bottom = 28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (items.size > 1) {
                        Row(
                            modifier = Modifier.padding(end = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            items.indices.forEach { dot ->
                                Box(
                                    Modifier
                                        .width(if (dot == safeIndex) 20.dp else 6.dp)
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(
                                            if (dot == safeIndex) palette.accent
                                            else Color.White.copy(alpha = 0.28f),
                                        ),
                                )
                            }
                        }
                        IconAction(
                            icon = Icons.Rounded.ChevronLeft,
                            label = "Anterior",
                            onSelect = { index = (index - 1 + items.size) % items.size },
                            size = 38.dp,
                        )
                        IconAction(
                            icon = Icons.Rounded.ChevronRight,
                            label = "Siguiente",
                            onSelect = { index = (index + 1) % items.size },
                            size = 38.dp,
                        )
                    }
                }
            }
        }
    }
}
