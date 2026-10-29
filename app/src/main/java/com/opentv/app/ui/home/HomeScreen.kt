package com.opentv.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.opentv.app.core.BuildInfo
import com.opentv.app.navigation.Routes
import com.opentv.app.ui.apps.AddAppCard
import com.opentv.app.ui.apps.AppPickerDialog
import com.opentv.app.ui.apps.AppTile
import com.opentv.app.ui.apps.LaunchMessage
import com.opentv.app.ui.apps.PortraitWarningDialog
import com.opentv.app.ui.components.StatusChip
import com.opentv.app.ui.components.TvButton
import com.opentv.app.ui.components.TvCard
import com.opentv.app.ui.connect.CastButton
import com.opentv.app.ui.shell.EntryAction
import com.opentv.app.ui.shell.NavCatalog
import com.opentv.app.ui.shell.NavEntry
import com.opentv.app.ui.theme.OpenTvColors

private val CARD_WIDTH = 250.dp

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
    var hero by remember { mutableStateOf(HeroInfo(DEFAULT_TITLE, DEFAULT_TEXT)) }
    val context = LocalContext.current

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
        val restored = lastFocus.isNotEmpty() && runCatching { requesterFor(lastFocus).requestFocus() }.getOrDefault(false)
        if (!restored) runCatching { requesterFor(firstKey).requestFocus() }.getOrDefault(false)
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
        HeroBanner(hero)

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
                        hero = HeroInfo(app.label, "OK abre la app en el teléfono. Mantené OK (o Menú) para quitarla de favoritas.")
                    },
                    modifier = Modifier.width(150.dp).height(148.dp).then(focusModifier(key)),
                )
            }
            item(key = KEY_ADD) {
                AddAppCard(
                    onClick = { appPickerOpen = true },
                    modifier = Modifier.width(150.dp).height(148.dp).then(focusModifier(KEY_ADD)),
                )
            }
        }
        LaunchMessage(state = launchState)

        // Aviso fijo (solo texto): no ejecuta nada. Si el 16:9 queda girado o cortado, el paso manual conocido es este.
        Text(
            text = "Para utilizar correctamente el modo 16:9, activá la rotación automática del teléfono desde la barra de notificaciones y volvé a utilizar la función 16:9.",
            style = MaterialTheme.typography.bodyMedium,
            color = OpenTvColors.Warning,
        )

        // Cada tarjeta solo ABRE una pantalla que ya existe; ninguna toca la resolución.
        HomeRow(title = "Transmitir") {
            entryItems(NavCatalog.homeTransmit, ::open, { lastFocus = it }, { hero = it }, ::focusModifier)
        }
        HomeRow(title = "Explorar") {
            entryItems(NavCatalog.explore, ::open, { lastFocus = it }, { hero = it }, ::focusModifier)
        }
    }
}

private const val KEY_ADD = "add-app"
private const val DEFAULT_TITLE = "OpenTV"
private val DEFAULT_TEXT = "Tu centro multimedia · ${BuildInfo.LABEL}"

private fun LazyListScope.entryItems(
    entries: List<NavEntry>,
    open: (NavEntry) -> Unit,
    onFocused: (String) -> Unit,
    onHero: (HeroInfo) -> Unit,
    focusFor: (String) -> Modifier,
) {
    for (entry in entries) {
        item(key = "entry:${entry.id}") {
            val key = "entry:${entry.id}"
            TvCard(
                title = entry.title,
                subtitle = entry.subtitle,
                badge = entry.badge,
                onClick = { open(entry) },
                onFocus = {
                    onFocused(key)
                    onHero(HeroInfo(entry.title, entry.hero))
                },
                modifier = Modifier.width(CARD_WIDTH).then(focusFor(key)),
            )
        }
    }
}

data class HeroInfo(val title: String, val text: String)

/**
 * Banner destacado fijo (como el «Browse» de Android TV): degradado turquesa suave y el texto del elemento enfocado.
 * Sin imágenes: no hay carátulas reales todavía. Alto fijo para que lo de abajo no salte al navegar.
 */
@Composable
private fun HeroBanner(info: HeroInfo) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(
                Brush.horizontalGradient(listOf(OpenTvColors.Primary.copy(alpha = 0.28f), OpenTvColors.SurfaceFocused, OpenTvColors.Background)),
                RoundedCornerShape(20.dp),
            )
            .padding(horizontal = 26.dp, vertical = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = info.title, style = MaterialTheme.typography.headlineMedium, color = OpenTvColors.OnBackground, maxLines = 1)
            Text(text = info.text, style = MaterialTheme.typography.bodyLarge, color = OpenTvColors.Muted, maxLines = 2)
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
