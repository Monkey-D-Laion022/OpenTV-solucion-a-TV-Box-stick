package com.opentv.homenova

import android.content.Intent
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsInputAntenna
import androidx.compose.material.icons.rounded.SettingsInputComponent
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.opentv.homenova.data.LauncherSettings
import com.opentv.homenova.extras.OpenTvLink
import com.opentv.homenova.extras.SystemShortcut
import com.opentv.homenova.ui.components.ShortcutItem
import com.opentv.homenova.navigation.Routes
import com.opentv.homenova.navigation.mainNavItems
import com.opentv.homenova.ui.components.AmbientBackground
import com.opentv.homenova.ui.components.DialogAction
import com.opentv.homenova.ui.components.NovaDialog
import com.opentv.homenova.ui.components.NovaLogo
import com.opentv.homenova.ui.components.SideNavRail
import com.opentv.homenova.ui.components.TopBar
import com.opentv.homenova.ui.screens.AppsScreen
import com.opentv.homenova.ui.screens.DiscoverScreen
import com.opentv.homenova.ui.screens.HomeScreen
import com.opentv.homenova.ui.screens.InputsScreen
import com.opentv.homenova.ui.screens.LiveScreen
import com.opentv.homenova.ui.screens.NotificationsScreen
import com.opentv.homenova.ui.screens.PlayerScreen
import com.opentv.homenova.ui.screens.SearchScreen
import com.opentv.homenova.ui.screens.SettingsScreen
import com.opentv.homenova.ui.screens.VoiceScreen
import com.opentv.homenova.ui.theme.AccentStyle
import com.opentv.homenova.ui.theme.LocalDpadSounds
import com.opentv.homenova.ui.theme.LocalNovaPalette
import com.opentv.homenova.ui.theme.NovaInk
import com.opentv.homenova.ui.theme.NovaTheme
import com.opentv.homenova.ui.theme.accentStyleFor
import com.opentv.homenova.voice.VoiceAction
import com.opentv.homenova.voice.VoiceApp
import com.opentv.homenova.voice.VoiceAssistant
import com.opentv.homenova.voice.VoiceCatalog
import com.opentv.homenova.voice.VoiceMedia
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

