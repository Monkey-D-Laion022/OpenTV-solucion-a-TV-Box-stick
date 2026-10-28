package com.opentv.app.ui.shell

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.provider.Settings
import java.net.Inet4Address

/**
 * Acceso de SOLO LECTURA a lo que Android informa de la red, y apertura de las pantallas oficiales de ajustes.
 * OpenTV no cambia ningún ajuste del sistema desde acá: cuando algo pertenece a Android, se abre su pantalla.
 */
object SystemAccess {
    /** Pantallas oficiales de Android que se pueden abrir desde Ajustes. */
    enum class Screen(val action: String) {
        WIFI(Settings.ACTION_WIFI_SETTINGS),
        DISPLAY(Settings.ACTION_DISPLAY_SETTINGS),
        SOUND(Settings.ACTION_SOUND_SETTINGS),
        APPLICATIONS(Settings.ACTION_APPLICATION_SETTINGS),
    }

    /** `true` si Android abrió la pantalla; `false` si este equipo no la ofrece. */
    fun open(context: Context, screen: Screen): Boolean = try {
        context.startActivity(Intent(screen.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }

    @Suppress("DEPRECATION")
    fun readNetwork(context: Context): NetworkSnapshot {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = runCatching { cm?.activeNetwork }.getOrNull()
        val caps = network?.let { runCatching { cm?.getNetworkCapabilities(it) }.getOrNull() }
        val transport = when {
            caps == null -> null
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Datos móviles"
            else -> "Otra"
        }
        val ipv4 = network?.let {
            runCatching {
                cm?.getLinkProperties(it)?.linkAddresses?.map { a -> a.address }?.firstOrNull { a -> a is Inet4Address }?.hostAddress
            }.getOrNull()
        }
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val wifiEnabled = runCatching { wifi?.isWifiEnabled }.getOrNull()
        var rssi: Int? = null
        var link: Int? = null
        if (transport == "Wi-Fi") {
            runCatching { wifi?.connectionInfo }.getOrNull()?.let { info ->
                if (info.rssi in -126..0) rssi = info.rssi
                if (info.linkSpeed > 0) link = info.linkSpeed
            }
        }
        return NetworkSnapshot(
            connected = network != null,
            transport = transport,
            validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            ipv4 = ipv4,
            wifiEnabled = wifiEnabled,
            rssiDbm = rssi,
            linkMbps = link,
        )
    }
}
