package com.opentv.app.stream

/**
 * Relleno de flujo TS a ritmo constante. Con la interfaz quieta el codificador entrega muy pocos datos
 * y el TV, que espera un caudal sostenido, se queda sin búfer y abandona. Los paquetes nulos (PID 0x1FFF)
 * son el relleno estándar de MPEG-TS: los decodificadores los ignoran. Lógica pura.
 */
object TsPadding {
    private const val PACKET = 188

    /** [count] paquetes nulos seguidos (contador de continuidad no aplica a PID 0x1FFF). */
    fun nullPackets(count: Int): ByteArray {
        val out = ByteArray(count * PACKET) { 0xFF.toByte() }
        for (i in 0 until count) {
            val o = i * PACKET
            out[o] = 0x47
            out[o + 1] = 0x1F
            out[o + 2] = 0xFF.toByte()
            out[o + 3] = 0x10
        }
        return out
    }

    /** Cuántos paquetes nulos faltan para que, tras [elapsedMs], se hayan emitido [rateBps] bits/s en total. */
    fun packetsNeeded(rateBps: Long, elapsedMs: Long, emittedBytes: Long, maxPackets: Int = 200): Int {
        val targetBytes = rateBps / 8 * elapsedMs / 1000
        val deficit = targetBytes - emittedBytes
        if (deficit < PACKET) return 0
        return (deficit / PACKET).coerceAtMost(maxPackets.toLong()).toInt()
    }
}
