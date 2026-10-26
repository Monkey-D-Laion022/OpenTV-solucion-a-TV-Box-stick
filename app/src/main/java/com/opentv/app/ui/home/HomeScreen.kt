package com.opentv.app.ui.home

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
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
    var appPickerOpen by remember { mutableStateOf(false) }
    var hero by remember { mutableStateOf(HeroInfo("OpenTV", "Elegí una app o una forma de transmitir a tu TV.")) }

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
                onCast = { onNavigate(Routes.CONNECT) },
                resizeForced = resizePending,
                onRestoreResolution = { onNavigate(Routes.MIRROR) },
            )

            HeroPanel(hero)

            HomeRow(title = "Aplicaciones") {
                itemsIndexed(favorites, key = { _, app -> app.packageName }) { index, app ->
                    AppTile(
                        app = app,
                        isFavorite = true,
                        loadIcon = viewModel.iconLoader::load,
                        onClick = { viewModel.appLaunch.onAppClick(app) },
                        onToggleFavorite = { viewModel.toggleFavorite(app) },
                        onFocus = { hero = HeroInfo(app.label, "OK abre la app en el teléfono. Mantené OK (o Menú) para quitarla de favoritas.") },
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

            // «Transmitir TV»: cada tarjeta solo ABRE una pantalla que ya existe; ninguna toca la resolución. El 16:9 se
            // activa y se restaura únicamente con los botones de su propia pantalla («Espejo nativo 16:9»).
            HomeRow(title = "Transmitir TV") {
                item(key = "hub") {
                    TvCard(
                        title = "Transmitir a TV",
                        subtitle = "Elegí un Smart TV de tu red (DLNA)",
                        onClick = { onNavigate(Routes.CONNECT) },
                        onFocus = { hero = HeroInfo("Transmitir a TV", "Busca los TV de tu red y les envía una dirección o la pantalla del teléfono por DLNA.") },
                        modifier = Modifier.width(CARD_WIDTH),
                    )
                }
                item(key = "mirror") {
                    TvCard(
                        title = "Espejo nativo 16:9",
                        subtitle = "Miracast del sistema + Shizuku",
                        onClick = { onNavigate(Routes.MIRROR) },
                        onFocus = { hero = HeroInfo("Espejo nativo 16:9", "Abre la pantalla donde vos forzás el 16:9 y abrís la transmisión del sistema. Acá no se cambia nada.") },
                        modifier = Modifier.width(CARD_WIDTH),
                    )
                }
                item(key = "browser") {
                    TvCard(
                        title = "Espejo por navegador",
                        subtitle = "Sin Miracast: el TV abre una web",
                        onClick = { onNavigate(Routes.BROWSER) },
                        onFocus = { hero = HeroInfo("Espejo por navegador", "El TV abre una dirección corta en su navegador y ve la pantalla del teléfono (imagen sin sonido).") },
                        modifier = Modifier.width(CARD_WIDTH),
                    )
                }
                item(key = "own") {
                    TvCard(
                        title = "Proyectar OpenTV en el TV",
                        subtitle = "Pantalla propia 16:9 por DLNA",
                        onClick = { onNavigate(Routes.REMOTE) },
                        onFocus = { hero = HeroInfo("Proyectar OpenTV", "Dibuja la interfaz de OpenTV en una pantalla virtual 16:9 y la envía al TV por DLNA.") },
                        modifier = Modifier.width(CARD_WIDTH),
                    )
                }
                item(key = "native") {
                    TvCard(
                        title = "Emisión del sistema",
                        subtitle = "Cast / Smart View / Miracast",
                        onClick = { viewModel.openNativeCast(context) },
                        onFocus = { hero = HeroInfo("Emisión del sistema", "Abre el panel de transmisión de Android, si tu teléfono lo tiene. No cambia la resolución.") },
                        modifier = Modifier.width(CARD_WIDTH),
                    )
                }
            }

            HomeRow(title = "Más") {
                item(key = "all-apps") {
                    TvCard(
                        title = "Todas las apps",
                        subtitle = "Lista completa de tu teléfono",
                        onClick = { onNavigate(Routes.APPS) },
                        onFocus = { hero = HeroInfo("Todas las apps", "La lista completa de tu teléfono; desde ahí podés fijar favoritas en la fila principal.") },
                        modifier = Modifier.width(CARD_WIDTH),
                    )
                }
                item(key = "local-media") {
                    TvCard(
                        title = "Multimedia local",
                        subtitle = "Pendiente (no implementado)",
                        onClick = { onNavigate(Routes.pending(PendingFeature.LOCAL_MEDIA)) },
                        onFocus = { hero = HeroInfo("Multimedia local", "Todavía no existe: la pantalla explica qué falta.") },
                        modifier = Modifier.width(CARD_WIDTH),
                    )
                }
                item(key = "shizuku") {
                    TvCard(
                        title = "Asistente de Shizuku",
                        subtitle = "Estado y pasos guiados",
                        onClick = { onNavigate(Routes.SHIZUKU_GUIDE) },
                        onFocus = { hero = HeroInfo("Asistente de Shizuku", "Comprueba si Shizuku está lista y te guía paso a paso. No cambia la resolución.") },
                        modifier = Modifier.width(CARD_WIDTH),
                    )
                }
                item(key = "device") {
                    TvCard(
                        title = "Dispositivo",
                        subtitle = "Pantalla, memoria y formato",
                        onClick = { onNavigate(Routes.DEVICE) },
                        onFocus = { hero = HeroInfo("Dispositivo", "Datos reales de tu pantalla, memoria y formato.") },
                        modifier = Modifier.width(CARD_WIDTH),
                    )
                }
                item(key = "settings") {
                    TvCard(
                        title = "Configuración",
                        subtitle = "Launcher, herramientas, diagnóstico",
                        onClick = { onNavigate(Routes.SETTINGS) },
                        onFocus = { hero = HeroInfo("Configuración", "Launcher, herramientas, diagnóstico del equipo y formato de pantalla.") },
                        modifier = Modifier.width(CARD_WIDTH),
                    )
                }
            }
        }
    }
}

private data class HeroInfo(val title: String, val text: String)

/**
 * Panel destacado fijo (como el «Browse» de Android TV): muestra de qué trata el elemento que tiene el foco.
 * Alto fijo para que el contenido de abajo no salte al navegar con el control remoto.
 */
@Composable
private fun HeroPanel(info: HeroInfo) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp)
            .background(
                Brush.horizontalGradient(listOf(OpenTvColors.SurfaceFocused, OpenTvColors.Background)),
                RoundedCornerShape(20.dp),
            )
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = info.title,
                style = MaterialTheme.typography.headlineMedium,
                color = OpenTvColors.OnBackground,
                maxLines = 1,
            )
            Text(
                text = info.text,
                style = MaterialTheme.typography.bodyLarge,
                color = OpenTvColors.Muted,
                maxLines = 2,
            )
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
