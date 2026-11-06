package com.opentv.app.media.dlna

/** Un servicio UPnP del televisor: tipo y dirección absoluta de control. */
data class ServiceRef(val type: String, val controlUrl: String)

/** Televisor o reproductor DLNA (MediaRenderer) descubierto. Capa independiente: no usa clases de `connect/`. */
data class Renderer(
    val udn: String,
    val friendlyName: String,
    val manufacturer: String,
    val modelName: String,
    val location: String,
    val avTransport: ServiceRef,
    val connectionManager: ServiceRef? = null,
    val renderingControl: ServiceRef? = null,
)

/** Una entrada del `Sink` de `GetProtocolInfo`: `protocolo:red:formato:adicional`. */
data class ProtocolInfo(val protocol: String, val network: String, val contentFormat: String, val additional: String) {
    /** Valor de `DLNA.ORG_PN` si el televisor lo declara. */
    val profileName: String? get() = additional.split(';').map { it.trim() }.firstOrNull { it.startsWith("DLNA.ORG_PN=", true) }?.substringAfter('=')?.trim()?.ifEmpty { null }

    val isHttp: Boolean get() = protocol.equals("http-get", true)

    val literal: String get() = "$protocol:$network:$contentFormat:$additional"

    companion object {
        fun parse(entry: String): ProtocolInfo? {
            val parts = entry.trim().split(':', limit = 4)
            if (parts.size < 4 || parts[0].isBlank() || parts[2].isBlank()) return null
            return ProtocolInfo(parts[0].trim(), parts[1].trim(), parts[2].trim(), parts[3].trim())
        }
    }
}

/** Estado de transporte UPnP (`GetTransportInfo`). */
enum class TransportState {
    PLAYING, PAUSED, STOPPED, TRANSITIONING, NO_MEDIA, ERROR, UNKNOWN;

    companion object {
        fun parse(raw: String?): TransportState = when (raw?.trim()?.uppercase()) {
            "PLAYING" -> PLAYING
            "PAUSED_PLAYBACK", "PAUSED_RECORDING" -> PAUSED
            "STOPPED" -> STOPPED
            "TRANSITIONING" -> TRANSITIONING
            "NO_MEDIA_PRESENT" -> NO_MEDIA
            "ERROR" -> ERROR
            else -> UNKNOWN
        }
    }
}

data class TransportInfo(val state: TransportState, val status: String?)

/** Resultado de una llamada SOAP. */
data class SoapResult(val status: Int, val body: String, val failure: String? = null) {
    val ok: Boolean get() = failure == null && status in 200..299
}
