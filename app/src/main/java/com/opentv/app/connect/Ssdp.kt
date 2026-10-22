package com.opentv.app.connect

data class SsdpResponse(val location: String, val usn: String, val st: String)

/** Descubrimiento SSDP (la parte de «buscar» de UPnP/DLNA). Solo formato de mensajes: sin red. */
object Ssdp {

    const val MULTICAST_ADDRESS = "239.255.255.250"
    const val PORT = 1900
    const val MEDIA_RENDERER = "urn:schemas-upnp-org:device:MediaRenderer:1"

    private const val MAX_DATAGRAM = 4096

    fun buildSearch(target: String = MEDIA_RENDERER, maxWaitSeconds: Int = 2): ByteArray {
        require(target.none { it.code < 0x20 || it.code == 0x7F }) { "Objetivo SSDP inválido" }
        val text = "M-SEARCH * HTTP/1.1\r\n" +
            "HOST: $MULTICAST_ADDRESS:$PORT\r\n" +
            "MAN: \"ssdp:discover\"\r\n" +
            "MX: $maxWaitSeconds\r\n" +
            "ST: $target\r\n\r\n"
        return text.toByteArray(Charsets.ISO_8859_1)
    }

    /** Interpreta la respuesta de un equipo. `null` si está mal formada o no trae LOCATION válida. */
    fun parseResponse(raw: ByteArray, length: Int = raw.size): SsdpResponse? {
        if (length <= 0 || length > MAX_DATAGRAM || length > raw.size) return null
        val lines = String(raw, 0, length, Charsets.ISO_8859_1).split("\r\n")
        val first = lines.first()
        if (!(first.startsWith("HTTP/1.1 200") || first.startsWith("HTTP/1.0 200"))) return null

        val headers = HashMap<String, String>()
        for (line in lines.drop(1)) {
            val colon = line.indexOf(':')
            if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }
        val location = headers["location"] ?: return null
        if (LanAddress.parseHttpUrl(location) == null) return null
        return SsdpResponse(location = location, usn = headers["usn"].orEmpty(), st = headers["st"].orEmpty())
    }

    /**
     * La LOCATION debe apuntar al mismo equipo que respondió. Evita que un dispositivo de la red
     * haga que OpenTV consulte a otro equipo distinto.
     */
    fun isTrustedSource(response: SsdpResponse, senderHost: String): Boolean =
        LanAddress.parseHttpUrl(response.location)?.host == senderHost
}