@Composable
fun NovaTvApp(voiceRequests: Flow<Int> = emptyFlow(), viewModel: LauncherViewModel = viewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val prefs = settings
    val accent = accentStyleFor(prefs?.accent ?: AccentStyle.AURORA.key)

    NovaTheme(accent = accent) {
        CompositionLocalProvider(LocalDpadSounds provides (prefs?.dpadSounds ?: true)) {
            if (prefs == null) {
                LoadingScreen()
            } else {
                LauncherContent(viewModel = viewModel, prefs = prefs, voiceRequests = voiceRequests)
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NovaInk),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            NovaLogo(size = 64.dp)
            Text(
                text = "OPENTV",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
    }
}

@Composable
private fun LauncherContent(viewModel: LauncherViewModel, prefs: LauncherSettings, voiceRequests: Flow<Int>) {
    val context = LocalContext.current
    val palette = LocalNovaPalette.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route

    var railExpanded by remember { mutableStateOf(false) }
    var detailMediaId by remember { mutableStateOf<String?>(null) }

    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val allApps by viewModel.allAppsForManagement.collectAsStateWithLifecycle()
    val inputs by viewModel.inputs.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val nowPlaying by viewModel.nowPlayingTitle.collectAsStateWithLifecycle()
    val unread = if (prefs.dndEnabled) 0 else notifications.count { !it.read }
    val visibleNotifications = if (prefs.notificationsEnabled) notifications else emptyList()

    val immersive = route == Routes.PLAYER || route == Routes.VOICE || route == Routes.NOTIFICATIONS
    val isDefaultLauncher = remember(route) { viewModel.isDefaultLauncher() }

    val voiceCatalog = remember(allApps, viewModel.catalogue) {
        VoiceCatalog(
            apps = allApps.map { VoiceApp(it.packageName, it.label) },
            media = (viewModel.catalogue + viewModel.liveChannels).map {
                VoiceMedia(it.id, it.title)
            },
        )
    }

    // Text-to-speech is optional: if no engine is installed, feedback remains on
    // screen and the command still executes.
    val ttsHolder = remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        var engineRef: TextToSpeech? = null
        val engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engineRef?.language = Locale.forLanguageTag(prefs.voiceLanguage)
            }
        }
        engineRef = engine
        ttsHolder.value = engine
        onDispose {
            engine.stop()
            engine.shutdown()
        }
    }
    LaunchedEffect(prefs.voiceLanguage) {
        runCatching { ttsHolder.value?.language = Locale.forLanguageTag(prefs.voiceLanguage) }
    }

    fun speak(text: String) {
        runCatching {
            ttsHolder.value?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nova-tv")
        }
    }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun navigateTop(destination: String) {
        val target = if (destination == "search") Routes.search("") else destination
        if (target == navController.currentDestination?.route) return
        navController.navigate(target) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openVoice() {
        if (prefs.useSystemAssistant && VoiceAssistant.launchSystemAssistant(context)) return
        navController.navigate(Routes.VOICE)
    }

    // Botón de Asistente/Voz del control remoto (lo reenvía la actividad): abre el asistente igual que el micrófono de la barra.
    LaunchedEffect(voiceRequests) {
        voiceRequests.collect { count -> if (count > 0) openVoice() }
    }

    val openTvShortcuts = remember(context) {
        listOf(
            ShortcutItem(OpenTvLink.Iptv.title, OpenTvLink.Iptv.subtitle, Icons.Rounded.Tv) { OpenTvLink.Iptv.launch(context) },
            ShortcutItem(OpenTvLink.Dlna.title, OpenTvLink.Dlna.subtitle, Icons.Rounded.Cast) { OpenTvLink.Dlna.launch(context) },
            ShortcutItem(OpenTvLink.Connections.title, OpenTvLink.Connections.subtitle, Icons.Rounded.SettingsInputAntenna) { OpenTvLink.Connections.launch(context) },
            ShortcutItem(OpenTvLink.Media.title, OpenTvLink.Media.subtitle, Icons.Rounded.Movie) { OpenTvLink.Media.launch(context) },
            ShortcutItem(OpenTvLink.Apps.title, OpenTvLink.Apps.subtitle, Icons.Rounded.Apps) { OpenTvLink.Apps.launch(context) },
            ShortcutItem(OpenTvLink.Remote.title, OpenTvLink.Remote.subtitle, Icons.Rounded.SettingsInputComponent) { OpenTvLink.Remote.launch(context) },
            ShortcutItem(OpenTvLink.Settings.title, OpenTvLink.Settings.subtitle, Icons.Rounded.Settings) { OpenTvLink.Settings.launch(context) },
        )
    }
    val systemShortcuts = remember(context) {
        listOf(
            ShortcutItem(SystemShortcut.Wifi.title, SystemShortcut.Wifi.subtitle, Icons.Rounded.Wifi) { SystemShortcut.Wifi.launch(context) },
            ShortcutItem(SystemShortcut.Bluetooth.title, SystemShortcut.Bluetooth.subtitle, Icons.Rounded.Bluetooth) { SystemShortcut.Bluetooth.launch(context) },
            ShortcutItem(SystemShortcut.Sound.title, SystemShortcut.Sound.subtitle, Icons.Rounded.VolumeUp) { SystemShortcut.Sound.launch(context) },
            ShortcutItem(SystemShortcut.Display.title, SystemShortcut.Display.subtitle, Icons.Rounded.Brightness6) { SystemShortcut.Display.launch(context) },
            ShortcutItem(SystemShortcut.Cast.title, SystemShortcut.Cast.subtitle, Icons.Rounded.Cast) { SystemShortcut.Cast.launch(context) },
            ShortcutItem(SystemShortcut.DateTime.title, SystemShortcut.DateTime.subtitle, Icons.Rounded.Schedule) { SystemShortcut.DateTime.launch(context) },
            ShortcutItem(SystemShortcut.Apps.title, SystemShortcut.Apps.subtitle, Icons.Rounded.Apps) { SystemShortcut.Apps.launch(context) },
            ShortcutItem(SystemShortcut.All.title, SystemShortcut.All.subtitle, Icons.Rounded.Settings) { SystemShortcut.All.launch(context) },
        )
    }

    fun openHomeSettings() {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { toast("Este dispositivo no expone los ajustes de pantalla de inicio.") }
    }

    fun openSystemSettings() {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { toast("No se han podido abrir los ajustes del sistema.") }
    }

    fun executeVoice(action: VoiceAction): String? = when (action) {
        is VoiceAction.OpenApp -> {
            val launched = viewModel.launchApp(action.packageName)
            if (launched) {
                navController.popBackStack()
                speak("Abriendo ${action.label}")
                null
            } else {
                "No he podido abrir ${action.label}."
            }
        }
        is VoiceAction.OpenScreen -> {
            if (action.route == Routes.NOTIFICATIONS) {
                navController.navigate(Routes.NOTIFICATIONS) {
                    popUpTo(Routes.VOICE) { inclusive = true }
                }
            } else {
                navigateTop(action.route)
            }
            null
        }
        is VoiceAction.Search -> {
            navController.navigate(Routes.search(action.query)) {
                popUpTo(Routes.VOICE) { inclusive = true }
            }
            null
        }
        is VoiceAction.Play -> {
            navController.navigate(Routes.player(action.mediaId)) {
                popUpTo(Routes.VOICE) { inclusive = true }
            }
            null
        }
        is VoiceAction.Volume -> {
            VoiceAssistant.adjustVolume(context, action.delta)
            if (action.delta > 0) "Subiendo el volumen." else "Bajando el volumen."
        }
        VoiceAction.Mute -> {
            VoiceAssistant.mute(context)
            "Silencio activado."
        }
        VoiceAction.TellTime -> {
            val time = LocalTime.now().format(DateTimeFormatter.ofPattern("H:mm", Locale.forLanguageTag("es-ES")))
            speak("Son las $time")
            "Son las $time."
        }
        is VoiceAction.Speak -> {
            speak(action.text)
            action.text
        }
        VoiceAction.NoMatch -> "No te he entendido. Prueba a decir «abre Ajustes»."
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.ink),
    ) {
        if (!immersive && prefs.ambientBackground) {
            AmbientBackground(
                accent = palette.accent,
                secondary = palette.accentSecondary,
                animated = !prefs.reduceMotion,
            )
        }

        Row(Modifier.fillMaxSize()) {
            if (!immersive) {
                SideNavRail(
                    items = mainNavItems,
                    selectedRoute = route,
                    expanded = railExpanded,
                    onExpandedChange = { railExpanded = it },
                    onNavigate = { item -> navigateTop(item.route) },
                    modifier = Modifier.fillMaxHeight(),
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                if (!immersive) {
                    TopBar(
                        clock24h = prefs.clock24h,
                        nowPlayingTitle = nowPlaying,
                        unreadNotifications = unread,
                        onVoice = { openVoice() },
                        onSearch = { navController.navigate(Routes.search("")) },
                        onNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                        onSettings = { navigateTop(Routes.SETTINGS) },
                    )
                }

                NavHost(
                    navController = navController,
                    startDestination = startDestination(prefs),
                    modifier = Modifier.weight(1f),
                ) {
                    composable(Routes.HOME) {
                        HomeScreen(
                            featured = viewModel.featured,
                            continueWatching = viewModel.continueWatching,
                            trending = viewModel.trending,
                            recommendations = viewModel.recommendations,
                            liveChannels = viewModel.liveChannels,
                            apps = apps,
                            favourites = prefs.favourites,
                            showRecommendations = prefs.showRecommendations,
                            onPlay = { entry -> navController.navigate(Routes.player(entry.id)) },
                            onDetails = { entry -> detailMediaId = entry.id },
                            onAppClick = { app ->
                                if (!viewModel.launchApp(app)) toast("No se pudo abrir ${app.label}")
                            },
                            onAppLongClick = { navigateTop(Routes.APPS) },
                            onOpenApps = { navigateTop(Routes.APPS) },
                            onOpenDiscover = { navigateTop(Routes.DISCOVER) },
                            onOpenLive = { navigateTop(Routes.LIVE) },
                            onOpenSettings = { navigateTop(Routes.SETTINGS) },
                            openTvShortcuts = openTvShortcuts,
                            systemShortcuts = systemShortcuts,
                        )
                    }

                    composable(Routes.APPS) {
                        AppsScreen(
                            apps = allApps,
                            favourites = prefs.favourites,
                            hiddenApps = prefs.hiddenApps,
                            gridColumns = prefs.gridColumns,
                            onLaunch = { app ->
                                if (!viewModel.launchApp(app)) toast("No se pudo abrir ${app.label}")
                            },
                            onToggleFavourite = { app -> viewModel.toggleFavourite(app.packageName) },
                            onToggleHidden = { app -> viewModel.toggleHidden(app.packageName) },
                            onOpenInfo = { app ->
                                if (!viewModel.openAppInfo(app)) toast("No se pudo abrir la información de la app")
                            },
                            onUninstall = { app ->
                                if (!viewModel.uninstallApp(app)) toast("No se pudo iniciar la desinstalación")
                            },
                            onOpenSearch = { navController.navigate(Routes.search("")) },
                        )
                    }

                    composable(Routes.DISCOVER) {
                        DiscoverScreen(
                            catalogue = viewModel.catalogue,
                            onPlay = { entry -> navController.navigate(Routes.player(entry.id)) },
                            onDetails = { entry -> detailMediaId = entry.id },
                        )
                    }

                    composable(Routes.LIVE) {
                        LiveScreen(
                            channels = viewModel.liveChannels,
                            onPlay = { entry -> navController.navigate(Routes.player(entry.id)) },
                            clock24h = prefs.clock24h,
                        )
                    }

                    composable(Routes.INPUTS) {
                        InputsScreen(
                            inputs = inputs,
                            onOpenInput = { input -> viewModel.openInput(input) },
                            onOpenSystemPicker = { viewModel.openSystemInputPicker() },
                        )
                    }

                    composable(Routes.NOTIFICATIONS) {
                        NotificationsScreen(
                            notifications = visibleNotifications,
                            dndEnabled = prefs.dndEnabled,
                            onToggleDnd = {
                                viewModel.updateSettings { it.copy(dndEnabled = !it.dndEnabled) }
                            },
                            onDismiss = { id -> viewModel.dismissNotification(id) },
                            onMarkRead = { id -> viewModel.markNotificationRead(id) },
                            onMarkAllRead = { viewModel.markAllNotificationsRead() },
                            onClose = {
                                if (!navController.popBackStack()) navigateTop(Routes.HOME)
                            },
                        )
                    }

                    composable(Routes.SETTINGS) {
                        SettingsScreen(
                            settings = prefs,
                            apps = allApps,
                            voiceAvailable = VoiceAssistant.isRecognitionAvailable(context),
                            isDefaultLauncher = isDefaultLauncher,
                            onUpdate = { transform -> viewModel.updateSettings(transform) },
                            onToggleFavourite = { packageName -> viewModel.toggleFavourite(packageName) },
                            onToggleHidden = { packageName -> viewModel.toggleHidden(packageName) },
                            onOpenApps = { navigateTop(Routes.APPS) },
                            onOpenHomeSettings = { openHomeSettings() },
                            onOpenSystemSettings = { openSystemSettings() },
                            onTestVoice = { openVoice() },
                            onReset = { viewModel.resetSettings() },
                        )
                    }

                    composable(Routes.VOICE) {
                        BackHandler(onBack = { navController.popBackStack() })
                        VoiceScreen(
                            language = prefs.voiceLanguage,
                            catalog = voiceCatalog,
                            onAction = { action -> executeVoice(action) },
                            onClose = {
                                if (!navController.popBackStack()) navigateTop(Routes.HOME)
                            },
                        )
                    }

                    composable(
                        route = Routes.SEARCH,
                        arguments = listOf(
                            navArgument("q") {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                        ),
                    ) { entry ->
                        SearchScreen(
                            initialQuery = entry.arguments?.getString("q").orEmpty(),
                            apps = allApps,
                            media = viewModel.catalogue + viewModel.liveChannels,
                            onAppClick = { app ->
                                if (!viewModel.launchApp(app)) toast("No se pudo abrir ${app.label}")
                            },
                            onMediaClick = { media ->
                                navController.navigate(Routes.player(media.id))
                            },
                            onVoice = { openVoice() },
                            onBack = {
                                if (!navController.popBackStack()) navigateTop(Routes.HOME)
                            },
                        )
                    }

                    composable(
                        route = Routes.PLAYER,
                        arguments = listOf(navArgument("mediaId") { type = NavType.StringType }),
                    ) { entry ->
                        val mediaId = entry.arguments?.getString("mediaId").orEmpty()
                        val media = viewModel.findMedia(mediaId)
                        if (media == null) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "Contenido no encontrado",
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                            }
                        } else {
                            PlayerScreen(
                                media = media,
                                onBack = { navController.popBackStack() },
                                onNowPlayingChanged = { title ->
                                    viewModel.nowPlayingTitle.value = title
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    // Home behaves like a launcher: BACK does not drop the user out of the app.
    BackHandler(enabled = !immersive && route == Routes.HOME) { /* consume */ }
    BackHandler(enabled = !immersive && route != null && route != Routes.HOME) {
        if (!navController.popBackStack(Routes.HOME, false)) {
            navigateTop(Routes.HOME)
        }
    }

    val detail = detailMediaId?.let { viewModel.findMedia(it) }
    if (detail != null) {
        NovaDialog(
            title = detail.title,
            message = buildString {
                append(detail.description)
                append("\n\n")
                append("${detail.year} · ${detail.rating} · ${detail.durationMinutes} min")
                append("\n")
                append(detail.genres.joinToString(" / "))
            },
            onDismiss = { detailMediaId = null },
            actions = listOf(
                DialogAction(
                    label = "Reproducir",
                    primary = true,
                    onClick = {
                        detailMediaId = null
                        navController.navigate(Routes.player(detail.id))
                    },
                ),
                DialogAction(
                    label = "Cerrar",
                    onClick = { detailMediaId = null },
                ),
            ),
        )
    }
}

private fun startDestination(prefs: LauncherSettings): String = when (prefs.startScreen) {
    "apps" -> Routes.APPS
    "discover" -> Routes.DISCOVER
    "live" -> Routes.LIVE
    else -> Routes.HOME
}
