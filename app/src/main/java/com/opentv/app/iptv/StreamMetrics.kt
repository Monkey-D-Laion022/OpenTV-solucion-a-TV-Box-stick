package com.opentv.app.iptv

/** Tasa por segundo de un contador que solo crece (cuadros codificados, bytes enviados). Lógica pura; el tiempo se pasa. */
class RateMeter {
    private var lastTotal = 0L
    private var lastMs = -1L

    /** Devuelve unidades por segundo desde la muestra anterior; 0 en la primera llamada. */
    fun sample(total: Long, nowMs: Long): Double {
        val rate = if (lastMs < 0 || nowMs <= lastMs) 0.0 else (total - lastTotal) * 1000.0 / (nowMs - lastMs)
        lastTotal = total
        lastMs = nowMs
        return rate.coerceAtLeast(0.0)
    }
}

/** Separa lo pedido de lo medido; nunca presenta un FPS pedido como efectivo. */
object FpsVerdict {
    /** Tolerancia: por debajo de 85 % de lo pedido se avisa. Una pantalla quieta puede dar menos cuadros sin que sea un fallo. */
    fun describe(requestedFps: Int, encodedFps: Double, secondsLive: Long): String? {
        if (secondsLive < 6) return null
        if (encodedFps <= 0.5) return "El codificador no está entregando cuadros (¿pantalla sin cambios o captura detenida?)."
        if (encodedFps < requestedFps * 0.85) {
            return "FPS codificados ${"%.0f".format(encodedFps)} de ${requestedFps} pedidos: el teléfono entrega menos (o la pantalla cambia poco). " +
                "Con 1080p60 esto suele indicar que el codificador no sostiene ese ritmo."
        }
        return null
    }
}
