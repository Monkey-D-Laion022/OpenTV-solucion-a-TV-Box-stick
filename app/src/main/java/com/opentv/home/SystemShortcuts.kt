package com.opentv.home

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings as AndroidSettings

/**
 * Accesos directos a pantallas del sistema. Solo abren la pantalla de ajustes correspondiente:
 * no cambian resolución, densidad ni nada del 16:9.
 */
enum class SystemShortcut(
    val title: String,
    private val action: String,
    /** Frases (ya normalizadas) que activan el acceso por voz. */
    val keywords: List<String>,
) {
    AllSettings("Ajustes del sistema", AndroidSettings.ACTION_SETTINGS, listOf("ajustes del sistema", "configuracion del sistema")),
    Wifi("Wi-Fi", AndroidSettings.ACTION_WIFI_SETTINGS, listOf("wifi", "wi fi")),
    Bluetooth("Bluetooth", AndroidSettings.ACTION_BLUETOOTH_SETTINGS, listOf("bluetooth")),
    Sound("Sonido", AndroidSettings.ACTION_SOUND_SETTINGS, listOf("sonido", "volumen")),
    Display("Pantalla", AndroidSettings.ACTION_DISPLAY_SETTINGS, listOf("pantalla")),
    Cast("Transmitir pantalla", AndroidSettings.ACTION_CAST_SETTINGS, listOf("transmitir", "proyectar", "cast")),
    DateTime("Fecha y hora", AndroidSettings.ACTION_DATE_SETTINGS, listOf("fecha y hora")),
    Apps("Administrar apps", AndroidSettings.ACTION_APPLICATION_SETTINGS, listOf("administrar apps", "administrar aplicaciones")),
    NotificationAccess(
        "Acceso a notificaciones",
        AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS,
        listOf("acceso a notificaciones", "notificaciones"),
    ),
    ;

    fun launch(context: Context): Boolean {
        val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    companion object {
        fun fromSpoken(name: String): SystemShortcut? =
            values().firstOrNull { name in it.keywords }
    }
}

object NetworkStatus {
    /** Requiere ACCESS_NETWORK_STATE. */
    fun label(context: Context): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return "Sin red"
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "Sin red"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Datos móviles"
            else -> "Conectado"
        }
    }
}
