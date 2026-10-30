package com.opentv.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.opentv.app.ui.apps.AppsScreen
import com.opentv.app.ui.connect.ConnectTvScreen
import com.opentv.app.ui.device.DeviceInfoScreen
import com.opentv.app.ui.home.HomeScreen
import com.opentv.app.ui.pending.PendingScreen
import com.opentv.app.ui.mirror.NativeMirrorScreen
import com.opentv.app.ui.remote.RemoteScreen
import com.opentv.app.ui.player.PlayerScreen
import com.opentv.app.ui.connections.ConnectionsScreen
import com.opentv.app.ui.media.MediaScreen
import com.opentv.app.ui.settings.SettingsHubScreen
import com.opentv.app.ui.shell.OpenTvShell
import com.opentv.app.ui.shell.SettingsCategory
import com.opentv.app.ui.shell.TopSection
import com.opentv.app.ui.settings.ToolsScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * @param homeRequests contador que sube cada vez que el usuario pulsa el botón Inicio con OpenTV
 * como launcher; al subir se vuelve a la pantalla de inicio. El valor 0 es el inicial y se ignora.
 */
@Composable
fun OpenTvNavHost(
    navController: NavHostController = rememberNavController(),
    homeRequests: Flow<Int> = emptyFlow(),
) {
    val goBack: () -> Unit = { navController.popBackStack() }

    LaunchedEffect(navController, homeRequests) {
        homeRequests.collect { count ->
            if (count > 0) navController.popBackStack(Routes.HOME, inclusive = false)
        }
    }

    // Cambiar de sección con la barra superior no apila pantallas: siempre se vuelve al inicio con Atrás.
    val goSection: (TopSection) -> Unit = { section ->
        navController.navigate(section.route) {
            popUpTo(Routes.HOME) { inclusive = false }
            launchSingleTop = true
        }
    }
    val open: (String) -> Unit = { route -> navController.navigate(route) }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            OpenTvShell(selected = TopSection.HOME, onSelect = goSection) { HomeScreen(onNavigate = open) }
        }
        composable(Routes.APPS) {
            OpenTvShell(selected = TopSection.APPS, onSelect = goSection) { AppsScreen(onBack = goBack, embedded = true) }
        }
        composable(Routes.MEDIA) {
            OpenTvShell(selected = TopSection.MEDIA, onSelect = goSection) { MediaScreen(onNavigate = open) }
        }
        composable(Routes.CONNECTIONS) {
            OpenTvShell(selected = TopSection.CONNECTIONS, onSelect = goSection) { ConnectionsScreen(onNavigate = open) }
        }
        composable(Routes.CONNECT) {
            ConnectTvScreen(onBack = goBack)
        }
        composable(Routes.PLAYER) {
            PlayerScreen(onBack = goBack)
        }
        composable(Routes.SETTINGS_CATALOG) {
            OpenTvShell(selected = TopSection.SETTINGS, onSelect = goSection) {
                SettingsHubScreen(onNavigate = open, initialCategoryId = SettingsCategory.CATALOG.id)
            }
        }
        composable(Routes.SETTINGS) {
            OpenTvShell(selected = TopSection.SETTINGS, onSelect = goSection) { SettingsHubScreen(onNavigate = open) }
        }
        composable(Routes.MIRROR) {
            NativeMirrorScreen(onBack = goBack, onOpenGuide = { navController.navigate(Routes.SHIZUKU_GUIDE) })
        }
        composable(Routes.BROWSER) {
            com.opentv.app.ui.browser.BrowserMirrorScreen(onBack = goBack)
        }
        composable(Routes.IPTV) {
            com.opentv.app.ui.iptv.IptvScreen(onBack = goBack)
        }
        composable(Routes.BENCH) {
            com.opentv.app.bench.BenchScreen(onBack = goBack)
        }
        composable(Routes.P2P_DISCOVERY) {
            com.opentv.app.miracast.P2pDiscoveryScreen(onBack = goBack)
        }
        composable(Routes.WFD_DIAGNOSTIC) {
            com.opentv.app.miracast.WfdDiagnosticScreen(onBack = goBack)
        }
        composable(Routes.SHIZUKU_GUIDE) {
            com.opentv.app.ui.shizuku.ShizukuGuideScreen(onBack = goBack)
        }
        composable(Routes.REMOTE) {
            RemoteScreen(onBack = goBack)
        }
        composable(Routes.TOOLS) {
            ToolsScreen(
                onOpenPlayer = { navController.navigate(Routes.PLAYER) },
                onBack = goBack,
            )
        }
        composable(Routes.DEVICE) {
            DeviceInfoScreen(onBack = goBack)
        }
        composable(
            route = Routes.PENDING_PATTERN,
            arguments = listOf(navArgument(Routes.PENDING_ARG) { type = NavType.StringType }),
        ) { entry ->
            val feature = PendingFeature.fromId(entry.arguments?.getString(Routes.PENDING_ARG))
            PendingScreen(feature = feature, onBack = goBack)
        }
    }
}
