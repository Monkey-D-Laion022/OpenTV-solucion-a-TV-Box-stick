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

/**
 * Salud VERIFICADA de la emisión: que el servicio o la notificación sigan visibles no prueba nada. Aquí solo cuentan los
 * cuadros que el codificador produjo y los datos que el servidor entregó. Lógica pura (el tiempo y los contadores se pasan).
 */
object StreamHealth {
    data class Verdict(val healthy: Boolean, val text: String)

    fun judge(
        sinceFrameMs: Long, elapsedMs: Long, pumpActive: Boolean, capturedFps: Double?,
        sinceDeliveryMs: Long, deliveredKbps: Int, clients: Int, networkUp: Boolean,
    ): Verdict {
        if (!networkUp) return Verdict(false, "No hay una Wi‑Fi local utilizable: el televisor no puede recibir la emisión.")
        if (elapsedMs < 5_000) return Verdict(true, "Iniciando…")
        if (sinceFrameMs > 3_000) {
            val secs = sinceFrameMs / 1000
            return Verdict(
                false,
                if (pumpActive) "El codificador no produce cuadros desde hace $secs s aunque el ritmo fijo está activo: falla el codificador o la captura."
                else "No llegan cuadros nuevos desde hace $secs s. Con captura directa pasa cuando la pantalla no cambia; activá el ritmo fijo de cuadros.",
            )
        }
        if (clients > 0 && sinceDeliveryMs > 8_000) {
            return Verdict(false, "Hay $clients conexión(es) abierta(s) pero no se entregan datos desde hace ${sinceDeliveryMs / 1000} s: el reproductor se detuvo o dejó de pedir.")
        }
        val capturedNote = if (capturedFps != null && capturedFps < 1.0) " La pantalla casi no cambia (${"%.1f".format(capturedFps)} cuadros nuevos/s); se repiten cuadros." else ""
        return Verdict(
            true,
            if (clients == 0) "Produciendo cuadros; ningún equipo conectado ahora.$capturedNote"
            else "Produciendo cuadros y entregando datos ($deliveredKbps kbit/s) a $clients conexión(es).$capturedNote",
        )
    }
}
