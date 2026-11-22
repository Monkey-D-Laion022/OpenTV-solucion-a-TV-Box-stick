package com.opentv.homenova.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.view.SoundEffectConstants
import com.opentv.homenova.ui.theme.LocalDpadSounds
import com.opentv.homenova.ui.theme.LocalNovaPalette

/**
 * D-pad-first clickable modifier.
 *
 * Center/Enter activates on key-up; a hold longer than [longPressMillis] fires
 * the long action. MENU is a second, discoverable shortcut used by TV remotes
 * for contextual actions. Touch input still works through detectTapGestures.
 */
fun Modifier.tvFocusable(
    onSelect: () -> Unit,
    onLongSelect: (() -> Unit)? = null,
    enabled: Boolean = true,
    longPressMillis: Long = 550L,
): Modifier = composed {
    var pressedAt by remember { mutableLongStateOf(0L) }

    this
        // Key handlers must sit above the focus target in the modifier chain so
        // they receive events bubbling up from the focused node.
        .onKeyEvent { event ->
            if (!enabled) return@onKeyEvent false
            when (event.key) {
                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                    if (event.type == KeyEventType.KeyDown) {
                        // A held key auto-repeats KeyDown; only the first one starts the
                        // timer, otherwise a long press could never reach the threshold.
                        if (pressedAt == 0L || event.nativeKeyEvent.repeatCount == 0) {
                            pressedAt = System.currentTimeMillis()
                        }
                        true
                    } else {
                        val startedAt = pressedAt
                        pressedAt = 0L
                        // KeyUp without a matching KeyDown on this node (focus arrived
                        // mid-press, e.g. right after a dialog opened): ignore it.
                        if (startedAt != 0L) {
                            val isLong = onLongSelect != null &&
                                System.currentTimeMillis() - startedAt >= longPressMillis
                            if (isLong) onLongSelect?.invoke() else onSelect()
                        }
                        true
                    }
                }
                Key.Menu -> {
                    if (event.type == KeyEventType.KeyDown && onLongSelect != null) {
                        onLongSelect()
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }
        .focusable(enabled = enabled)
        .semantics {
            onClick(label = "Seleccionar") {
                if (enabled) onSelect()
                true
            }
        }
        .pointerInput(enabled, onSelect, onLongSelect) {
            if (!enabled) return@pointerInput
            detectTapGestures(
                onTap = { onSelect() },
                onLongPress = { onLongSelect?.invoke() },
            )
        }
}

/**
 * Card container with the launcher's focus language: slight scale, elevated
 * shadow tinted with the accent, a two-pixel ring and a brighter surface.
 */
@Composable
fun FocusableCard(
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    onLongSelect: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(18.dp),
    focusedScale: Float = 1.055f,
    focusedElevation: Dp = 18.dp,
    selected: Boolean = false,
    enabled: Boolean = true,
    background: Color = LocalNovaPalette.current.surface,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val palette = LocalNovaPalette.current
    val view = LocalView.current
    val soundsEnabled = LocalDpadSounds.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused && enabled) focusedScale else 1f,
        animationSpec = tween(170),
        label = "cardScale",
    )
    val elevation by animateDpAsState(
        targetValue = if (focused && enabled) focusedElevation else 0.dp,
        animationSpec = tween(170),
        label = "cardElevation",
    )
    val ringWidth by animateDpAsState(
        targetValue = if (focused || selected) 2.dp else 0.dp,
        animationSpec = tween(150),
        label = "ringWidth",
    )
    val tint by androidx.compose.animation.animateColorAsState(
        targetValue = if (focused) palette.accent else if (selected) palette.accent.copy(alpha = 0.65f) else Color.Transparent,
        animationSpec = tween(150),
        label = "ringTint",
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = palette.accent.copy(alpha = 0.55f),
                spotColor = palette.accent.copy(alpha = 0.4f),
            )
            .clip(shape)
            .background(if (focused) background.copy(alpha = 0.98f) else background)
            .border(ringWidth, tint, shape)
            .onFocusChanged { state ->
                if (state.isFocused && !focused && soundsEnabled) {
                    runCatching { view.playSoundEffect(SoundEffectConstants.NAVIGATION_UP) }
                }
                focused = state.hasFocus
            }
            .tvFocusable(onSelect = onSelect, onLongSelect = onLongSelect, enabled = enabled),
        content = { content(focused) },
    )
}
