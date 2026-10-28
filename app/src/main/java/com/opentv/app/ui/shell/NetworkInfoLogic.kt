package com.opentv.app.ui.shell

/** Lo que el sistema informa de la conexión actual. Cada campo `null` = el sistema no lo informó. */
data class NetworkSnapshot(
    val connected: Boolean,
    val transport: String?,
    val validated: Boolean?,
    val ipv4: String?,
    val wifiEnabled: Boolean?,
    val rssiDbm: Int?,
    val linkMbps: Int?,
)

/** Textos de «Red e Internet». PURO: no consulta nada, solo da formato a lo que se le entrega. */
object NetworkText {
    const val NA = "no disponible"

    /** Nivel de señal 0–4, con la misma fórmula que usa Android (de −100 a −55 dBm). */
    fun signalLevel(rssiDbm: Int): Int = when {
        rssiDbm <= -100 -> 0
        rssiDbm >= -55 -> 4
        else -> (rssiDbm + 100) * 4 / 45
    }

    fun rows(s: NetworkSnapshot): List<Pair<String, String>> {
        val rows = mutableListOf<Pair<String, String>>()
        rows += "Estado" to if (s.connected) "Conectado" else "Sin conexión"
        rows += "Tipo de conexión" to (s.transport ?: NA)
        rows += "Internet" to when (s.validated) {
            true -> "El sistema confirmó que hay salida a Internet"
            false -> "El sistema no pudo confirmar la salida a Internet"
            null -> NA
        }
        rows += "Dirección IP" to (s.ipv4 ?: NA)
        rows += "Wi-Fi del teléfono" to when (s.wifiEnabled) {
            true -> "Activado"
            false -> "Desactivado"
            null -> NA
        }
        rows += "Intensidad de señal" to (s.rssiDbm?.let { "${signalLevel(it)} de 4 ($it dBm)" } ?: NA)
        rows += "Velocidad del enlace" to (s.linkMbps?.let { "$it Mbps" } ?: NA)
        return rows
    }

    const val LIMITATION =
        "OpenTV no muestra el nombre de la red ni la lista de redes disponibles: Android las oculta a las apps que no tienen permiso de " +
            "ubicación y OpenTV no lo pide. Para ver o cambiar de red usá los ajustes de Wi-Fi de Android."
}
