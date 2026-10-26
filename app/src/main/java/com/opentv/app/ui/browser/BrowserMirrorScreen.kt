package com.opentv.app.ui.browser

import android.app.Activity
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.opentv.app.stream.BrowserMirrorState
import com.opentv.app.ui.components.SafeArea
import com.opentv.app.ui.components.TvButton
import com.opentv.app.ui.theme.OpenTvColors

/** Espejo por navegador: el TV abre una dirección web de OpenTV y muestra la pantalla del teléfono (MJPEG por HTTP). */
@Composable
fun BrowserMirrorScreen(
    onBack: () -> Unit,
    viewModel: BrowserMirrorViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val quality by viewModel.quality.collectAsStateWithLifecycle()
    val shortUrl by viewModel.shortUrl.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) viewModel.start(result.resultCode, data)
    }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    val running = state is BrowserMirrorState.Serving || state is BrowserMirrorState.Starting

    SafeArea {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Espejo por navegador (sin Miracast)", style = MaterialTheme.typography.headlineMedium, color = OpenTvColors.OnBackground)
            Text(
                "Funciona con cualquier teléfono con Wi‑Fi: el TV abre una dirección web y muestra la pantalla. Hace falta un TV con " +
                    "navegador, en la misma red Wi‑Fi. Es HTTP simple (sin HTTPS) en un puerto fijo (8080). Con la dirección corta, " +
                    "cualquiera en tu Wi‑Fi que la escriba puede ver la pantalla mientras esté activo: usá «con código» en redes ajenas. " +
                    "Los navegadores de TV muy viejos pueden no abrirla; no está verificado en ninguno.",
                style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.Muted,
            )
            when (val s = state) {
                BrowserMirrorState.Idle -> Text("Detenido.", style = MaterialTheme.typography.bodyLarge, color = OpenTvColors.OnBackground)
                BrowserMirrorState.Starting -> Text("Iniciando…", style = MaterialTheme.typography.bodyLarge, color = OpenTvColors.OnBackground)
                is BrowserMirrorState.Failed -> Text(s.message, style = MaterialTheme.typography.bodyLarge, color = OpenTvColors.Warning)
                is BrowserMirrorState.Serving -> {
                    Text("En el navegador del TV escribí:", style = MaterialTheme.typography.bodyLarge, color = OpenTvColors.OnBackground)
                    Text(s.url, style = MaterialTheme.typography.headlineSmall, color = OpenTvColors.Success)
                    Text(
                        "Si no se ve imagen (navegador muy viejo), probá el modo compatible: ${s.slowUrl}",
                        style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.Muted,
                    )
                    Text(
                        if (s.requests.isEmpty()) {
                            "Todavía no llegó ningún pedido del TV. Si el navegador dice «página no disponible», revisá que el TV " +
                                "y el teléfono estén en la misma red Wi‑Fi (y que el router no aísle los equipos) y que la dirección esté bien escrita."
                        } else {
                            "Pedidos recibidos:\n" + s.requests.joinToString("\n")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (s.requests.isEmpty()) OpenTvColors.Warning else OpenTvColors.Muted,
                    )
                    Text(
                        "TVs conectados: ${s.clients} · ${s.framesPerSecond} cuadros/s · ${s.kbPerSecond} KB/s",
                        style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.OnBackground,
                    )
                    Text(
                        "Podés salir de OpenTV y usar cualquier app; para cortar, volvé acá o usá «Detener» de la notificación. " +
                            "Consejo: con el 16:9 forzado y el teléfono en horizontal no habrá franjas negras.",
                        style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.Muted,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (running) {
                    TvButton(text = "Detener", onClick = viewModel::stop, horizontalPadding = 20.dp, modifier = Modifier.focusRequester(firstFocus))
                } else {
                    TvButton(
                        text = "Iniciar espejo por navegador",
                        onClick = {
                            val manager = context.getSystemService(MediaProjectionManager::class.java)
                            launcher.launch(manager.createScreenCaptureIntent())
                        },
                        horizontalPadding = 20.dp,
                        modifier = Modifier.focusRequester(firstFocus),
                    )
                    TvButton(text = "Calidad: ${quality.label}", onClick = viewModel::cycleQuality, horizontalPadding = 20.dp)
                    TvButton(
                        text = if (shortUrl) "Dirección: corta (sin código)" else "Dirección: con código",
                        onClick = viewModel::toggleShortUrl,
                        horizontalPadding = 20.dp,
                    )
                }
                TvButton(text = "Volver", onClick = onBack, horizontalPadding = 20.dp)
            }
        }
    }
}
