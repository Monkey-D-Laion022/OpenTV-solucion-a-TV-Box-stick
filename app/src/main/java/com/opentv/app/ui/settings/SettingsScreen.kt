package com.opentv.app.ui.settings

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.opentv.app.launcher.DefaultLauncherHelper
import com.opentv.app.ui.components.SafeArea
import com.opentv.app.ui.components.TvButton
import com.opentv.app.ui.components.TvListItem
import com.opentv.app.ui.device.rememberDeviceInfo
import com.opentv.app.ui.theme.OpenTvColors

/**
 * Solo se muestran las secciones que existen de verdad: Launcher, Diagnóstico y Acerca de.
 * Apariencia, Reproducción, Control remoto, etc. se añadirán junto con sus funciones.
 */
@Composable
fun SettingsScreen(onOpenDiagnostics: () -> Unit, onOpenTools: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val info = rememberDeviceInfo()
    val helper = remember(context) { DefaultLauncherHelper(context) }

    // Al volver de los ajustes de Android se vuelve a comprobar si OpenTV quedó como launcher.
    var resumeCount by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumeCount++ }
    val isDefault = remember(resumeCount) { helper.isDefault() }

    var chooserUnavailable by remember { mutableStateOf(false) }

    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    SafeArea {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Configuración",
                style = MaterialTheme.typography.headlineMedium,
                color = OpenTvColors.OnBackground,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            TvListItem(
                title = "Launcher predeterminado",
                subtitle = if (isDefault) {
                    "OpenTV es tu launcher actual. Abrí esta opción para elegir otro y volver al original."
                } else {
                    "OpenTV no es tu launcher actual. Abrí esta opción para elegirlo en los ajustes de Android."
                },
                onClick = { chooserUnavailable = !helper.openChooserSettings() },
                modifier = Modifier.focusRequester(firstFocus),
            )
            Text(
                text = "OpenTV nunca se pone como launcher por su cuenta: lo elegís vos en Android. " +
                    "Para volver al anterior, elegilo en esa misma pantalla (Ajustes › Apps › " +
                    "Apps predeterminadas › App de inicio).",
                style = MaterialTheme.typography.bodyMedium,
                color = OpenTvColors.Muted,
            )
            if (chooserUnavailable) {
                Text(
                    text = "Este equipo no permitió abrir esa pantalla. Buscá «App de inicio» o " +
                        "«Apps predeterminadas» dentro de los Ajustes de Android.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = OpenTvColors.Warning,
                )
            }

            TvListItem(
                title = "Herramientas",
                subtitle = "Funciones avanzadas: reproducir un enlace directo",
                onClick = onOpenTools,
            )
            TvListItem(
                title = "Diagnóstico",
                subtitle = "Pantalla, orientación, memoria y limitaciones conocidas",
                onClick = onOpenDiagnostics,
            )
            TvListItem(
                title = "Acerca de OpenTV",
                subtitle = "Versión ${info.appVersionName} · Android 7.0 (API 24) o superior",
            )
            Text(
                text = "Los demás ajustes se añadirán junto con las funciones que controlan.",
                style = MaterialTheme.typography.bodyMedium,
                color = OpenTvColors.Muted,
                modifier = Modifier.padding(top = 4.dp),
            )

            TvButton(
                text = "Volver",
                onClick = onBack,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}
