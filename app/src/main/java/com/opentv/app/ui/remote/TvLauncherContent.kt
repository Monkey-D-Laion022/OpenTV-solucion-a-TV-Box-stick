package com.opentv.app.ui.remote

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.opentv.app.apps.InstalledApp
import com.opentv.app.ui.theme.OpenTvColors
import com.opentv.app.ui.theme.OpenTvTheme
import kotlinx.coroutines.flow.StateFlow

/**
 * La interfaz que se ve en el TV (1280×720 dp, 16:9). No usa foco de Android: el cursor lo mueve el
 * control remoto del teléfono, así se comporta igual aunque el TV no tenga ninguna entrada.
 */
@Composable
fun TvLauncherContent(
    state: StateFlow<TvUiState>,
    loadIcon: suspend (InstalledApp) -> Bitmap?,
) {
    val ui by state.collectAsState()
    val listState = rememberLazyListState()
    LaunchedEffect(ui.cursor.row, ui.cursor.col) {
        if (ui.cursor.row == 0 && ui.apps.isNotEmpty()) {
            runCatching { listState.animateScrollToItem(ui.cursor.col.coerceIn(0, ui.apps.lastIndex)) }
        }
    }

    OpenTvTheme {
        Box(modifier = Modifier.fillMaxSize().background(OpenTvColors.Background)) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 64.dp, vertical = 48.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                Text("OpenTV", style = MaterialTheme.typography.displaySmall, color = OpenTvColors.OnBackground)

                Text("Aplicaciones", style = MaterialTheme.typography.headlineSmall, color = OpenTvColors.OnBackground)
                if (ui.apps.isEmpty()) {
                    Text(
                        text = "Todavía no tenés apps en la fila. Agregalas con «+» en el inicio del teléfono.",
                        style = MaterialTheme.typography.titleMedium,
                        color = OpenTvColors.Muted,
                    )
                } else {
                    LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        itemsIndexed(ui.apps, key = { _, app -> app.packageName }) { index, app ->
                            TvTile(
                                selected = ui.cursor.row == 0 && ui.cursor.col == index,
                                label = app.label,
                                loadIcon = { loadIcon(app) },
                                iconKey = app.packageName,
                            )
                        }
                    }
                }

                Text("Opciones", style = MaterialTheme.typography.headlineSmall, color = OpenTvColors.OnBackground)
                TvTile(
                    selected = ui.cursor.row == 1,
                    label = "Detener proyección",
                    loadIcon = { null },
                    iconKey = "stop",
                )

                ui.notice?.let {
                    Text(it, style = MaterialTheme.typography.titleLarge, color = OpenTvColors.Warning)
                }
            }
        }
    }
}

@Composable
private fun TvTile(selected: Boolean, label: String, loadIcon: suspend () -> Bitmap?, iconKey: String) {
    val icon: ImageBitmap? by produceState<ImageBitmap?>(initialValue = null, key1 = iconKey) {
        value = loadIcon()?.asImageBitmap()
    }
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .size(width = 220.dp, height = 180.dp)
            .scale(if (selected) 1.08f else 1f)
            .background(if (selected) OpenTvColors.SurfaceFocused else OpenTvColors.Surface, shape)
            .then(if (selected) Modifier.border(4.dp, OpenTvColors.Primary, shape) else Modifier)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.size(84.dp)) }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = OpenTvColors.OnBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
