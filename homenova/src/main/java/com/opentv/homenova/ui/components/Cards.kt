package com.opentv.homenova.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opentv.homenova.core.AppEntry
import com.opentv.homenova.core.MediaEntry
import com.opentv.homenova.ui.theme.NovaInk
import com.opentv.homenova.ui.theme.LocalNovaPalette

@Composable
fun MediaCard(
    entry: MediaEntry,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    onLongSelect: (() -> Unit)? = null,
    width: Dp = 286.dp,
    showSubtitle: Boolean = true,
) {
    val palette = LocalNovaPalette.current
    FocusableCard(
        onSelect = onSelect,
        onLongSelect = onLongSelect,
        modifier = modifier.width(width),
        shape = RoundedCornerShape(18.dp),
        background = palette.surface,
    ) {
        Column {
            Box {
                PosterArt(
                    seed = entry.paletteSeed,
                    badge = entry.badge,
                    live = entry.isLive,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                )
                if (entry.progress > 0f) {
                    ProgressLine(
                        progress = entry.progress,
                        height = 4.dp,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 13.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (showSubtitle) {
                    Text(
                        text = if (entry.isLive) entry.liveProgram ?: entry.subtitle else entry.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (entry.isLive) palette.danger else palette.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun AppIconTile(
    app: AppEntry,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    onLongSelect: (() -> Unit)? = null,
    isFavourite: Boolean = false,
    size: Dp = 96.dp,
) {
    val palette = LocalNovaPalette.current
    FocusableCard(
        onSelect = onSelect,
        onLongSelect = onLongSelect,
        modifier = modifier.width(size + 22.dp),
        shape = RoundedCornerShape(16.dp),
        focusedScale = 1.09f,
        background = Color.Transparent,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(6.dp),
        ) {
            Box(contentAlignment = Alignment.TopEnd) {
                AppIcon(app = app, size = size)
                if (isFavourite) {
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp, end = 4.dp)
                            .size(20.dp)
                            .clip(RoundedCornerShape(50))
                            .background(NovaInk.copy(alpha = 0.72f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = "Favorita",
                            tint = palette.accent,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(7.dp))
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(size + 12.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
fun AppGridTile(
    app: AppEntry,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    onLongSelect: (() -> Unit)? = null,
    isFavourite: Boolean = false,
    isHidden: Boolean = false,
) {
    val palette = LocalNovaPalette.current
    FocusableCard(
        onSelect = onSelect,
        onLongSelect = onLongSelect,
        modifier = modifier.height(142.dp),
        shape = RoundedCornerShape(18.dp),
        focusedScale = 1.045f,
        background = palette.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box {
                AppIcon(app = app, size = 62.dp)
                if (isFavourite) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = "Favorita",
                        tint = palette.accent,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(14.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = if (isHidden) palette.textMuted else palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            if (app.isGame) {
                Text(
                    text = "Juego",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.sp),
                    color = palette.textMuted,
                )
            }
        }
    }
}

@Composable
fun AppIcon(app: AppEntry, size: Dp) {
    val palette = LocalNovaPalette.current
    val bitmap = app.icon
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = app.label,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.22f)),
        )
    } else {
        val hue = ((app.label.hashCode() ushr 1) % 360).toFloat()
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.22f))
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color.hsv(hue, 0.55f, 0.72f),
                            Color.hsv((hue + 55f) % 360f, 0.65f, 0.36f),
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = app.label.take(1).uppercase(),
                color = Color.White,
                style = MaterialTheme.typography.displaySmall.copy(fontSize = (size.value * 0.42f).sp),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
