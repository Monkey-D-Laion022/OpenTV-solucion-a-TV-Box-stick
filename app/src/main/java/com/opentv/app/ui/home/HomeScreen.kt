package com.opentv.app.ui.home

import android.text.format.DateFormat
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
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
import com.opentv.app.navigation.PendingFeature
import com.opentv.app.navigation.Routes
import com.opentv.app.ui.apps.AppTile
import com.opentv.app.ui.apps.LaunchMessage
import com.opentv.app.ui.apps.PortraitWarningDialog
import com.opentv.app.ui.components.SafeArea
import com.opentv.app.ui.components.StatusChip
import com.opentv.app.core.BuildInfo
import com.opentv.app.ui.components.TvCard
import com.opentv.app.ui.connect.CastButton
import com.opentv.app.ui.connect.CastPickerDialog
import com.opentv.app.ui.theme.OpenTvColors
import java.util.Date

private data class HomeEntry(
    val title: String,
    val subtitle: String,
    val route: String,
    val badge: String? = null,
)

// "Continuar viendo" no aparece: no hay historial real de reproducción hasta la Fase 4.
private val homeEntries = listOf(
    HomeEntry(
        title = "Aplicaciones",
        subtitle = "Tus apps instaladas",
        route = Routes.APPS,
    ),
    HomeEntry(
        title = "Conectar TV",
        subtitle = "Miracast, Cast y DLNA",
        route = Routes.CONNECT,
    ),
    HomeEntry(
        title = "Reproductor",
        subtitle = "Videos locales y enlaces",
        route = Routes.PLAYER,
    ),
    HomeEntry(
        title = "Multimedia local",
        subtitle = "Videos, fotos y música",
        route = Routes.pending(PendingFeature.LOCAL_MEDIA),
        badge = "Fase 4",
    ),
    HomeEntry(
        title = "Configuración",
        subtitle = "Ajustes de OpenTV",
        route = Routes.SETTINGS,
    ),
    HomeEntry(
        title = "Dispositivo",
        subtitle = "Pantalla, memoria y formato",
        route = Routes.DEVICE,
    ),
)

private const val CARDS_PER_ROW = 3

@Composable
fun HomeScreen(
    onNavigate: (route: String) -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val nowMillis by viewModel.nowMillis.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteApps.collectAsStateWithLifecycle()
    val launchState by viewModel.appLaunch.state.collectAsStateWithLifecycle()
    val externalDisplay by viewModel.externalDisplay.collectAsStateWithLifecycle()
    var castPickerOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val date = Date(nowMillis)
    val timeText = DateFormat.getTimeFormat(context).format(date)
    val dateText = DateFormat.getLongDateFormat(context).format(date)

    // Las favoritas pueden cambiar en «Aplicaciones» o al instalar/borrar apps: se releen al volver.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.refreshFavorites() }

    launchState.pendingWarning?.let { app ->
        PortraitWarningDialog(
            app = app,
            onConfirm = viewModel.appLaunch::confirmWarning,
            onDismiss = viewModel.appLaunch::dismissWarning,
        )
    }

    val firstCardFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstCardFocus.requestFocus() } }

    if (castPickerOpen) CastPickerDialog(onDismiss = { castPickerOpen = false })

    SafeArea {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                // Holgura para que la ampliación de la tarjeta enfocada no se recorte.
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            HomeHeader(
                timeText = timeText,
                dateText = dateText,
                externalDisplayName = externalDisplay?.name,
                onCast = { castPickerOpen = true },
            )

            if (favorites.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Tus favoritas",
                        style = MaterialTheme.typography.titleLarge,
                        color = OpenTvColors.OnBackground,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        favorites.forEach { app ->
                            key(app.packageName) {
                                AppTile(
                                    app = app,
                                    isFavorite = true,
                                    loadIcon = viewModel.iconLoader::load,
                                    onClick = { viewModel.appLaunch.onAppClick(app) },
                                    onToggleFavorite = { viewModel.toggleFavorite(app) },
                                    modifier = Modifier
                                        .width(150.dp)
                                        .height(148.dp),
                                )
                            }
                        }
                    }
                    LaunchMessage(state = launchState)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                homeEntries.chunked(CARDS_PER_ROW).forEachIndexed { rowIndex, rowEntries ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        rowEntries.forEachIndexed { columnIndex, entry ->
                            val focusModifier = if (rowIndex == 0 && columnIndex == 0) {
                                Modifier.focusRequester(firstCardFocus)
                            } else {
                                Modifier
                            }
                            TvCard(
                                title = entry.title,
                                subtitle = entry.subtitle,
                                badge = entry.badge,
                                onClick = { onNavigate(entry.route) },
                                modifier = Modifier
                                    .weight(1f)
                                    .then(focusModifier),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(timeText: String, dateText: String, externalDisplayName: String?, onCast: () -> Unit) {
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
                style = MaterialTheme.typography.bodyLarge,
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
            CastButton(onClick = onCast)
        }
    }
}
