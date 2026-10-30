package com.opentv.app.ui.home

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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.opentv.app.catalog.CatalogItem
import com.opentv.app.catalog.CatalogText
import com.opentv.app.catalog.CatalogViewModel
import com.opentv.app.core.BuildInfo
import com.opentv.app.navigation.Routes
import com.opentv.app.ui.apps.AddAppCard
import com.opentv.app.ui.apps.AppPickerDialog
import com.opentv.app.ui.apps.AppTile
import com.opentv.app.ui.apps.LaunchMessage
import com.opentv.app.ui.apps.PortraitWarningDialog
import com.opentv.app.ui.components.StatusChip
import com.opentv.app.ui.components.TvButton
import com.opentv.app.ui.tv.ActionCard
import com.opentv.app.ui.tv.BackdropHero
import com.opentv.app.ui.tv.HeroContent
import com.opentv.app.ui.tv.TvMetrics
import com.opentv.app.ui.tv.rememberTvMetrics
import com.opentv.app.ui.connect.CastButton
import com.opentv.app.ui.shell.EntryAction
import com.opentv.app.ui.shell.NavCatalog
import com.opentv.app.ui.shell.NavEntry
import com.opentv.app.ui.theme.OpenTvColors

/**
 * Inicio cinematográfico estilo Android TV. SOLO muestra información real: tus apps favoritas y los accesos que existen.
 * No hay películas, series ni «Seguir viendo» inventados: todavía no hay historial ni catálogo de contenido.
 * La barra superior (secciones, reloj, engranaje) la pone [com.opentv.app.ui.shell.OpenTvShell].
 */
