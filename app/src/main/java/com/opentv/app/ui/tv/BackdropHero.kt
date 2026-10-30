package com.opentv.app.ui.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.opentv.app.ui.theme.OpenTvColors

/** Lo que muestra el banner. [tag] es una etiqueta corta (por ejemplo «Solo información · TMDB»). */
data class HeroContent(val title: String, val text: String, val tag: String? = null)

/**
 * Banner cinematográfico. Con [backdrop] muestra la imagen a toda la superficie con degradados para que el texto se lea;
 * sin imagen (o mientras carga) usa un fondo de degradado decorativo dibujado, sin ninguna imagen ni contenido inventado.
 * La altura la fija quien lo usa ([TvLayout]) para que lo de abajo no salte al navegar.
 */
@Composable
fun BackdropHero(
    content: HeroContent,
    heightDp: Int,
    bodyLines: Int,
    modifier: Modifier = Modifier,
    backdrop: ImageBitmap? = null,
) {
    val imageAlpha by animateFloatAsState(
        targetValue = if (backdrop != null) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "heroBackdrop",
    )
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .clip(shape)
            .background(OpenTvColors.Surface)
            .drawBehind {
                // Fondo decorativo: resplandor turquesa suave arriba a la derecha.
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(OpenTvColors.Primary.copy(alpha = 0.30f), Color.Transparent),
                        center = Offset(size.width * 0.85f, size.height * 0.1f),
                        radius = size.maxDimension * 0.7f,
                    ),
                )
            },
    ) {
        if (backdrop != null) {
            Image(
                bitmap = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(imageAlpha),
            )
        }
        // Degradado lateral (legibilidad del texto) y de abajo (continuidad con la fila siguiente).
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    colors = listOf(OpenTvColors.Background.copy(alpha = 0.92f), OpenTvColors.Background.copy(alpha = 0.55f), Color.Transparent),
                ),
            ),
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, OpenTvColors.Background.copy(alpha = 0.65f)),
                ),
            ),
        )
        Column(
            modifier = Modifier.fillMaxHeight().fillMaxWidth(0.62f).padding(horizontal = 26.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            if (content.tag != null) {
                Text(
                    text = content.tag,
                    style = MaterialTheme.typography.labelLarge,
                    color = OpenTvColors.Primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = content.title,
                style = MaterialTheme.typography.headlineMedium,
                color = OpenTvColors.OnBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = content.text,
                style = MaterialTheme.typography.bodyLarge,
                color = OpenTvColors.OnBackground.copy(alpha = 0.85f),
                maxLines = bodyLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
