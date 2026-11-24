package com.opentv.homenova.extras

import android.content.Context
import android.content.Intent

/**
 * Accesos a las funciones de OpenTV (el espíritu del Home original). El Home 2 vive dentro del mismo APK que OpenTV, así que abre la
 * actividad principal de OpenTV pidiéndole una pantalla por el extra [EXTRA_ROUTE]. Las rutas deben coincidir con
 * `RouteRequest.ALLOWED` de la app (ahí se validan; una ruta desconocida se ignora).
 */
enum class OpenTvLink(val title: String, val subtitle: String, val route: String) {
    Iptv("Transmitir (IPTV)", "Pantalla a la TV, 1080p60", "iptv"),
    Dlna("Transmitir por DLNA", "Para TV sin Miracast", "dlna_mirror"),
    Connections("Conexiones", "Miracast, DLNA, PS3", "connections"),
    Media("Multimedia", "Reproducir y catálogo", "media"),
    Apps("Apps de OpenTV", "Administrar y abrir", "apps"),
    Remote("Control remoto", "Usar el celular de mando", "remote"),
    Settings("Ajustes de OpenTV", "Catálogo y herramientas", "settings"),
    ;

    fun launch(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_MAIN)
            .setClassName(context.packageName, MAIN_ACTIVITY)
            .putExtra(EXTRA_ROUTE, route)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    companion object {
        const val MAIN_ACTIVITY = "com.opentv.app.MainActivity"
        const val EXTRA_ROUTE = "opentv_route"
    }
}
