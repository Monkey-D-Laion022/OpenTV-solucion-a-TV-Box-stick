package com.opentv.app.connect

/**
 * Metadatos DIDL-Lite que muchos televisores exigen junto con el enlace (`CurrentURIMetaData`).
 * Sin ellos, varios LG y Samsung rechazan el video o no lo abren. Lógica pura.
 */
object DidlLite {

    /** Cabeceras DLNA para un flujo MPEG-TS en vivo (el que genera la proyección de OpenTV). */
    const val TS_LIVE_PROTOCOL_INFO =
        "http-get:*:video/mpeg:DLNA.ORG_PN=MPEG_TS_HD_NA_ISO;DLNA.ORG_OP=00;DLNA.ORG_CI=0;" +
            "DLNA.ORG_FLAGS=01700000000000000000000000000000"

    fun protocolInfoFor(url: String): String {
        val path = url.substringBefore('#').substringBefore('?').lowercase()
        return when (path.substringAfterLast('.', missingDelimiterValue = "")) {
            "ts" -> TS_LIVE_PROTOCOL_INFO
            "mp4", "m4v" -> "http-get:*:video/mp4:*"
            "mkv" -> "http-get:*:video/x-matroska:*"
            "avi" -> "http-get:*:video/avi:*"
            "mp3" -> "http-get:*:audio/mpeg:*"
            else -> "http-get:*:*:*"
        }
    }

    fun forUrl(url: String, title: String): String {
        val isAudio = protocolInfoFor(url).contains(":audio/")
        val upnpClass = if (isAudio) "object.item.audioItem" else "object.item.videoItem"
        return "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" " +
            "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" " +
            "xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">" +
            "<item id=\"0\" parentID=\"-1\" restricted=\"1\">" +
            "<dc:title>${DlnaSoap.xmlEscape(title)}</dc:title>" +
            "<upnp:class>$upnpClass</upnp:class>" +
            "<res protocolInfo=\"${DlnaSoap.xmlEscape(protocolInfoFor(url))}\">${DlnaSoap.xmlEscape(url)}</res>" +
            "</item></DIDL-Lite>"
    }
}
