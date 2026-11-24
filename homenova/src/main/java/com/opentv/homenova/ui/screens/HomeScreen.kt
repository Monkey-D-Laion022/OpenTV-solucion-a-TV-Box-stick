package com.opentv.homenova.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opentv.homenova.core.AppEntry
import com.opentv.homenova.core.MediaEntry
import com.opentv.homenova.ui.components.AppRow
import com.opentv.homenova.ui.components.ContentRow
import com.opentv.homenova.ui.components.EmptyState
import com.opentv.homenova.ui.components.FeaturedHero
import com.opentv.homenova.ui.components.FocusableCard
import com.opentv.homenova.ui.components.NovaButton
import com.opentv.homenova.ui.components.ShortcutItem
import com.opentv.homenova.ui.components.ShortcutRow
import com.opentv.homenova.ui.theme.LocalNovaPalette
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    featured: List<MediaEntry>,
    continueWatching: List<MediaEntry>,
    trending: List<MediaEntry>,
    recommendations: List<MediaEntry>,
    liveChannels: List<MediaEntry>,
    apps: List<AppEntry>,
    favourites: Set<String>,
    showRecommendations: Boolean,
    onPlay: (MediaEntry) -> Unit,
    onDetails: (MediaEntry) -> Unit,
    onAppClick: (AppEntry) -> Unit,
    onAppLongClick: (AppEntry) -> Unit,
    onOpenApps: () -> Unit,
    onOpenDiscover: () -> Unit,
    onOpenLive: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    openTvShortcuts: List<ShortcutItem> = emptyList(),
    systemShortcuts: List<ShortcutItem> = emptyList(),
) {
    val palette = LocalNovaPalette.current
    val listState = rememberLazyListState()
    val heroFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(220)
        runCatching { heroFocus.requestFocus() }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 6.dp, bottom = 46.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item(key = "hero") {
            FeaturedHero(
                items = featured,
                onPlay = onPlay,
                onDetails = onDetails,
                initialFocusRequester = heroFocus,
                modifier = Modifier.padding(horizontal = 40.dp),
            )
        }

        if (continueWatching.isNotEmpty()) {
            item(key = "continue") {
                ContentRow(
                    title = "Continúa viendo",
                    items = continueWatching,
                    onSelect = onPlay,
                )
            }
        }

        item(key = "apps") {
            AppRow(
                title = "Tus aplicaciones",
                apps = apps.take(12),
                favourites = favourites,
                onSelect = onAppClick,
                onLongSelect = onAppLongClick,
                actionLabel = "Todas las apps",
                onAction = onOpenApps,
            )
        }

        if (openTvShortcuts.isNotEmpty()) {
            item(key = "opentv-shortcuts") {
                ShortcutRow(
                    title = "OpenTV",
                    subtitle = "Transmitir a la TV, conexiones y herramientas",
                    items = openTvShortcuts,
                )
            }
        }

        if (trending.isNotEmpty()) {
            item(key = "trending") {
                ContentRow(
                    title = "Tendencias esta semana",
                    subtitle = "Lo que más se está viendo en OpenTV",
                    items = trending,
                    onSelect = onPlay,
                    actionLabel = "Ver todo",
                    onAction = onOpenDiscover,
                )
            }
        }

        item(key = "live") {
            ContentRow(
                title = "En directo ahora",
                items = liveChannels,
                onSelect = onPlay,
                actionLabel = "Todos los canales",
                onAction = onOpenLive,
            )
        }

        if (showRecommendations && recommendations.isNotEmpty()) {
            item(key = "recommendations") {
                ContentRow(
                    title = "Elegido para ti",
                    subtitle = "Basado en lo que has visto en este dispositivo",
                    items = recommendations,
                    onSelect = onPlay,
                    actionLabel = "Personalizar",
                    onAction = onOpenSettings,
                )
            }
        }

        if (apps.isEmpty()) {
            item(key = "no-apps") {
                EmptyState(
                    icon = Icons.Rounded.Tune,
                    title = "Aún no hay aplicaciones",
                    body = "Cuando instales apps con soporte para TV aparecerán aquí automáticamente.",
                    actionLabel = "Abrir ajustes",
                    onAction = onOpenSettings,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (systemShortcuts.isNotEmpty()) {
            item(key = "system-shortcuts") {
                ShortcutRow(
                    title = "Sistema",
                    subtitle = "Accesos directos a los ajustes de Android",
                    items = systemShortcuts,
                )
            }
        }

        item(key = "customize") {
            CustomizaHomeCard(onOpenSettings = onOpenSettings)
        }

        item(key = "hint") {
            Text(
                text = "Muévete con el D-pad · OK para seleccionar · ATRÁS para volver",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textMuted,
                modifier = Modifier.padding(horizontal = 44.dp),
            )
        }
    }
}

@Composable
private fun CustomizaHomeCard(onOpenSettings: () -> Unit) {
    val palette = LocalNovaPalette.current
    FocusableCard(
        onSelect = onOpenSettings,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp),
        shape = RoundedCornerShape(22.dp),
        focusedScale = 1.012f,
        background = Color.White.copy(alpha = 0.045f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(44.dp)
                    .background(palette.accent, RoundedCornerShape(50)),
            )
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Personaliza tu pantalla de inicio",
                    style = MaterialTheme.typography.titleLarge,
                    color = palette.textPrimary,
                )
                Text(
                    text = "Elige qué filas, apps y recomendaciones quieres ver primero.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary,
                )
            }
            NovaButton(
                label = "Configurar",
                icon = Icons.Rounded.ArrowForward,
                primary = false,
                compact = true,
                onSelect = onOpenSettings,
            )
        }
    }
}
