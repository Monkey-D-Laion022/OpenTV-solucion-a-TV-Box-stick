package com.opentv.homenova.navigation

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Input
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector

data class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

object Routes {
    const val HOME = "home"
    const val APPS = "apps"
    const val DISCOVER = "discover"
    const val LIVE = "live"
    const val INPUTS = "inputs"
    const val NOTIFICATIONS = "notifications"
    const val SETTINGS = "settings"
    const val VOICE = "voice"
    const val SEARCH = "search?q={q}"
    const val PLAYER = "player/{mediaId}"

    fun search(query: String): String = "search?q=${Uri.encode(query)}"
    fun player(mediaId: String): String = "player/$mediaId"

    fun isTopLevel(route: String?): Boolean = mainNavItems.any { it.route == route }
}

val mainNavItems = listOf(
    NavItem(Routes.HOME, "Inicio", Icons.Rounded.Home),
    NavItem(Routes.APPS, "Aplicaciones", Icons.Rounded.Apps),
    NavItem(Routes.DISCOVER, "Descubrir", Icons.Rounded.Explore),
    NavItem(Routes.LIVE, "En vivo", Icons.Rounded.LiveTv),
    NavItem(Routes.INPUTS, "Entradas", Icons.Rounded.Input),
    NavItem(Routes.NOTIFICATIONS, "Notificaciones", Icons.Rounded.Notifications),
    NavItem(Routes.SETTINGS, "Ajustes", Icons.Rounded.Settings),
)
