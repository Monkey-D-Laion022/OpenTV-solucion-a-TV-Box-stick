package com.opentv.app.ui.home

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.opentv.app.core.BuildInfo
import com.opentv.app.navigation.PendingFeature
import com.opentv.app.navigation.Routes
import com.opentv.app.ui.apps.AddAppCard
import com.opentv.app.ui.apps.AppPickerDialog
import com.opentv.app.ui.apps.AppTile
import com.opentv.app.ui.apps.LaunchMessage
import com.opentv.app.ui.apps.PortraitWarningDialog
import com.opentv.app.ui.components.SafeArea
import com.opentv.app.ui.components.StatusChip
import com.opentv.app.ui.components.TvCard
import com.opentv.app.ui.connect.CastButton
import com.opentv.app.ui.connect.CastPickerDialog
import com.opentv.app.ui.theme.OpenTvColors
import java.util.Date

private val CARD_WIDTH = 270.dp

/**
 * Launcher en filas horizontales, al estilo Android TV: Aplicaciones, Multimedia y Ajustes. Cada fila
 * se recorre con izquierda/derecha y se cambia de fila con arriba/abajo; también se desplaza con el dedo.
 * «Continuar viendo» no aparece: todavía no hay historial real de reproducción.
 */
@Composable
fun HomeScreen(
    onNavigate: (route: String) -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val nowMillis by viewModel.nowMillis.collectAsStateWithLifecycle()
    val allApps by viewModel.allApps.collectAsStateWithLifecycle()
    val favoriteKeys by viewModel.favoriteKeys.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteApps.collectAsStateWithLifecycle()
    val launchState by viewModel.appLaunch.state.collectAsStateWithLifecycle()
    val externalDisplay by viewModel.externalDisplay.collectAsStateWithLifecycle()
    val resizePending by viewModel.resizePending.collectAsStateWithLifecycle()
    val outputTarget by viewModel.outputTarget.collectAsStateWithLifecycle()
    var castPickerOpen by remember { mutableStateOf(false) }
    var appPickerOpen by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val date = Date(nowMillis)
    val timeText = DateFormat.getTimeFormat(context).format(date)
    val dateText = DateFormat.getLongDateFormat(context).format(date)

    // Las apps y favoritas cambian al instalar/borrar o marcar ★: se releen al volver a la pantalla.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.refreshFavorites() }

    launchState.pendingWarning?.let { app ->
        PortraitWarningDialog(
            app = app,
            onConfirm = viewModel.appLaunch::confirmWarning,
            onDismiss = viewModel.appLaunch::dismissWarning,
        )
    }
    if (castPickerOpen) CastPickerDialog(onDismiss = { castPickerOpen = false })
    if (appPickerOpen) {
        AppPickerDialog(
            apps = allApps,
            pinned = favoriteKeys,
            onToggle = viewModel::toggleFavorite,
            onDismiss = { appPickerOpen = false },
        )
    }

    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(favorites.isNotEmpty()) {
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
    }

    SafeArea {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                // Holgura para que la ampliación del elemento enfocado no se recorte.
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HomeHeader(
                timeText = timeText,
                dateText = dateText,
                externalDisplayName = externalDisplay?.name,
                outputName = outputTarget?.name,
                onCast = { castPickerOpen = true },
                resizeForced = resizePending,
                onRestoreResolution = { onNavigate(Routes.MIRROR) },
            )

            HomeRow(title = "Aplicaciones") {
                itemsIndexed(favorites, key = { _, app -> app.packageName }) { index, app ->
                    AppTile(
                        app = app,
                        isFavorite = true,
                        loadIcon = viewModel.iconLoader::load,
                        onClick = { viewModel.appLaunch.onAppClick(app) },
                        onToggleFavorite = { viewModel.toggleFavorite(app) },
                        modifier = Modifier
                            .width(150.dp)
                            .height(148.dp)
                            .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier),
                    )
                }
                item(key = "add-app") {
                    AddAppCard(
                        onClick = { appPickerOpen = true },
                        modifier = Modifier
                            .width(150.dp)
                            .height(148.dp)
                            .then(if (favorites.isEmpty()) Modifier.focusRequester(firstFocus) else Modifier),
                    )
                }
            }
            LaunchMessage(state = launchState)

            HomeRow(title = "Multimedia") {
                item(key = "local-media") {
                    TvCard(
                        title = "Multimedia local",
                        subtitle = "Videos, fotos y música",
                        badge = "Próximamente",
                        onClick = { onNavigate(Routes.pending(PendingFeature.LOCAL_MEDIA)) },
                        modifier = Modifier.width(CARD_WIDTH),
                    )
                }
                item(key = "mirror") {
                    TvCard(
                        title = "Espejo nativo 16:9",
                        subtitle = "Miracast del sistema + Shizuku, sin franjas",
                        onClick = { onNavigate(Routes.MIRROR) },
                        modifier = Modifier.width(CARD_WIDTH),
                    )
                }
                item(key = "browser") {
                    TvCard(
                        title = "Espejo por navegador",
                        subtitle = "Sin Miracast: el TV abre una web",
                        onClick = { onNavigate(Routes.BROWSER) },
                        modifier = Modifier.width(CARD_WIDTH),
                    )
                }
                item(key = "project") {
                    TvCard(
                        title = "Proyectar OpenTV en el TV",
                        subtitle = "Pantalla propia 16:9 por DLNA",
                        onClick = { onNavigate(Routes.REMOTE) },
                        modifier = Modifier.width(CARD_WIDTH),
                    )
                }
                item(key = "cast") {
                    TvCard(
                        title = "Transmitir a TV",
                        subtitle = "Elegí un Smart TV de tu red",
                        onClick = { castPickerOpen = true },
                        modifier = Modifier.width(CARD_WIDTH),
                    )
                }
            }

            HomeRow(title = "Ajustes") {
                item(key = "all-apps") {
                    TvCard(
                        title = "Todas las apps",
                        subtitle = "Lista completa de tu teléfono",
                        onClick = { onNavigate(Routes.APPS) },
                        modifier = Modifier.width(CARD_WIDTH),
                    )
                }
                item(key = "connect") {
                    TvCard(
                        title = "Conectar TV",
                        subtitle = "Miracast, Cast y DLNA",
                        onClick = { onNavigate(Routes.CONNECT) },
                        modifier = Modifier.width(CARD_WIDTH),
                    )
                }
                item(key = "settings") {
                    TvCard(
                        title = "Configuración",
                        subtitle = "Launcher, herramientas, diagnóstico",
                        onClick = { onNavigate(Routes.SETTINGS) },
                        modifier = Modifier.width(CARD_WIDTH),
                    )
                }
                item(key = "device") {
                    TvCard(
                        title = "Dispositivo",
                        subtitle = "Pantalla, memoria y formato",
                        onClick = { onNavigate(Routes.DEVICE) },
                        modifier = Modifier.width(CARD_WIDTH),
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeRow(title: String, content: LazyListScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = OpenTvColors.OnBackground,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
            content = content,
        )
    }
}

@Composable
private fun HomeHeader(
    timeText: String,
    dateText: String,
    externalDisplayName: String?,
    outputName: String?,
    onCast: () -> Unit,
    resizeForced: Boolean,
    onRestoreResolution: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column {
            Text(
                text = "OpenTV",
                style = MaterialTheme.typography.headlineLarge,
                color = OpenTvColors.OnBackground,
            )
            Text(
                text = "Tu centro multimedia · ${BuildInfo.LABEL}",
                style = MaterialTheme.typography.bodyMedium,
                color = OpenTvColors.Muted,
            )
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = timeText,
                style = MaterialTheme.typography.headlineMedium,
                color = OpenTvColors.OnBackground,
            )
            Text(
                text = dateText,
                style = MaterialTheme.typography.bodyMedium,
                color = OpenTvColors.Muted,
            )
            // Estado real: lo informa DisplayManager (no distingue HDMI de inalámbrica).
            StatusChip(
                text = if (externalDisplayName == null) {
                    "Pantalla externa: ninguna"
                } else {
                    "Pantalla externa: $externalDisplayName"
                },
                dotColor = if (externalDisplayName == null) OpenTvColors.Muted else OpenTvColors.Success,
            )
            if (outputName != null) {
                StatusChip(text = "Salida: $outputName", dotColor = OpenTvColors.Success)
            }
            CastButton(onClick = onCast)
            if (resizeForced) {
                // La resolución forzada dura hasta restaurarla: se avisa en el inicio para no olvidarla.
                com.opentv.app.ui.components.TvButton(
                    text = "Resolución 16:9 forzada · Restaurar",
                    onClick = onRestoreResolution,
                    horizontalPadding = 20.dp,
                )
            }
        }
    }
}
