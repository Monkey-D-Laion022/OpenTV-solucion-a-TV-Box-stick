package com.opentv.app.ui.connect

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.opentv.app.ui.components.FocusableSurface
import com.opentv.app.ui.theme.OpenTvColors

/** Ícono de «transmitir» (pantalla con ondas) dibujado a mano: no hace falta ninguna librería de íconos. */
@Composable
fun CastIcon(modifier: Modifier = Modifier, size: Dp = 26.dp, tint: Color = OpenTvColors.OnBackground) {
    Canvas(modifier = modifier.size(size)) {
        val u = this.size.width / 24f
        val stroke = Stroke(width = 2f * u, cap = StrokeCap.Butt)

        val screen = Path().apply {
            moveTo(2f * u, 8f * u)
            lineTo(2f * u, 4f * u)
            lineTo(22f * u, 4f * u)
            lineTo(22f * u, 20f * u)
            lineTo(14f * u, 20f * u)
        }
        drawPath(screen, tint, style = stroke)

        val cx = 2f * u
        val cy = 22f * u
        listOf(5f, 9f, 13f).forEach { r ->
            drawArc(
                color = tint,
                startAngle = -90f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(cx - r * u, cy - r * u),
                size = Size(2f * r * u, 2f * r * u),
                style = stroke,
            )
        }
        drawCircle(tint, radius = 1.8f * u, center = Offset(cx + 0.5f * u, cy - 0.5f * u))
    }
}

/** Botón «Transmitir a TV» con el ícono de Cast; abre el selector propio de OpenTV. */
@Composable
fun CastButton(onClick: () -> Unit, modifier: Modifier = Modifier, label: String = "Transmitir a TV") {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(28.dp),
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CastIcon()
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = OpenTvColors.OnBackground,
            )
        }
    }
}