@Composable
fun HomeScreen(
    onNavigate: (route: String) -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val allApps by viewModel.allApps.collectAsStateWithLifecycle()
    val favoriteKeys by viewModel.favoriteKeys.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteApps.collectAsStateWithLifecycle()
    val launchState by viewModel.appLaunch.state.collectAsStateWithLifecycle()
    val externalDisplay by viewModel.externalDisplay.collectAsStateWithLifecycle()
    val resizePending by viewModel.resizePending.collectAsStateWithLifecycle()
    val outputTarget by viewModel.outputTarget.collectAsStateWithLifecycle()
    var appPickerOpen by remember { mutableStateOf(false) }
    var hero by remember { mutableStateOf(HeroContent(DEFAULT_TITLE, DEFAULT_TEXT)) }
    val metrics = rememberTvMetrics()
    val context = LocalContext.current

    // Catálogo (TMDB): información, no reproducción. Solo se actualiza con el Inicio visible y datos vencidos.
    val catalogVm: CatalogViewModel = viewModel()
    val catalog by catalogVm.state.collectAsStateWithLifecycle()
    var detailItem by remember { mutableStateOf<CatalogItem?>(null) }
    var backdropItem by remember { mutableStateOf<CatalogItem?>(null) }
    fun setHero(content: HeroContent) {
        hero = content
        backdropItem = null
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { catalogVm.refresh() }
    val backdropWidthPx = with(LocalDensity.current) { metrics.contentWidthDp.dp.roundToPx() }.coerceAtMost(1280)
    // El fondo cambia solo cuando el foco se queda quieto un momento: no se piden imágenes mientras se recorre rápido.
    val backdrop by produceState<ImageBitmap?>(initialValue = null, backdropItem) {
        val item = backdropItem
        if (item == null) {
            value = null
        } else {
            delay(450)
            value = catalogVm.backdrop(item, backdropWidthPx)
        }
    }
    detailItem?.let { item -> CatalogDetailDialog(item = item, onDismiss = { detailItem = null }) }

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

    // Restauración de foco: al volver de otra pantalla se enfoca el último elemento usado (si ya existe en pantalla).
    var lastFocus by rememberSaveable { mutableStateOf("") }
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    fun requesterFor(key: String): FocusRequester = requesters.getOrPut(key) { FocusRequester() }
    fun focusModifier(key: String): Modifier = Modifier.focusRequester(requesterFor(key))

    val firstKey = favorites.firstOrNull()?.let { "app:${it.packageName}" } ?: KEY_ADD
    LaunchedEffect(favorites.isNotEmpty()) {
        withFrameNanos { }
        // En Compose 1.6 requestFocus() devuelve Unit y lanza excepción si el elemento todavía no está en pantalla.
        val restored = lastFocus.isNotEmpty() && runCatching { requesterFor(lastFocus).requestFocus() }.isSuccess
        if (!restored) runCatching { requesterFor(firstKey).requestFocus() }
    }

    fun open(entry: NavEntry) {
        val route = entry.route
        if (route != null) onNavigate(route) else if (entry.action == EntryAction.NATIVE_CAST) viewModel.openNativeCast(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            // Holgura para que la ampliación del elemento enfocado no se recorte.
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BackdropHero(content = hero, heightDp = metrics.heroHeightDp, bodyLines = metrics.heroBodyLines, backdrop = backdrop)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Estado real: lo informa DisplayManager (no distingue HDMI de inalámbrica).
            StatusChip(
                text = if (externalDisplay?.name == null) "Pantalla externa: ninguna" else "Pantalla externa: ${externalDisplay?.name}",
                dotColor = if (externalDisplay?.name == null) OpenTvColors.Muted else OpenTvColors.Success,
            )
            outputTarget?.name?.let { StatusChip(text = "Salida: $it", dotColor = OpenTvColors.Success) }
            CastButton(onClick = { onNavigate(Routes.CONNECT) }, modifier = focusModifier("cast"))
            if (resizePending) {
                // La resolución forzada dura hasta restaurarla: se avisa en el inicio para no olvidarla.
                TvButton(
                    text = "Resolución 16:9 forzada · Restaurar",
                    onClick = { onNavigate(Routes.MIRROR) },
                    horizontalPadding = 20.dp,
                )
            }
        }

        HomeRow(title = "Aplicaciones") {
            itemsIndexed(favorites, key = { _, app -> app.packageName }) { _, app ->
                val key = "app:${app.packageName}"
                AppTile(
                    app = app,
                    isFavorite = true,
                    loadIcon = viewModel.iconLoader::load,
                    onClick = { viewModel.appLaunch.onAppClick(app) },
                    onToggleFavorite = { viewModel.toggleFavorite(app) },
                    onFocus = {
                        lastFocus = key
                        setHero(HeroContent(app.label, "OK abre la app en el teléfono. Mantené OK (o Menú) para quitarla de favoritas.", "Mis aplicaciones"))
                    },
                    modifier = Modifier.width(metrics.appTileWidthDp.dp).height(metrics.appTileHeightDp.dp).then(focusModifier(key)),
                )
            }
            item(key = KEY_ADD) {
                AddAppCard(
                    onClick = { appPickerOpen = true },
                    modifier = Modifier.width(metrics.appTileWidthDp.dp).height(metrics.appTileHeightDp.dp).then(focusModifier(KEY_ADD)),
                )
            }
        }
        LaunchMessage(state = launchState)

        CatalogRows(
            state = catalog,
            metrics = metrics,
            viewModel = catalogVm,
            focusFor = ::focusModifier,
            onItemFocused = { key, item ->
                lastFocus = key
                hero = HeroContent(item.title, CatalogText.overviewOrNote(item), "${CatalogText.INFO_ONLY} · TMDB · ${CatalogText.metaLine(item)}")
                backdropItem = item
            },
            onItemClick = { detailItem = it },
            onOpenSettings = { onNavigate(Routes.SETTINGS_CATALOG) },
        )

        // Qué se puede reproducir de verdad hoy (no es el catálogo de arriba).
        HomeRow(title = "Fuentes de reproducción") {
            entryItems(NavCatalog.sources, metrics, ::open, { lastFocus = it }, ::setHero, ::focusModifier)
        }

        // Aviso fijo (solo texto): no ejecuta nada. Si el 16:9 queda girado o cortado, el paso manual conocido es este.
        Text(
            text = "Para utilizar correctamente el modo 16:9, activá la rotación automática del teléfono desde la barra de notificaciones y volvé a utilizar la función 16:9.",
            style = MaterialTheme.typography.bodyMedium,
            color = OpenTvColors.Warning,
        )

        // Cada tarjeta solo ABRE una pantalla que ya existe; ninguna toca la resolución.
        HomeRow(title = "Transmitir") {
            entryItems(NavCatalog.homeTransmit, metrics, ::open, { lastFocus = it }, ::setHero, ::focusModifier)
        }
        HomeRow(title = "Explorar") {
            entryItems(NavCatalog.explore, metrics, ::open, { lastFocus = it }, ::setHero, ::focusModifier)
        }
        if (catalog.tmdbConfigured) {
            Text(
                text = CatalogText.ATTRIBUTION + if (catalog.rpdbActive) " " + CatalogText.RPDB_NOTE else "",
                style = MaterialTheme.typography.bodyMedium,
                color = OpenTvColors.Muted,
            )
        }
    }
}

private const val KEY_ADD = "add-app"
private const val DEFAULT_TITLE = "OpenTV"
private val DEFAULT_TEXT = "Tu centro multimedia · ${BuildInfo.LABEL}"

private fun LazyListScope.entryItems(
    entries: List<NavEntry>,
    metrics: TvMetrics,
    open: (NavEntry) -> Unit,
    onFocused: (String) -> Unit,
    onHero: (HeroContent) -> Unit,
    focusFor: (String) -> Modifier,
) {
    for (entry in entries) {
        item(key = "entry:${entry.id}") {
            val key = "entry:${entry.id}"
            ActionCard(
                title = entry.title,
                subtitle = entry.subtitle,
                badge = entry.badge,
                onClick = { open(entry) },
                width = metrics.actionCardWidthDp.dp,
                height = metrics.actionCardHeightDp.dp,
                onFocus = {
                    onFocused(key)
                    onHero(HeroContent(entry.title, entry.hero))
                },
                modifier = focusFor(key),
            )
        }
    }
}

@Composable
private fun HomeRow(title: String, content: LazyListScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleLarge, color = OpenTvColors.OnBackground)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
            content = content,
        )
    }
}
