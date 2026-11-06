package com.opentv.app.media.dlna

import java.net.URI

/** Lectura y escritura de los mensajes UPnP/DLNA. Lógica pura y tolerante: los televisores viejos generan XML poco prolijo. */
object DlnaXml {
    const val MAX_CHARS = 262_144

    private fun tag(xml: String, name: String): String? =
        Regex("<(?:\\w+:)?$name(?:\\s[^>]*)?>\\s*([^<]{0,400}?)\\s*</(?:\\w+:)?$name>", RegexOption.IGNORE_CASE).find(xml)?.groupValues?.get(1)?.let(::unescape)

    fun unescape(s: String): String = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")

    fun escape(s: String): String = buildString(s.length + 16) {
        for (c in s) when (c) { '&' -> append("&amp;"); '<' -> append("&lt;"); '>' -> append("&gt;"); '"' -> append("&quot;"); '\'' -> append("&apos;"); else -> append(c) }
    }

    /** Descripción del dispositivo → televisor, o `null` si no es un MediaRenderer con AVTransport. */
    fun parseRenderer(xml: String, location: String): Renderer? {
        if (xml.length > MAX_CHARS) return null
        if (!xml.contains("MediaRenderer", ignoreCase = true)) return null
        val base = tag(xml, "URLBase")?.takeIf { it.startsWith("http") } ?: location
        val services = Regex("<service>(.*?)</service>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).findAll(xml).mapNotNull { m ->
            val body = m.groupValues[1]
            val type = tag(body, "serviceType") ?: return@mapNotNull null
            val control = tag(body, "controlURL") ?: return@mapNotNull null
            val abs = resolve(base, control) ?: return@mapNotNull null
            ServiceRef(type, abs)
        }.toList()
        val av = services.firstOrNull { it.type.contains("AVTransport", true) } ?: return null
        val deviceBlock = Regex("<device>(.*?)<serviceList>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).find(xml)?.groupValues?.get(1) ?: xml
        return Renderer(
            udn = tag(deviceBlock, "UDN") ?: location,
            friendlyName = tag(deviceBlock, "friendlyName") ?: "Televisor DLNA",
            manufacturer = tag(deviceBlock, "manufacturer") ?: "",
            modelName = tag(deviceBlock, "modelName") ?: "",
            location = location,
            avTransport = av,
            connectionManager = services.firstOrNull { it.type.contains("ConnectionManager", true) },
            renderingControl = services.firstOrNull { it.type.contains("RenderingControl", true) },
        )
    }

    fun resolve(base: String, path: String): String? = try {
        val u = URI(base).resolve(path)
        if (u.scheme == "http" && !u.host.isNullOrEmpty()) u.toString() else null
    } catch (e: Exception) { null }

    /** `Sink` de `GetProtocolInfo` → formatos que el televisor dice aceptar. */
    fun parseSink(responseBody: String): List<ProtocolInfo> {
        val sink = tag(responseBody.replace("\n", ""), "Sink") ?: return emptyList()
        return splitTopLevel(sink).mapNotNull(ProtocolInfo::parse)
    }

    /** Divide por comas sin partir dentro de valores (los campos adicionales no llevan comas, pero algunos televisores sí agregan espacios). */
    private fun splitTopLevel(s: String): List<String> = s.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    fun parseTransportInfo(body: String): TransportInfo = TransportInfo(TransportState.parse(tag(body, "CurrentTransportState")), tag(body, "CurrentTransportStatus"))

    fun errorDetail(body: String): String? {
        val code = Regex("<errorCode>\\s*(\\d{1,5})\\s*</errorCode>").find(body)?.groupValues?.get(1)
        val desc = Regex("<errorDescription>([^<]{1,120})</errorDescription>").find(body)?.groupValues?.get(1)?.trim()
        return when { code != null && !desc.isNullOrEmpty() -> "$desc (código $code)"; code != null -> "código $code"; else -> null }
    }

    // ---- Mensajes SOAP ----
    fun soapAction(serviceType: String, action: String) = "\"$serviceType#$action\""

    private fun envelope(serviceType: String, action: String, args: String): String {
        require(serviceType.matches(Regex("[A-Za-z0-9:._-]{1,100}"))) { "Tipo de servicio inválido" }
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
            "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body><u:$action xmlns:u=\"$serviceType\">$args</u:$action></s:Body></s:Envelope>"
    }

    fun setUri(type: String, url: String, didl: String) =
        envelope(type, "SetAVTransportURI", "<InstanceID>0</InstanceID><CurrentURI>${escape(url)}</CurrentURI><CurrentURIMetaData>${escape(didl)}</CurrentURIMetaData>")
    fun play(type: String) = envelope(type, "Play", "<InstanceID>0</InstanceID><Speed>1</Speed>")
    fun pause(type: String) = envelope(type, "Pause", "<InstanceID>0</InstanceID>")
    fun stop(type: String) = envelope(type, "Stop", "<InstanceID>0</InstanceID>")
    fun transportInfo(type: String) = envelope(type, "GetTransportInfo", "<InstanceID>0</InstanceID>")
    fun protocolInfo(type: String) = envelope(type, "GetProtocolInfo", "")
    fun setVolume(type: String, percent: Int) =
        envelope(type, "SetVolume", "<InstanceID>0</InstanceID><Channel>Master</Channel><DesiredVolume>${percent.coerceIn(0, 100)}</DesiredVolume>")

    /** Metadatos DIDL-Lite del flujo en vivo; el literal de `protocolInfo` es el que eligió el selector, no uno fijo. */
    fun didl(title: String, url: String, protocolInfo: String): String =
        "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" " +
            "xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\"><item id=\"0\" parentID=\"-1\" restricted=\"1\">" +
            "<dc:title>${escape(title)}</dc:title><upnp:class>object.item.videoItem</upnp:class>" +
            "<res protocolInfo=\"${escape(protocolInfo)}\">${escape(url)}</res></item></DIDL-Lite>"
}
