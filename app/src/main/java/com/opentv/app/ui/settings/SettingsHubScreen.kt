package com.opentv.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.opentv.app.launcher.DefaultLauncherHelper
import com.opentv.app.navigation.Routes
import com.opentv.app.ui.components.FocusableSurface
import com.opentv.app.ui.components.InfoRow
import com.opentv.app.ui.components.TvButton
import com.opentv.app.ui.components.TvListItem
import com.opentv.app.ui.device.rememberDeviceInfo
import com.opentv.app.ui.shell.NavCatalog
import com.opentv.app.ui.shell.NetworkText
import com.opentv.app.ui.shell.SettingsCategory
import com.opentv.app.ui.shell.SystemAccess
import com.opentv.app.ui.theme.OpenTvColors

/**
 * Ajustes: categorías a la izquierda y detalle a la derecha. Solo hay controles que funcionan: o muestran datos que
 * Android informa, o abren la pantalla oficial de Android que los cambia. OpenTV no cambia ajustes del sistema por su cuenta.
 */
@Composable
fun SettingsHubScreen(onNavigate: (String) -> Unit) {
    var categoryId by rememberSaveable { mutableStateOf(SettingsCategory.NETWORK.id) }
    val category = SettingsCategory.fromId(categoryId)
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
    }

    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(
            modifier = Modifier.width(260.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Ajustes", style = MaterialTheme.typography.headlineMedium, color = OpenTvColors.OnBackground)
            for (c in SettingsCategory.values()) {
                FocusableSurface(
                    onClick = { categoryId = c.id },
                    onFocusGained = { categoryId = c.id },
                    modifier = Modifier.fillMaxWidth().then(if (c == SettingsCategory.NETWORK) Modifier.focusRequester(firstFocus) else Modifier),
                    focusedScale = 1.02f,
                ) {
                    Text(
                        text = c.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (c == category) OpenTvColors.Primary else OpenTvColors.OnBackground,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    )
                }
            }
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(category.title, style = MaterialTheme.typography.titleLarge, color = OpenTvColors.Primary)
            when (category) {
                SettingsCategory.NETWORK -> NetworkPane()
                SettingsCategory.DISPLAY -> DisplayPane()
                SettingsCategory.SOUND -> SoundPane()
                SettingsCategory.APPS -> AppsPane(onNavigate)
                SettingsCategory.SYSTEM -> SystemPane(onNavigate) { categoryId = SettingsCategory.DIAGNOSTICS.id }
                SettingsCategory.DIAGNOSTICS -> DiagnosticsPane(onNavigate)
            }
        }
    }
}

@Composable
private fun OpenAndroidButton(text: String, screen: SystemAccess.Screen) {
    val context = LocalContext.current
    var unavailable by remember { mutableStateOf(false) }
    TvButton(text = text, onClick = { unavailable = !SystemAccess.open(context, screen) }, horizontalPadding = 24.dp)
    if (unavailable) {
        Text("Este equipo no permitió abrir esa pantalla de Android.", style = MaterialTheme.typography.bodyLarge, color = OpenTvColors.Warning)
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.Muted)
}

@Composable
private fun NetworkPane() {
    val context = LocalContext.current
    // Lectura puntual al abrir; se vuelve a leer al volver de los ajustes de Android.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val snapshot = remember(refresh) { SystemAccess.readNetwork(context) }
    for ((label, value) in NetworkText.rows(snapshot)) InfoRow(label, value)
    Note(NetworkText.LIMITATION)
    OpenAndroidButton("Abrir ajustes de Wi-Fi de Android", SystemAccess.Screen.WIFI)
}

@Composable
private fun DisplayPane() {
    val info = rememberDeviceInfo()
    InfoRow("Pantalla del teléfono", "${info.displayWidthPx} × ${info.displayHeightPx} px · ${info.densityDpi} dpi")
    InfoRow("Orientación", if (info.isLandscape) "Horizontal" else "Vertical")
    Note("OpenTV muestra estos datos pero no cambia la resolución ni la densidad del teléfono desde acá.")
    OpenAndroidButton("Abrir ajustes de pantalla de Android", SystemAccess.Screen.DISPLAY)
}

@Composable
private fun SoundPane() {
    Note("El volumen y el sonido los controla Android: OpenTV no tiene ajustes de sonido propios todavía.")
    OpenAndroidButton("Abrir ajustes de sonido de Android", SystemAccess.Screen.SOUND)
}

@Composable
private fun AppsPane(onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val helper = remember(context) { DefaultLauncherHelper(context) }
    var resumeCount by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumeCount++ }
    val isDefault = remember(resumeCount) { helper.isDefault() }
    var chooserUnavailable by remember { mutableStateOf(false) }

    TvListItem(
        title = "Launcher predeterminado",
        subtitle = if (isDefault) {
            "OpenTV es tu launcher actual. Abrí esta opción para elegir otro y volver al original."
        } else {
            "OpenTV no es tu launcher actual. Abrí esta opción para elegirlo en los ajustes de Android."
        },
        onClick = { chooserUnavailable = !helper.openChooserSettings() },
    )
    Note(
        "OpenTV nunca se pone como launcher por su cuenta: lo elegís vos en Android. Para volver al anterior, elegilo en esa misma " +
            "pantalla (Ajustes › Apps › Apps predeterminadas › App de inicio).",
    )
    if (chooserUnavailable) {
        Text(
            "Este equipo no permitió abrir esa pantalla. Buscá «App de inicio» o «Apps predeterminadas» dentro de los Ajustes de Android.",
            style = MaterialTheme.typography.bodyLarge,
            color = OpenTvColors.Warning,
        )
    }
    TvListItem("Todas las aplicaciones", "Lista completa y favoritas de OpenTV", onClick = { onNavigate(Routes.APPS) })
    OpenAndroidButton("Abrir ajustes de aplicaciones de Android", SystemAccess.Screen.APPLICATIONS)
}

@Composable
private fun SystemPane(onNavigate: (String) -> Unit, onGoDiagnostics: () -> Unit) {
    val info = rememberDeviceInfo()
    InfoRow("Versión de OpenTV", info.appVersionName)
    InfoRow("Equipo", "${info.manufacturer} ${info.model}")
    InfoRow("Android", "${info.androidRelease} (API ${info.sdkInt})")
    InfoRow("Memoria", "${info.totalRamBytes / (1024 * 1024)} MB" + if (info.isLowRamDevice) " · equipo de poca memoria" else "")
    Note("OpenTV funciona en Android 7.0 (API 24) o superior.")
    TvListItem("Herramientas", "Funciones avanzadas: reproducir un enlace directo", onClick = { onNavigate(Routes.TOOLS) })
    TvListItem("Diagnósticos", "Medición de captura, Wi-Fi Direct, Wi-Fi Display, Shizuku y dispositivo", onClick = onGoDiagnostics)
}

@Composable
private fun DiagnosticsPane(onNavigate: (String) -> Unit) {
    Note("Pruebas y asistentes. Cada una solo hace lo que dice y arranca cuando vos la iniciás.")
    for (e in NavCatalog.diagnostics) {
        TvListItem(e.title, e.subtitle, onClick = { e.route?.let(onNavigate) })
    }
}
