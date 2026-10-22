package com.opentv.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.opentv.app.ui.theme.OpenTvColors

/**
 * Base de todo elemento seleccionable con control remoto, teclado o toque.
 *
 * Con [onClick] responde a Enter / centro del D-pad / toque; [onLongClick] añade la pulsación
 * prolongada. Sin [onClick] solo recibe el foco (útil para filas de información que se desplazan
 * con las flechas).
 * El estado de foco es siempre visible: borde turquesa, fondo más claro y ligera ampliación.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FocusableSurface(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    focusedScale: Float = 1.04f,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) focusedScale else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "focusScale",
    )

    // onFocusChanged debe ir ANTES (a la izquierda) del nodo que recibe el foco.
    val base = modifier
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clip(shape)
        .background(if (focused) OpenTvColors.SurfaceFocused else OpenTvColors.Surface)
        .then(if (focused) Modifier.border(3.dp, OpenTvColors.Primary, shape) else Modifier)
        .onFocusChanged { focused = it.isFocused }

    val interactive = when {
        onClick == null -> base.focusable()
        onLongClick != null -> base.combinedClickable(onLongClick = onLongClick, onClick = onClick)
        else -> base.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        )
    }

    Box(modifier = interactive) { content(focused) }
}
