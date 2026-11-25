package com.opentv.app.proto

/**
 * Control de bitrate del emisor a partir de lo que informa el receptor. Reglas simples y explicables (no un
 * algoritmo de red sofisticado): si el receptor acumula demasiado búfer o descarta tramas, se baja; si lleva varios
 * informes seguidos sanos, se sube de a poco hasta el máximo pedido. Lógica pura.
 */
class OtBitrateController(
    private val maxBps: Long,
    // Piso de calidad: con 3 Mbit pedidos, la ronda 7 mostró que el control caía a 375 kbit/s (max/8) y la imagen salía pixelada.
    // Por debajo de ~1,5 Mbit un 720p30 de pantalla se ve mal; el piso es el mayor entre max/4 y min(max, 1,5 Mbit).
    private val minBps: Long = maxOf(maxBps / 4, minOf(maxBps, 1_500_000L)),
    private val startBps: Long = maxBps,
    private val targetBufferMs: Int = 200,
) {
    var currentBps: Long = startBps.coerceIn(minBps, maxBps)
        private set
    private var healthyStreak = 0
    private var lastDropped = 0L
    private var lastLate = 0L

    companion object {
        /** Bloques de audio vacíos por informe a partir de los cuales se considera atraso real (≈ 200 ms con bloques de 256 muestras a 48 kHz). */
        const val LATE_SOFT_BLOCKS = 40L
    }

    /** Devuelve el nuevo bitrate si hay que cambiarlo, o `null` si no. */
    fun onStats(stats: OtStats): Long? {
        val newDrops = stats.dropped - lastDropped
        val newLate = stats.lateFrames - lastLate
        lastDropped = stats.dropped
        lastLate = stats.lateFrames
        // «late» del receptor = bloques de audio vacíos (5 ms c/u en la PS3): una pausa del emisor/Wi‑Fi de 1-3 s suma cientos de golpe sin que
        // el receptor vaya lento. Bajar fuerte por eso (x0,7 en cada pausa, cada ~20 s) hundía el bitrate. Ahora solo baja suave (x0,9) si son muchos.
        val hard = newDrops > 0 || stats.bufferMs > targetBufferMs * 3
        val soft = newLate > LATE_SOFT_BLOCKS
        if (hard || soft) {
            healthyStreak = 0
            val next = (if (hard) currentBps * 7 / 10 else currentBps * 9 / 10).coerceAtLeast(minBps)
            return if (next != currentBps) { currentBps = next; next } else null
        }
        if (stats.bufferMs <= targetBufferMs) healthyStreak++ else healthyStreak = 0
        if (healthyStreak >= 4 && currentBps < maxBps) {
            healthyStreak = 0
            val next = (currentBps * 12 / 10).coerceAtMost(maxBps)
            if (next != currentBps) { currentBps = next; return next }
        }
        return null
    }
}

/**
 * Decide si una trama saliente se envía o se descarta cuando el receptor va lento. TCP nunca pierde datos, así que
 * la "pérdida" aparece como cola creciente: si la cola supera el tope se descarta lo que no sea clave y se vuelve
 * a enviar desde el próximo fotograma clave (el receptor lo pide con REQUEST_KEYFRAME si hace falta).
 */
class OtSendGate(private val maxQueuedBytes: Int) {
    private var skipping = false

    /** [queuedBytes]: bytes ya aceptados pero todavía sin salir hacia ese receptor. */
    fun allow(queuedBytes: Int, keyStart: Boolean): Boolean {
        if (skipping) {
            if (keyStart && queuedBytes < maxQueuedBytes / 2) { skipping = false; return true }
            return false
        }
        if (queuedBytes > maxQueuedBytes) { skipping = true; return keyStart && false }
        return true
    }

    val isSkipping: Boolean get() = skipping
}

/** Topes que el receptor pide en ACCEPT (puede pedir MENOS de lo ofrecido, nunca más). 0 = sin tope. */
data class OtAcceptCaps(val bitrateBps: Long, val fps: Int) {
    companion object {
        fun resolve(offeredBps: Long, offeredFps: Int, accept: OtAccept): OtAcceptCaps = OtAcceptCaps(
            bitrateBps = if (accept.bitrateBps in 200_000 until offeredBps) accept.bitrateBps else offeredBps,
            fps = if (accept.fps in 1 until offeredFps) accept.fps else 0,
        )
    }
}
