package com.opentv.app.proto

/**
 * Descubrimiento de OpenTV Link por DNS-SD/mDNS (en Android: NsdManager). El servicio es `_opentv._tcp` y los
 * datos van en el registro TXT. Esto SOLO descubre: la conexión, el emparejamiento y el video son otras capas.
 * Lógica pura.
 */
object OtDiscovery {
    const val SERVICE_TYPE = "_opentv._tcp."
    const val DEFAULT_PORT = 8765

    data class Advert(val deviceId: String, val name: String, val role: Int, val caps: Int, val port: Int)

    /** Pares clave/valor del TXT (cada uno ≤ 255 bytes, como exige DNS-SD). */
    fun txt(advert: Advert): Map<String, String> = mapOf(
        "v" to OtWire.VERSION.toString(),
        "id" to advert.deviceId,
        "n" to advert.name.take(40),
        "r" to if (advert.role == OtWire.ROLE_SENDER) "s" else "r",
        "c" to advert.caps.toString(16),
    )

    /** Interpreta un TXT recibido; `null` si no es de OpenTV o es de otra versión. Nunca confía en el contenido. */
    fun parse(txt: Map<String, String>, port: Int): Advert? {
        if (txt["v"] != OtWire.VERSION.toString()) return null
        val id = txt["id"]?.takeIf { Regex("^[0-9a-f]{16}$").matches(it) } ?: return null
        val role = when (txt["r"]) { "s" -> OtWire.ROLE_SENDER; "r" -> OtWire.ROLE_RECEIVER; else -> return null }
        val caps = txt["c"]?.toIntOrNull(16) ?: return null
        val name = (txt["n"] ?: "").filter { !it.isISOControl() }.take(40).ifBlank { "OpenTV" }
        if (port !in 1..65535) return null
        return Advert(id, name, role, caps, port)
    }

    fun deviceIdHex(id: ByteArray): String = id.joinToString("") { "%02x".format(it) }
}
