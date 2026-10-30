package com.opentv.app.proto

/**
 * Control de bitrate del emisor a partir de lo que informa el receptor. Reglas simples y explicables (no un
 * algoritmo de red sofisticado): si el receptor acumula demasiado búfer o descarta tramas, se baja; si lleva varios
 * informes seguidos sanos, se sube de a poco hasta el máximo pedido. Lógica pura.
 */
class OtBitrateController(
    private val maxBps: Long,
    private val minBps: Long = maxBps / 8,
    private val startBps: Long = maxBps,
    private val targetBufferMs: Int = 200,
) {
    var currentBps: Long = startBps.coerceIn(minBps, maxBps)
        private set
    private var healthyStreak = 0
    private var lastDropped = 0L
    private var lastLate = 0L

    /** Devuelve el nuevo bitrate si hay que cambiarlo, o `null` si no. */
    fun onStats(stats: OtStats): Long? {
        val newDrops = stats.dropped - lastDropped
        val newLate = stats.lateFrames - lastLate
        lastDropped = stats.dropped
        lastLate = stats.lateFrames
        val congested = newDrops > 0 || newLate > 2 || stats.bufferMs > targetBufferMs * 3
        if (congested) {
            healthyStreak = 0
            val next = (currentBps * 7 / 10).coerceAtLeast(minBps)
            return if (next != currentBps) { currentBps = next; next } else null
        }
        if (stats.bufferMs <= targetBufferMs) healthyStreak++ else healthyStreak = 0
        if (healthyStreak >= 5 && currentBps < maxBps) {
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
