package com.opentv.home

import android.os.SystemClock
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.tv.material3.ButtonColors
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.CardBorder
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.CardScale
import androidx.tv.material3.CardShape
import androidx.tv.material3.ExperimentalTvMaterial3Api

/**
 * Soporte TÁCTIL para Home v3 sin tocar su lógica: los `Button` y `Card` de tv-material responden al D-Pad/control; estas versiones con el mismo
 * nombre y parámetros añaden toque y toque largo (`detectTapGestures`) encima. Un guardián de 350 ms evita que una misma pulsación dispare la
 * acción dos veces si el componente de tv-material también la procesa. El desplazamiento táctil de las listas ya funciona por sí solo.
 */
private class TapGuard {
    private var last = 0L
    fun run(action: () -> Unit) {
        val now = SystemClock.uptimeMillis()
        if (now - last < 350L) return
        last = now
        action()
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: ButtonColors = ButtonDefaults.colors(),
    content: @Composable RowScope.() -> Unit,
) {
    val guard = remember { TapGuard() }
    androidx.tv.material3.Button(
        onClick = { guard.run(onClick) },
        modifier = modifier.pointerInput(onClick) { detectTapGestures(onTap = { guard.run(onClick) }) },
        colors = colors,
        content = content,
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun Card(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    shape: CardShape = CardDefaults.shape(),
    scale: CardScale = CardDefaults.scale(),
    border: CardBorder = CardDefaults.border(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val tapGuard = remember { TapGuard() }
    val longGuard = remember { TapGuard() }
    val onLong: ((Offset) -> Unit)? = onLongClick?.let { l -> { _: Offset -> longGuard.run(l) } }
    androidx.tv.material3.Card(
        onClick = { tapGuard.run(onClick) },
        onLongClick = onLongClick?.let { l -> { longGuard.run(l) } },
        shape = shape,
        scale = scale,
        border = border,
        modifier = modifier.pointerInput(onClick, onLongClick) {
            detectTapGestures(
                onTap = { tapGuard.run(onClick) },
                onLongPress = onLong,
            )
        },
        content = content,
    )
}
