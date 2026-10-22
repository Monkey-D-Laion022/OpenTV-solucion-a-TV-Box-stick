package com.opentv.app.ui.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.opentv.app.OpenTvApp
import com.opentv.app.core.AspectRatio
import com.opentv.app.core.BuildInfo
import com.opentv.app.device.DeviceInfo
import com.opentv.app.device.DeviceInfoProvider
import com.opentv.app.ui.components.InfoRow
import com.opentv.app.ui.components.SafeArea
import com.opentv.app.ui.components.TvButton
import com.opentv.app.ui.theme.OpenTvColors
import java.util.Locale

/** Lee la información del equipo y la vuelve a leer si cambia la configuración (p. ej. pantalla). */
@Composable
fun rememberDeviceInfo(): DeviceInfo {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(configuration) { DeviceInfoProvider(context).read() }
}

@Composable
fun DeviceInfoScreen(onBack: () -> Unit) {
    val info = rememberDeviceInfo()
    val context = LocalContext.current
    // Lectura puntual al abrir la pantalla; el seguimiento en vivo está en «Conectar TV».
    val externalDisplay = remember { runCatching { (context.applicationContext as OpenTvApp).container.displayMonitor.current() }.getOrNull() }
    val backFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { backFocus.requestFocus() } }

    SafeArea {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Dispositivo y formato de pantalla",
                style = MaterialTheme.typography.headlineMedium,
                color = OpenTvColors.OnBackground,
            )
            Text(
                text = "Datos que informa el sistema. No se inventa ningún valor.",
                style = MaterialTheme.typography.bodyMedium,
                color = OpenTvColors.Muted,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            InfoRow("Pantalla (real)", "${info.displayWidthPx} × ${info.displayHeightPx} px")
            InfoRow("Ventana de la app", "${info.windowWidthPx} × ${info.windowHeightPx} px")
            InfoRow("Orientación", if (info.isLandscape) "Horizontal" else "Vertical")
            InfoRow("Densidad", "${info.densityDpi} dpi")
            InfoRow(
                "Relación de aspecto",
                aspectRatioText(info),
            )
            InfoRow(
                "Resolución de salida",
                "Depende del sistema y del televisor; no se puede medir desde esta pantalla.",
            )
            InfoRow("Versión de OpenTV", BuildInfo.LABEL)
            InfoRow("Equipo", "${info.manufacturer} ${info.model}".trim())
            InfoRow("Android", "${info.androidRelease} (API ${info.sdkInt})")
            InfoRow("Memoria RAM", ramText(info))
            InfoRow(
                "Pantalla externa",
                externalDisplay?.let { "${it.name} (Android no indica si es HDMI o inalámbrica)" }
                    ?: "Ninguna detectada",
            )
            InfoRow(
                "Limitaciones conocidas",
                "La transmisión de pantalla (Miracast) depende del teléfono, de Android y del TV. " +
                    "OpenTV solo podrá abrir la función del sistema y no controla la relación de " +
                    "aspecto de esa transmisión. Las apps externas con orientación fija no pueden " +
                    "forzarse a horizontal.",
            )

            TvButton(
                text = "Volver",
                onClick = onBack,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .focusRequester(backFocus),
            )
        }
    }
}

private fun aspectRatioText(info: DeviceInfo): String {
    val label = AspectRatio.describe(info.windowWidthPx, info.windowHeightPx)
    val note = if (AspectRatio.isApproximately16by9(info.windowWidthPx, info.windowHeightPx)) {
        "aprox. 16:9"
    } else {
        "no es 16:9; la interfaz se adapta al espacio disponible"
    }
    return "$label ($note)"
}

private fun ramText(info: DeviceInfo): String {
    val gb = info.totalRamBytes / (1024.0 * 1024.0 * 1024.0)
    val size = String.format(Locale.US, "%.1f GB", gb)
    return if (info.isLowRamDevice) "$size (el sistema lo marca como equipo de poca RAM)" else size
}
