package com.opentv.app.stream

/**
 * Fluidez del espejo por DLNA: cuadros por segundo y caudal de video. Antes era fijo (15 cuadros/s y 4 Mbps),
 * por eso se veía a tirones. Con 60 cuadros/s en 1080p hace falta nivel H.264 4.2, que algunos TV viejos no
 * decodifican: si el TV no abre el video, volver a 30. Lógica pura.
 */
enum class StreamRate(val label: String, val fps: Int, val bitrate: Int) {
    FPS30("30 cuadros/s", 30, 8_000_000),
    FPS60("60 cuadros/s", 60, 14_000_000),
    FPS15("15 cuadros/s (el de antes)", 15, 4_000_000);

    fun next(): StreamRate = values()[(ordinal + 1) % values().size]

    /** Cola máxima por cliente: nunca menos de 1 s de video (un fotograma clave de 1080p no entra en 192 KB). */
    fun hubQueueBytes(base: Int): Int = maxOf(base, bitrate / 8)

    /** Búfer de envío del socket: con caudales altos uno chico frena el envío. */
    fun sendBufferBytes(base: Int): Int = maxOf(base, 256 * 1024)

    /** Nivel H.264 necesario: `true` si pasa de lo que cubre el nivel 4.0 (1080p30). */
    fun needsLevel42(height: Int): Boolean = height >= 1080 && fps > 30
}
