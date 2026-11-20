package com.opentv.home

import android.text.format.DateFormat
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Date

private val Accent = Color(0xFF26C6DA) // turquesa; cambialo por el de tu tema
private val Background = Color(0xFF0E1419)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun OpenTvHome(state: HomeState, onVoice: () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Color.Black,
            surface = Background,
            onSurface = Color(0xFFE6EEF2),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                // Botón de búsqueda o asistente del control remoto
                .onPreviewKeyEvent { event ->
                    val isVoiceKey = event.key == Key.Search ||
                        event.key == Key(android.view.KeyEvent.KEYCODE_VOICE_ASSIST)
                    if (isVoiceKey && event.type == KeyEventType.KeyUp) {
                        onVoice()
                        true
                    } else {
                        false
                    }
                }
                .padding(top = 24.dp),
        ) {
            TopBar(state, onVoice)

            if (state.status.isNotBlank()) {
                Text(
                    text = state.status,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 48.dp, vertical = 8.dp),
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                when (state.tab) {
                    HomeTab.Home -> HomeTabContent(state)
                    HomeTab.Apps -> AppsTabContent(state)
                    HomeTab.Search -> SearchTabContent(state)
                    HomeTab.Settings -> SettingsTabContent(state)
                }
            }
        }

        state.menuApp?.let { AppMenuDialog(state, it) }
        if (state.quickPanelOpen) QuickPanel(state)
        if (state.profileDialogOpen) ProfileDialog(state)
    }
}

// ---------------------------------------------------------------------------
// Barra superior: pestañas, red, reloj y voz
// ---------------------------------------------------------------------------

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TopBar(state: HomeState, onVoice: () -> Unit) {
    val context = LocalContext.current
    var clock by remember { mutableStateOf("") }
    var network by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            clock = DateFormat.getTimeFormat(context).format(Date())
            network = NetworkStatus.label(context)
            delay(15_000)
        }
    }

    val voiceFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { voiceFocus.requestFocus() } }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("OpenTV", fontSize = 28.sp, color = Accent, modifier = Modifier.padding(end = 12.dp))
        HomeTab.values().forEach { tab ->
            TabButton(tab.title, selected = state.tab == tab) { state.tab = tab }
        }
        Box(modifier = Modifier.weight(1f))
        Text(network, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(clock, fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface)
        Button(onClick = { state.profileDialogOpen = true }) { Text(state.activeProfile.name) }
        Button(onClick = onVoice, modifier = Modifier.focusRequester(voiceFocus)) {
            Text(if (state.listening) "Escuchando…" else "Voz")
        }
        Button(onClick = { state.quickPanelOpen = true }) { Text("Ajustes rápidos") }
    }
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 6..11 -> "Buenos días"
    in 12..19 -> "Buenas tardes"
    else -> "Buenas noches"
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TabButton(title: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.colors(
            containerColor = if (selected) Accent.copy(alpha = 0.25f) else Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = Accent,
            focusedContentColor = Color.Black,
        ),
    ) {
        Text(title)
    }
}

// ---------------------------------------------------------------------------
// Pestaña Inicio: banner destacado + filas
// ---------------------------------------------------------------------------

@Composable
private fun HomeTabContent(state: HomeState) {
    if (!state.loaded) {
        CenterMessage("Cargando aplicaciones…")
        return
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
    ) {
        item {
            Text(
                text = greeting(),
                fontSize = 26.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 48.dp),
            )
        }
        item { FeaturedBanner(state) }
        val nowPlaying = NotificationHub.nowPlaying
        if (nowPlaying.isNotEmpty()) item { NowPlayingRow(nowPlaying, state) }
        val favorites = state.favorites
        if (favorites.isNotEmpty()) item { SectionRow("Favoritos", favorites, state) }
        val recents = state.recents
        if (recents.isNotEmpty()) item { SectionRow("Recientes", recents, state) }
        val notifications = NotificationHub.notifications
        if (notifications.isNotEmpty()) item { NotificationsRow(notifications, state) }
        val tvApps = state.tvApps
        if (tvApps.isNotEmpty()) item { SectionRow("Apps de TV", tvApps, state) }
        item { SectionRow("Todas las apps", state.visibleApps, state) }
        if (!state.notifAccess) item { NotificationAccessHint(state) }
        item { ShortcutsRow(state) }
        if (favorites.isEmpty()) {
            item {
                Text(
                    "Tip: mantené presionada una app para fijarla en Favoritos, o decí «fijar» y su nombre.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 48.dp),
                )
            }
        }
    }
}

