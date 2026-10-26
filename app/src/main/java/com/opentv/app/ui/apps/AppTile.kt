package com.opentv.app.ui.apps

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.opentv.app.apps.InstalledApp
import com.opentv.app.ui.components.FocusableSurface
import com.opentv.app.ui.theme.OpenTvColors

/**
 * Tarjeta de una app: ícono real (cargado bajo demanda) y nombre.
 *
 * Abrir: Enter / toque. Marcar como favorita (★): pulsación prolongada, o las teclas Menú / F.
 */
@Composable
fun AppTile(
    app: InstalledApp,
    isFavorite: Boolean,
    loadIcon: suspend (InstalledApp) -> Bitmap?,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    onFocus: (() -> Unit)? = null,
) {
    val icon: ImageBitmap? by produceState<ImageBitmap?>(initialValue = null, key1 = app.packageName) {
        value = loadIcon(app)?.asImageBitmap()
    }

    FocusableSurface(
        onClick = onClick,
        onLongClick = onToggleFavorite,
        onFocusGained = onFocus,
        modifier = modifier.onKeyEvent { event ->
            val isFavoriteKey = event.key == Key.Menu || event.key == Key.F
            if (isFavoriteKey && event.type == KeyEventType.KeyUp) {
                onToggleFavorite()
                true
            } else {
                false
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(modifier = Modifier.size(72.dp)) {
                icon?.let { bitmap ->
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyMedium,
                color = OpenTvColors.OnBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (isFavorite) {
            Text(
                text = "★",
                style = MaterialTheme.typography.titleMedium,
                color = OpenTvColors.Warning,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            )
        }
    }
}
