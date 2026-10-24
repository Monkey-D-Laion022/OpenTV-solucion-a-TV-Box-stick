package com.opentv.app.stream

/**
 * Variantes para medir de dónde viene el retraso. Cada una cambia una sola cosa respecto de «Estable» (lo ya
 * probado en la LG NetCast) para poder comparar. El búfer interno del TV es el piso y no se controla desde acá.
 * Lógica pura.
 */
enum class StreamLatency(
    val label: String,
    val fps: Int,
    val iFrameSeconds: Int,
    /** Desfase del PTS respecto del reloj, a 90 kHz. */
    val ptsOffset90: Long,
    /** Cuánto antes del PTS va el PCR, a 90 kHz. */
    val pcrLead90: Long,
    /** Caudal mínimo que se rellena con paquetes nulos (0 = sin relleno). */
    val minRateBps: Long,
    /** Tamaño del búfer de envío del socket: más chico = menos datos en tránsito. */
    val sendBufferBytes: Int,
    /** Máximo de bytes en cola por cliente; si el TV se atrasa, se descarta lo viejo y se resincroniza. */
    val hubQueueBytes: Int,
    val lowLatencyEncoder: Boolean = false,
) {
    STABLE("Estable", 15, 1, 63_000L, 45_000L, 2_000_000L, 256 * 1024, 1024 * 1024),
    SHORT_QUEUE("Cola corta, sin relleno", 15, 1, 63_000L, 45_000L, 0L, 32 * 1024, 192 * 1024),
    SHORT_MARGIN("Margen de tiempo corto", 15, 1, 18_000L, 9_000L, 2_000_000L, 256 * 1024, 1024 * 1024);

    fun next(): StreamLatency = values()[(ordinal + 1) % values().size]
}