/** Banner destacado: el fondo hace un fundido cuando cambia la app enfocada, como en Google TV. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FeaturedBanner(state: HomeState) {
    val current = state.featured

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp)
            .height(240.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF14202A)),
    ) {
        Crossfade(targetState = current, label = "featured") { app ->
            val banner = app?.banner
            val icon = app?.icon
            Box(modifier = Modifier.fillMaxSize()) {
                if (banner != null) {
                    Image(
                        bitmap = banner,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alpha = 0.6f,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // Degradado de lectura: izquierda y abajo, como el banner de Android TV
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.horizontalGradient(listOf(Color(0xF2101820), Color.Transparent))),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC0E1419)))),
                )
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(28.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    if (icon != null) {
                        Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(88.dp))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(app?.label ?: "OpenTV", fontSize = 34.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            text = when {
                                app == null -> "Tu centro multimedia"
                                state.isFavorite(app) -> "★ Favorita"
                                app.isTvApp -> "App de TV"
                                else -> "Aplicación"
                            },
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (app != null) {
                            Button(onClick = { state.open(app) }) { Text("Abrir") }
                        }
                    }
                }
            }
        }
    }
}

/** Fila de accesos rápidos al sistema (Wi-Fi, Bluetooth, sonido…), al final del inicio. */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun ShortcutsRow(state: HomeState) {
    Column {
        Text(
            text = "Accesos rápidos",
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 48.dp, bottom = 8.dp),
        )
        LazyRow(
            modifier = Modifier.focusRestorer(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
        ) {
            items(SystemShortcut.values().toList(), key = { it.name }) { shortcut ->
                Card(
                    onClick = { state.openSystem(shortcut) },
                    scale = CardDefaults.scale(focusedScale = 1.08f),
                    border = CardDefaults.border(
                        focusedBorder = Border(border = BorderStroke(3.dp, Accent), shape = RoundedCornerShape(12.dp)),
                    ),
                    modifier = Modifier.width(180.dp),
                ) {
                    Text(
                        text = shortcut.title,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
        }
    }
}

/** Fila de apps. `focusRestorer` hace que, al volver a la fila, el foco regrese a la última tarjeta. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SectionRow(title: String, apps: List<AppEntry>, state: HomeState) {
    Column {
        Text(
            text = title,
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 48.dp, bottom = 8.dp),
        )
        LazyRow(
            modifier = Modifier.focusRestorer(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
        ) {
            items(apps, key = { it.packageName }) { app -> AppCard(app, state) }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AppCard(app: AppEntry, state: HomeState, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Column(modifier = modifier.width(200.dp)) {
        // Tarjeta 16:9 como en Android TV: banner de la app, o su ícono sobre fondo oscuro si no tiene
        Card(
            onClick = { state.open(app) },
            onLongClick = { state.menuApp = app },
            shape = CardDefaults.shape(shape = shape),
            scale = CardDefaults.scale(focusedScale = 1.08f),
            border = CardDefaults.border(
                focusedBorder = Border(border = BorderStroke(3.dp, Accent), shape = shape),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .onFocusChanged { if (it.hasFocus) state.focusedApp = app },
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val banner = app.banner
                val icon = app.icon
                if (banner != null) {
                    Image(
                        bitmap = banner,
                        contentDescription = app.label,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.linearGradient(listOf(Color(0xFF1B2A35), Color(0xFF111A22)))),
                    )
                    if (icon != null) {
                        Image(bitmap = icon, contentDescription = app.label, modifier = Modifier.size(56.dp))
                    }
                }
                if (state.isFavorite(app)) {
                    Text(
                        text = "★",
                        fontSize = 16.sp,
                        color = Accent,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                    )
                }
            }
        }
        Text(
            text = app.label,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Pestaña Apps: grilla completa
// ---------------------------------------------------------------------------

@Composable
private fun AppsTabContent(state: HomeState) {
    if (!state.loaded) {
        CenterMessage("Cargando aplicaciones…")
        return
    }
    AppGrid(state.visibleApps, state)
}

@Composable
private fun AppGrid(apps: List<AppEntry>, state: HomeState) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(210.dp),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(apps, key = { it.packageName }) { app -> AppCard(app, state, Modifier.fillMaxWidth()) }
    }
}

// ---------------------------------------------------------------------------
// Pestaña Buscar: texto o voz
// ---------------------------------------------------------------------------

@Composable
private fun SearchTabContent(state: HomeState) {
    Column(modifier = Modifier.fillMaxSize()) {
        BasicTextField(
            value = state.query,
            onValueChange = { state.query = it },
            singleLine = true,
            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 24.sp),
            cursorBrush = SolidColor(Accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp, vertical = 16.dp),
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1B262F))
                        .padding(16.dp),
                ) {
                    if (state.query.isEmpty()) {
                        Text(
                            "Escribí o decí qué buscar…",
                            fontSize = 24.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                }
            },
        )
        val results = state.searchResults
        when {
            state.query.isBlank() -> CenterMessage("Probá decir «buscar YouTube»")
            results.isEmpty() -> CenterMessage("Sin resultados para «${state.query}»")
            else -> AppGrid(results, state)
        }
    }
}

// ---------------------------------------------------------------------------
// Pestaña Ajustes: accesos rápidos al sistema
// ---------------------------------------------------------------------------

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingsTabContent(state: HomeState) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(220.dp),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(SystemShortcut.values().toList(), key = { it.name }) { shortcut ->
            Card(onClick = { state.openSystem(shortcut) }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = shortcut.title,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        if (state.hiddenPkgs.isNotEmpty()) {
            item(key = "restore_hidden") {
                Card(onClick = { state.restoreHidden() }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Restaurar apps ocultas (${state.hiddenPkgs.size})",
                        fontSize = 18.sp,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Panel lateral de ajustes rápidos (botón de la barra superior)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QuickPanel(state: HomeState) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    val close = { state.quickPanelOpen = false }

    Dialog(
        onDismissRequest = { close() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x99000000)),
            contentAlignment = Alignment.CenterEnd,
        ) {
            LazyColumn(
                modifier = Modifier
                    .width(360.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF121C24)),
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text("Ajustes rápidos", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurface)
                }
                items(SystemShortcut.values().toList(), key = { it.name }) { shortcut ->
                    Button(
                        onClick = { close(); state.openSystem(shortcut) },
                        modifier = if (shortcut == SystemShortcut.values().first()) {
                            Modifier.focusRequester(firstFocus)
                        } else {
                            Modifier
                        },
                    ) {
                        Text(shortcut.title)
                    }
                }
                if (state.hiddenPkgs.isNotEmpty()) {
                    item {
                        Button(onClick = { state.restoreHidden(); close() }) {
                            Text("Restaurar apps ocultas (${state.hiddenPkgs.size})")
                        }
                    }
                }
                item {
                    Button(onClick = { state.updateShowNotifText(!state.showNotifText) }) {
                        Text(if (state.showNotifText) "Ocultar texto de notificaciones" else "Mostrar texto de notificaciones")
                    }
                }
                if (NotificationHub.notifications.isNotEmpty()) {
                    item {
                        Button(onClick = { NotificationHub.dismissAll(); close() }) {
                            Text("Descartar todas las notificaciones del dispositivo")
                        }
                    }
                }
                item { Button(onClick = { close() }) { Text("Cerrar") } }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Seguir viendo (sesiones de medios) y notificaciones: requieren «Acceso a notificaciones»
// ---------------------------------------------------------------------------

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun NowPlayingRow(entries: List<NowPlaying>, state: HomeState) {
    Column {
        Text(
            text = "Seguir viendo",
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 48.dp, bottom = 8.dp),
        )
        LazyRow(
            modifier = Modifier.focusRestorer(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
        ) {
            items(entries) { item ->
                Card(
                    onClick = { state.openNowPlaying(item) },
                    onLongClick = { NotificationHub.togglePlayback(item) },
                    scale = CardDefaults.scale(focusedScale = 1.06f),
                    border = CardDefaults.border(
                        focusedBorder = Border(border = BorderStroke(3.dp, Accent), shape = RoundedCornerShape(12.dp)),
                    ),
                    modifier = Modifier.width(300.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(item.appLabel, fontSize = 12.sp, color = Accent, maxLines = 1)
                        Text(item.title, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (item.subtitle.isNotBlank()) {
                            Text(
                                item.subtitle,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = if (item.playing) "▶ Reproduciendo · mantené para pausar" else "❚❚ En pausa · mantené para reanudar",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun NotificationsRow(entries: List<NotifItem>, state: HomeState) {
    Column {
        Text(
            text = "Notificaciones",
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 48.dp, bottom = 8.dp),
        )
        LazyRow(
            modifier = Modifier.focusRestorer(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
        ) {
            items(entries) { item ->
                Card(
                    onClick = { state.openNotification(item) },
                    onLongClick = { NotificationHub.dismiss(item) },
                    scale = CardDefaults.scale(focusedScale = 1.06f),
                    border = CardDefaults.border(
                        focusedBorder = Border(border = BorderStroke(3.dp, Accent), shape = RoundedCornerShape(12.dp)),
                    ),
                    modifier = Modifier.width(300.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(item.appLabel, fontSize = 12.sp, color = Accent, maxLines = 1)
                        if (state.showNotifText) {
                            Text(
                                item.title.ifBlank { "Notificación" },
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (item.text.isNotBlank()) {
                                Text(
                                    item.text,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        } else {
                            // Por privacidad (la pantalla puede verla cualquiera) no se muestra el contenido
                            Text(
                                "Notificación nueva",
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (item.clearable) {
                            Text(
                                "Mantené presionado para descartar",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Aviso (solo mientras falte el permiso) para activar «Seguir viendo» y las notificaciones. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NotificationAccessHint(state: HomeState) {
    Card(
        onClick = { state.openSystem(SystemShortcut.NotificationAccess) },
        scale = CardDefaults.scale(focusedScale = 1.03f),
        border = CardDefaults.border(
            focusedBorder = Border(border = BorderStroke(3.dp, Accent), shape = RoundedCornerShape(12.dp)),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Activá «Seguir viendo» y las notificaciones", fontSize = 18.sp)
            Text(
                "Concedé «Acceso a notificaciones» a OpenTV en los ajustes del sistema. " +
                    "Solo se usa en este dispositivo: no se guarda ni se envía nada.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Perfiles locales (cada uno con sus favoritos, recientes y apps ocultas)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ProfileDialog(state: HomeState) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    var newName by remember { mutableStateOf("") }
    var confirmDeleteId by remember { mutableStateOf<String?>(null) }
    val close = { state.profileDialogOpen = false }

    Dialog(onDismissRequest = { close() }) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Perfiles", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface)
            state.profiles.forEachIndexed { index, profile ->
                val active = profile.id == state.activeProfileId
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { state.switchProfile(profile.id); close() },
                        modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    ) {
                        Text(if (active) "● ${profile.name}" else profile.name)
                    }
                    if (state.profiles.size > 1) {
                        val confirming = confirmDeleteId == profile.id
                        Button(
                            onClick = {
                                if (confirming) {
                                    state.deleteProfile(profile.id)
                                    confirmDeleteId = null
                                } else {
                                    confirmDeleteId = profile.id
                                }
                            },
                        ) {
                            Text(if (confirming) "¿Seguro? Se borran sus favoritos" else "Eliminar")
                        }
                    }
                }
            }
            BasicTextField(
                value = newName,
                onValueChange = { newName = it },
                singleLine = true,
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp),
                cursorBrush = SolidColor(Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1B262F))
                            .padding(14.dp),
                    ) {
                        if (newName.isEmpty()) {
                            Text("Nombre del perfil nuevo", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        inner()
                    }
                },
            )
            Button(onClick = { state.addProfile(newName); newName = ""; close() }) { Text("Crear perfil") }
            Button(onClick = { close() }) { Text("Cerrar") }
        }
    }
}

// ---------------------------------------------------------------------------
// Menú contextual de una app (mantener presionado)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AppMenuDialog(state: HomeState, app: AppEntry) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(app.packageName) { runCatching { firstFocus.requestFocus() } }
    val close = { state.menuApp = null }
    val favorite = state.isFavorite(app)

    Dialog(onDismissRequest = { close() }) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(app.label, fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface)
            Button(
                onClick = { state.setFavorite(app, !favorite); close() },
                modifier = Modifier.focusRequester(firstFocus),
            ) {
                Text(if (favorite) "Quitar de favoritos" else "Fijar en favoritos")
            }
            if (favorite) {
                Button(onClick = { state.moveFavorite(app, -1) }) { Text("Mover a la izquierda") }
                Button(onClick = { state.moveFavorite(app, +1) }) { Text("Mover a la derecha") }
            }
            Button(onClick = { state.hide(app); close() }) { Text("Ocultar de la lista") }
            Button(onClick = { state.openAppInfo(app); close() }) { Text("Información de la app") }
            Button(onClick = { state.uninstall(app); close() }) { Text("Desinstalar") }
            Button(onClick = { close() }) { Text("Cerrar") }
        }
    }
}

@Composable
private fun CenterMessage(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
