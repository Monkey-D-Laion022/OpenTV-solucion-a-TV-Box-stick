package com.opentv.app.ui.shell

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.opentv.app.ui.components.FocusableSurface
import com.opentv.app.ui.components.SafeArea
import com.opentv.app.ui.theme.OpenTvColors
import java.util.Date
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * Marco común de las cinco secciones: barra superior fija (Inicio, Aplicaciones, Multimedia, Conexiones, Ajustes), reloj y
 * engranaje. Solo navega; no ejecuta ninguna función de transmisión ni toca la resolución.
 *
 * Control remoto: abajo desde la barra entra al contenido; Atrás desde el contenido vuelve a la pestaña actual y un
 * segundo Atrás sale de la sección. Con toque (sin foco) Atrás se comporta como siempre.
 */
@Composable
fun OpenTvShell(
    selected: TopSection,
    onSelect: (TopSection) -> Unit,
    content: @Composable () -> Unit,
) {
    var contentHasFocus by remember { mutableStateOf(false) }
    val selectedTabFocus = remember { FocusRequester() }
    BackHandler(enabled = contentHasFocus) { runCatching { selectedTabFocus.requestFocus() } }

    SafeArea {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TopBar(selected = selected, onSelect = onSelect, selectedTabFocus = selectedTabFocus)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onFocusChanged { contentHasFocus = it.hasFocus },
            ) { content() }
        }
    }
}

@Composable
private fun TopBar(selected: TopSection, onSelect: (TopSection) -> Unit, selectedTabFocus: FocusRequester) {
    val context = LocalContext.current
    val wide = LocalConfiguration.current.screenWidthDp >= 900
    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(15_000)
        }
    }
    val clock = remember(now) { DateFormat.getTimeFormat(context).format(Date(now)) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (wide) {
                Text(
                    text = "OpenTV",
                    style = MaterialTheme.typography.titleLarge,
                    color = OpenTvColors.Primary,
                    modifier = Modifier.padding(end = 14.dp),
                )
            }
            for (section in TopSection.values()) {
                Tab(
                    label = section.label,
                    isSelected = section == selected,
                    onClick = { if (section != selected) onSelect(section) },
                    modifier = if (section == selected) Modifier.focusRequester(selectedTabFocus) else Modifier,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = clock, style = MaterialTheme.typography.titleMedium, color = OpenTvColors.OnBackground, maxLines = 1)
            FocusableSurface(
                onClick = { if (selected != TopSection.SETTINGS) onSelect(TopSection.SETTINGS) },
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(22.dp),
                focusedScale = 1.08f,
            ) { focused ->
                GearIcon(
                    color = if (focused || selected == TopSection.SETTINGS) OpenTvColors.Primary else OpenTvColors.Muted,
                    modifier = Modifier.align(Alignment.Center).size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun Tab(label: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(22.dp),
        focusedScale = 1.05f,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected) OpenTvColors.Primary else OpenTvColors.OnBackground,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 16.dp),
        )
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp)
                    .width(22.dp)
                    .height(3.dp)
                    .background(OpenTvColors.Primary, RoundedCornerShape(2.dp)),
            )
        }
    }
}

/** Engranaje dibujado (sin librería de iconos): aro central y ocho dientes. */
@Composable
private fun GearIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val r = size.minDimension / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(color = color, radius = r * 0.5f, center = c, style = Stroke(width = r * 0.3f))
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            val dx = cos(a).toFloat()
            val dy = sin(a).toFloat()
            drawLine(
                color = color,
                start = Offset(c.x + dx * r * 0.62f, c.y + dy * r * 0.62f),
                end = Offset(c.x + dx * r * 0.95f, c.y + dy * r * 0.95f),
                strokeWidth = r * 0.34f,
                cap = StrokeCap.Butt,
            )
        }
    }
}
