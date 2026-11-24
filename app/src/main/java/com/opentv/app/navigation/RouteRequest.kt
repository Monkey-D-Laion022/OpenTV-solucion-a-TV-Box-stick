package com.opentv.app.navigation

/**
 * Pedido de abrir una pantalla concreta de OpenTV desde fuera (por ejemplo, desde el Home 2 de prueba). Sin dependencias de Android para
 * poder probarlo con JUnit simple. [id] sube con cada pedido para que pedir la misma ruta dos veces vuelva a emitir.
 */
data class RouteRequest(val id: Int, val route: String?) {
    companion object {
        /** Clave del extra del Intent que lleva la ruta. */
        const val EXTRA = "opentv_route"

        /** Solo estas rutas se aceptan desde fuera: cualquier otra se ignora (no se navega a destinos arbitrarios). */
        val ALLOWED: Set<String> = setOf(
            Routes.HOME, Routes.MEDIA, Routes.CONNECTIONS, Routes.CONNECT, Routes.APPS, Routes.SETTINGS,
            Routes.IPTV, Routes.DLNA_MIRROR, Routes.BROWSER, Routes.LINK_SEND, Routes.LINK_RECEIVE, Routes.PLAYER,
            Routes.MIRROR, Routes.REMOTE, Routes.TOOLS, Routes.DEVICE,
        )

        fun accept(route: String?): String? = route?.takeIf { it in ALLOWED }
    }
}
