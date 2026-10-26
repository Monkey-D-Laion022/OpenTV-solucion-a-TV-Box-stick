package com.opentv.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.opentv.app.ui.theme.OpenTvColors

/** Tarjeta 16:9 del inicio. Si [badge] no es nulo, la función aún no existe y se indica. */
@Composable
fun TvCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    accent: Color = OpenTvColors.Primary,
    onFocus: (() -> Unit)? = null,
) {
    FocusableSurface(onClick = onClick, modifier = modifier.aspectRatio(16f / 9f), onFocusGained = onFocus) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(6.dp)
                        .background(accent, RoundedCornerShape(3.dp)),
                )
                if (badge != null) PendingBadge(badge)
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = OpenTvColors.OnBackground,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OpenTvColors.Muted,
                )
            }
        }
    }
}

@Composable
fun PendingBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = OpenTvColors.Warning,
        modifier = modifier
            .background(OpenTvColors.Warning.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 32.dp,
) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(28.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = OpenTvColors.OnBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = horizontalPadding),
        )
    }
}

/** Fila de lista; con [onClick] nulo solo recibe el foco para poder desplazarse con las flechas. */
@Composable
fun TvListItem(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        focusedScale = 1.02f,
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = OpenTvColors.OnBackground,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OpenTvColors.Muted,
                )
            }
        }
    }
}

/** Fila etiqueta/valor, enfocable para poder recorrer la lista con las flechas. */
@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    FocusableSurface(
        onClick = null,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        focusedScale = 1.01f,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = OpenTvColors.Muted,
                modifier = Modifier.width(260.dp),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = OpenTvColors.OnBackground,
                modifier = Modifier.weight(1f, fill = true),
            )
        }
    }
}

@Composable
fun StatusChip(text: String, dotColor: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(OpenTvColors.Surface, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(dotColor, CircleShape),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = OpenTvColors.OnBackground,
        )
    }
}
