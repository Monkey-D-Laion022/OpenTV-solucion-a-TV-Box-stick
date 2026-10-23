package com.opentv.app.stream

import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketException

/** Dirección IPv4 de la red local del teléfono (la que el TV puede alcanzar). */
object LanIp {

    fun pick(): Inet4Address? {
        val interfaces = try {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        } catch (e: SocketException) {
            return null
        }
        val candidates = interfaces.mapNotNull { iface ->
            val usable = try {
                iface.isUp && !iface.isLoopback
            } catch (e: SocketException) {
                false
            }
            if (!usable) return@mapNotNull null
            val address = iface.inetAddresses.toList()
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress } ?: return@mapNotNull null
            iface.name to address
        }
        // Wi‑Fi o Ethernet antes que cualquier otra interfaz (datos móviles, VPN…).
        return candidates.sortedBy { (name, _) -> if (name.startsWith("wlan") || name.startsWith("eth")) 0 else 1 }
            .firstOrNull()?.second
    }
}
