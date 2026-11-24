package com.opentv.homenova.extras

import android.content.Context
import android.content.Intent
import android.provider.Settings as AndroidSettings

/**
 * Accesos directos a pantallas del sistema (traído del Home v3). Solo abren la pantalla de ajustes correspondiente: no cambian resolución,
 * densidad ni nada del 16:9.
 */
enum class SystemShortcut(val title: String, val subtitle: String, private val action: String) {
    Wifi("Wi-Fi", "Redes", AndroidSettings.ACTION_WIFI_SETTINGS),
    Bluetooth("Bluetooth", "Dispositivos", AndroidSettings.ACTION_BLUETOOTH_SETTINGS),
    Sound("Sonido", "Volumen y salidas", AndroidSettings.ACTION_SOUND_SETTINGS),
    Display("Pantalla", "Brillo y modo", AndroidSettings.ACTION_DISPLAY_SETTINGS),
    Cast("Transmitir", "Pantalla del sistema", AndroidSettings.ACTION_CAST_SETTINGS),
    DateTime("Fecha y hora", "Zona horaria", AndroidSettings.ACTION_DATE_SETTINGS),
    Apps("Aplicaciones", "Administrar apps", AndroidSettings.ACTION_APPLICATION_SETTINGS),
    All("Ajustes", "Sistema completo", AndroidSettings.ACTION_SETTINGS),
    ;

    fun launch(context: Context): Boolean {
        val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}
