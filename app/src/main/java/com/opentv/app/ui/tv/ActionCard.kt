package com.opentv.app.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.opentv.app.ui.components.FocusableSurface
import com.opentv.app.ui.components.PendingBadge
import com.opentv.app.ui.theme.OpenTvColors

/**
 * Tarjeta de acción de las pantallas rediseñadas (reemplaza a `TvCard` en ellas). Tamaño explícito calculado por
 * [TvLayout]; título y subtítulo con 2 líneas como máximo y puntos suspensivos, para que nada se corte.
 */
@Composable
fun ActionCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    badge: String? = null,
    accent: Color = OpenTvColors.Primary,
    onFocus: (() -> Unit)? = null,
) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.size(width, height),
        shape = RoundedCornerShape(18.dp),
        onFocusGained = onFocus,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.width(36.dp).height(6.dp).background(accent, RoundedCornerShape(3.dp)))
                if (badge != null) PendingBadge(badge)
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = OpenTvColors.OnBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OpenTvColors.Muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
