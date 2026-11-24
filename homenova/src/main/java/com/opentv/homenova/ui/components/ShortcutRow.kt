package com.opentv.homenova.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import com.opentv.homenova.ui.theme.LocalNovaPalette

/** Una tarjeta de acceso rápido (funciones de OpenTV o pantallas del sistema). */
data class ShortcutItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onSelect: () -> Unit,
)

/**
 * Fila horizontal de accesos rápidos. Usa la misma tarjeta enfocable que el resto del Home 2: con control se navega con el D-pad y con la
 * pantalla táctil basta UN toque.
 */
@Composable
fun ShortcutRow(
    title: String,
    items: List<ShortcutItem>,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    if (items.isEmpty()) return
    val palette = LocalNovaPalette.current
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            title = title,
            subtitle = subtitle,
            modifier = Modifier.padding(start = 40.dp, end = 48.dp, bottom = 10.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(start = 40.dp, end = 48.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { it.title }) { item ->
                FocusableCard(
                    onSelect = item.onSelect,
                    modifier = Modifier
                        .width(210.dp)
                        .height(92.dp),
                    shape = RoundedCornerShape(18.dp),
                    background = palette.surface,
                ) { focused ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = if (focused) palette.accent else palette.textSecondary,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = item.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
