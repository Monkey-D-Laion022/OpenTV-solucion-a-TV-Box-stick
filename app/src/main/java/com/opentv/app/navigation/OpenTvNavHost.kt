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
import com.opentv.app.ui.settings.SettingsScreen
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

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(onNavigate = { route -> navController.navigate(route) })
        }
        composable(Routes.APPS) {
            AppsScreen(onBack = goBack)
        }
        composable(Routes.CONNECT) {
            ConnectTvScreen(onBack = goBack)
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onOpenDiagnostics = { navController.navigate(Routes.DEVICE) },
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
