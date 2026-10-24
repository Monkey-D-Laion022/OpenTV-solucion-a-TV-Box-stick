package com.opentv.app.stream

/**
 * Cuánta anticipación se le da al TV. «Estable» es lo que ya se probó en la LG NetCast. «Baja latencia» reduce
 * los márgenes de tiempo del flujo (lo que el TV espera antes de mostrar un cuadro), sube los cuadros por segundo
 * y el caudal mínimo. El búfer propio del TV no se puede cambiar desde acá: es el piso del retraso. Lógica pura.
 */
enum class StreamLatency(
    val label: String,
    val fps: Int,
    val iFrameSeconds: Int,
    /** Desfase del PTS respecto del reloj, a 90 kHz. */
    val ptsOffset90: Long,
    /** Cuánto antes del PTS va el PCR, a 90 kHz. */
    val pcrLead90: Long,
    val minRateBps: Long,
    val lowLatencyEncoder: Boolean,
) {
    STABLE("Estable", 15, 1, 63_000L, 45_000L, 2_000_000L, false),
    LOW("Baja latencia", 30, 1, 18_000L, 9_000L, 4_000_000L, true);

    fun next(): StreamLatency = values()[(ordinal + 1) % values().size]
}
