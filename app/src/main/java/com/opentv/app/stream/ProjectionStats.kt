package com.opentv.app.stream

/** Contadores que se muestran en el teléfono para saber qué pasa con el TV. Lógica pura. */
data class ProjectionStats(
    val profileLabel: String = "",
    val audio: Boolean = false,
    /** Cuadros dibujados hacia el codificador. */
    val drawnFrames: Long = 0,
    /** Cuadros que salieron del codificador. */
    val encodedFrames: Long = 0,
    val bytesSent: Long = 0,
    val clients: Int = 0,
    val requests: Int = 0,
    val lastRequest: String = "",
    /** El contenido de OpenTV (la interfaz) ya se dibujó en la pantalla virtual. */
    val uiVisible: Boolean = false,
) {
    /** Texto de diagnóstico en lenguaje llano. */
    fun describe(): String = buildString {
        append("Modo: ").append(profileLabel).append(if (audio) " · con audio mudo" else " · sin audio").append('\n')
        append("Cuadros dibujados: ").append(drawnFrames).append(" · codificados: ").append(encodedFrames).append('\n')
        append("Enviado al TV: ").append(formatBytes(bytesSent)).append(" · conexiones del TV: ").append(clients)
        append(" (pedidos: ").append(requests).append(")\n")
        append("Interfaz en pantalla virtual: ").append(if (uiVisible) "sí" else "NO (se envía un cartel de OpenTV)")
        if (lastRequest.isNotEmpty()) append("\nÚltimo pedido del TV: ").append(lastRequest)
        append('\n').append(hint())
    }

    /** Orientación sobre qué mirar según los números. */
    fun hint(): String = when {
        requests == 0 -> "El TV todavía no pidió el video: si pasan segundos, el TV no pudo abrir el enlace."
        encodedFrames == 0L -> "El codificador no entrega cuadros: fallo en el teléfono, no en el TV."
        bytesSent == 0L -> "Hay cuadros pero no salen hacia el TV."
        else -> "El video está saliendo hacia el TV."
    }

    companion object {
        fun formatBytes(bytes: Long): String = when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "%.1f MB".format(java.util.Locale.US, bytes / 1024.0 / 1024.0)
        }
    }
}
