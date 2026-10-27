package com.opentv.app.bench

import android.app.Activity
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.opentv.app.ui.components.SafeArea
import com.opentv.app.ui.components.TvButton
import com.opentv.app.ui.theme.OpenTvColors

/**
 * Medición de captura y codificación LOCALES. Solo mide; no transmite, no guarda nada y no cambia ningún ajuste
 * del teléfono. Nada arranca solo: cada prueba empieza cuando se pulsa su botón y se acepta el permiso de Android.
 */
@Composable
fun BenchScreen(
    onBack: () -> Unit,
    viewModel: BenchViewModel = viewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pending by remember { mutableStateOf<List<BenchPlan.Option>>(emptyList()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val chosen = pending
        pending = emptyList()
        if (result.resultCode == Activity.RESULT_OK && data != null && chosen.isNotEmpty()) {
            viewModel.start(result.resultCode, data, chosen)
        }
    }
    val askAndStart: (List<BenchPlan.Option>) -> Unit = { chosen ->
        pending = chosen
        val manager = context.getSystemService(MediaProjectionManager::class.java)
        launcher.launch(manager.createScreenCaptureIntent())
    }
    // Al salir de la pantalla se detiene todo y se liberan los recursos.
    DisposableEffect(Unit) { onDispose { viewModel.stop() } }

    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    val options = viewModel.options
    val unsupported = viewModel.unsupported

    SafeArea {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Medición de captura y codificación", style = MaterialTheme.typography.headlineMedium, color = OpenTvColors.OnBackground)
            Text(
                "Mide cuántos cuadros por segundo logra ESTE teléfono al capturar la pantalla y al codificarla en H.264 por " +
                    "hardware. No transmite nada, no guarda imágenes ni audio, y no cambia la resolución ni la rotación. " +
                    "NO mide los FPS que llegan al TV ni el retraso de Miracast: eso depende además del transporte y del receptor. " +
                    "Android solo entrega cuadros cuando la imagen cambia: dejá esta pantalla abierta (la barra animada la mantiene en movimiento).",
                style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.Muted,
            )
            MotionBar(active = ui.running)

            Text(
                "Pantalla del teléfono: ${ui.refreshHz?.let { String.format(java.util.Locale.US, "%.0f Hz", it) } ?: BenchFormat.NA} (lo que informa Android)",
                style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.OnBackground,
            )
            Text(ui.status, style = MaterialTheme.typography.bodyLarge, color = OpenTvColors.OnBackground)

            if (ui.running) {
                TvButton(text = "Detener medición", onClick = viewModel::stop, horizontalPadding = 20.dp, modifier = Modifier.focusRequester(firstFocus))
            } else if (options.isEmpty()) {
                Text(
                    "Este teléfono no informa ningún codificador H.264 compatible con las configuraciones de prueba: no hay nada que medir.",
                    style = MaterialTheme.typography.bodyLarge, color = OpenTvColors.Warning,
                )
            } else {
                TvButton(
                    text = "Medir todas las compatibles (${options.size})",
                    onClick = { askAndStart(options) },
                    horizontalPadding = 20.dp,
                    modifier = Modifier.focusRequester(firstFocus),
                )
                for (option in options) {
                    TvButton(
                        text = "Medir solo ${option.config.label}",
                        onClick = { askAndStart(listOf(option)) },
                        horizontalPadding = 20.dp,
                    )
                }
            }

            if (options.isNotEmpty()) {
                Text("Capacidades que informa Android (por configuración)", style = MaterialTheme.typography.titleLarge, color = OpenTvColors.OnBackground)
                for (option in options) {
                    val f = option.encoder
                    Text(
                        "${option.config.label}: ${f.name} · hardware: ${f.hardware?.let { if (it) "sí" else "no" } ?: BenchFormat.NA} · " +
                            "ancho ${f.widthRange ?: BenchFormat.NA} · alto ${f.heightRange ?: BenchFormat.NA} · " +
                            "FPS admitidos para ese tamaño ${f.frameRateForSize ?: BenchFormat.NA} · bitrate ${f.bitrateRange ?: BenchFormat.NA} bps · " +
                            (f.profileLevel ?: "perfil y nivel: ${BenchFormat.NA}"),
                        style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.Muted,
                    )
                }
            }
            if (unsupported.isNotEmpty()) {
                Text(
                    "No compatibles con este codificador (no se prueban ni se fuerzan): " + unsupported.joinToString(", ") { it.label },
                    style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.Warning,
                )
            }

            if (ui.results.isNotEmpty() || ui.thermalBefore != null) {
                Text("Resultados medidos", style = MaterialTheme.typography.titleLarge, color = OpenTvColors.OnBackground)
                for (r in ui.results) {
                    Text(
                        BenchFormat.result(r).joinToString("\n"),
                        style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.OnBackground,
                    )
                }
                Text("Térmico antes: ${BenchFormat.thermal(ui.thermalBefore)}", style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.Muted)
                if (ui.thermalAfter != null) {
                    Text("Térmico después: ${BenchFormat.thermal(ui.thermalAfter)}", style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.Muted)
                }
                Text(
                    "La captura se mide con un ImageReader RGBA (puede ser más lenta que la captura directa al codificador). " +
                        "Cada fase dura unos 6 s y el primer segundo no cuenta. Es una medición puntual: repetila para ver la variación.",
                    style = MaterialTheme.typography.bodySmall, color = OpenTvColors.Muted,
                )
            }

            TvButton(text = "Volver", onClick = onBack, horizontalPadding = 20.dp)
        }
    }
}

/** Barra que se mueve a cada cuadro de la pantalla mientras se mide, para que la imagen cambie siempre. */
@Composable
private fun MotionBar(active: Boolean) {
    var t by remember { mutableStateOf(0L) }
    LaunchedEffect(active) {
        if (active) {
            while (true) withFrameNanos { t = it }
        }
    }
    Canvas(modifier = Modifier.fillMaxWidth().height(28.dp)) {
        val x = ((t / 3_000_000L) % 1000L) / 1000f
        drawRect(
            color = OpenTvColors.Success,
            topLeft = Offset(x * (size.width - 48f), 0f),
            size = Size(48f, size.height),
        )
    }
}
