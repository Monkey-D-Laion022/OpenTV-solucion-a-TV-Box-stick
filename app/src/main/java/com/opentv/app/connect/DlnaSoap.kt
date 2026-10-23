package com.opentv.app.connect

/** Mensajes SOAP de UPnP AVTransport: enviar un enlace, reproducir y detener. */
object DlnaSoap {

    const val ACTION_SET_URI = "SetAVTransportURI"
    const val ACTION_PLAY = "Play"
    const val ACTION_STOP = "Stop"

    fun soapAction(serviceType: String, action: String): String = "\"$serviceType#$action\""

    fun setUri(serviceType: String, mediaUrl: String, metadata: String? = null): String = envelope(
        serviceType,
        ACTION_SET_URI,
        "<InstanceID>0</InstanceID>" +
            "<CurrentURI>${xmlEscape(mediaUrl)}</CurrentURI>" +
            "<CurrentURIMetaData>${xmlEscape(metadata.orEmpty())}</CurrentURIMetaData>",
    )

    fun play(serviceType: String): String =
        envelope(serviceType, ACTION_PLAY, "<InstanceID>0</InstanceID><Speed>1</Speed>")

    fun stop(serviceType: String): String =
        envelope(serviceType, ACTION_STOP, "<InstanceID>0</InstanceID>")

    fun xmlEscape(text: String): String = buildString(text.length + 16) {
        for (c in text) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(c)
            }
        }
    }

    /** Extrae el código y la descripción de un error UPnP, si la respuesta los trae. */
    fun errorDetail(body: String): String? {
        val code = Regex("<errorCode>\\s*(\\d{1,5})\\s*</errorCode>").find(body)?.groupValues?.get(1)
        val description = Regex("<errorDescription>([^<]{1,120})</errorDescription>")
            .find(body)?.groupValues?.get(1)?.trim()
        return when {
            code != null && !description.isNullOrEmpty() -> "$description (código $code)"
            code != null -> "código $code"
            else -> null
        }
    }

    private fun envelope(serviceType: String, action: String, arguments: String): String {
        require(serviceType.matches(Regex("[A-Za-z0-9:._-]{1,100}"))) { "Tipo de servicio inválido" }
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
            "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
            "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
            "<s:Body><u:$action xmlns:u=\"$serviceType\">$arguments</u:$action></s:Body></s:Envelope>"
    }
}

/** Resultado de una orden DLNA. */
sealed interface DlnaResult {
    object Ok : DlnaResult
    data class Rejected(val status: Int, val detail: String?) : DlnaResult
    data class Unreachable(val reason: String) : DlnaResult
}

object DlnaMessages {

    fun describe(deviceName: String, action: String, result: DlnaResult): String = when (result) {
        DlnaResult.Ok -> when (action) {
            DlnaSoap.ACTION_STOP -> "Se detuvo la reproducción en $deviceName."
            else -> "Enviado a $deviceName. Si no empieza, el TV puede no admitir ese formato o enlace."
        }
        is DlnaResult.Rejected ->
            "$deviceName rechazó la orden (HTTP ${result.status})" +
                (result.detail?.let { ": $it" } ?: ".")
        is DlnaResult.Unreachable ->
            "No se pudo hablar con $deviceName: ${result.reason}. Revisá que siga encendido y " +
                "en la misma red."
    }
}
