package com.opentv.app.stream

/**
 * Calidad JPEG adaptativa del espejo por navegador. Lógica pura.
 *
 * El navegador de un TV viejo puede leer más lento de lo que el teléfono produce; entonces el servidor salta cuadros (nunca los
 * acumula) pero cada cuadro pesado sigue ocupando la Wi‑Fi y la fluidez cae. Si hay clientes y reciben bastante menos cuadros
 * de los que se producen, se baja la calidad JPEG (cuadros más livianos); si vuelve a haber holgura sostenida, se sube de a poco
 * hasta el máximo del perfil elegido. Una pantalla quieta NO cuenta como congestión: se compara lo enviado contra lo PRODUCIDO,
 * no contra el FPS pedido.
 */
class MjpegAdaptive(private val maxQuality: Int, private val minQuality: Int = 30) {
    var quality: Int = maxQuality
        private set
    private var bad = 0
    private var good = 0

    /** [producedFps]: cuadros nuevos por segundo publicados. [sentPerClient]: cuadros por segundo entregados a cada cliente. */
    fun onSecond(producedFps: Double, sentPerClient: Double, clients: Int): Int {
        if (clients <= 0 || producedFps < MIN_PRODUCED) { bad = 0; good = 0; return quality }
        if (sentPerClient < producedFps * LOW_RATIO) { bad++; good = 0 } else { good++; bad = 0 }
        if (bad >= DOWN_AFTER_S) { quality = (quality - STEP_DOWN).coerceAtLeast(minQuality); bad = 0 }
        if (good >= UP_AFTER_S) { quality = (quality + STEP_UP).coerceAtMost(maxQuality); good = 0 }
        return quality
    }

    companion object {
        const val MIN_PRODUCED = 5.0
        const val LOW_RATIO = 0.7
        const val DOWN_AFTER_S = 3
        const val UP_AFTER_S = 10
        const val STEP_DOWN = 10
        const val STEP_UP = 5
    }
}
