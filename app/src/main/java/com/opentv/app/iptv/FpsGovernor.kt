package com.opentv.app.iptv

/**
 * Gobernador de FPS sostenidos. Lógica pura: el tiempo y las medidas se pasan, no lee nada de Android.
 *
 * Idea: pedir 60 fps no sirve si el teléfono solo sostiene 30; una emisión que «intenta» 60 y entrega 38 irregulares se ve peor
 * que una a 30 estables. Cada segundo recibe lo MEDIDO (dibujados por la bomba, codificados, bitrate producido y entregado,
 * estado térmico) y devuelve: qué cuello de botella se observa y, si corresponde, un nuevo FPS objetivo. Baja por escalones
 * (60 → 30 → 24 → 15) y vuelve a probar el escalón superior con prudencia. Nunca sube por encima de lo pedido y nunca actúa
 * por una pantalla quieta (la bomba la cubre repitiendo cuadros, así que `rendered` mide la capacidad real de dibujar).
 */
class FpsGovernor(private val requestedFps: Int, private val floorFps: Int = 15) {
    enum class Bottleneck(val label: String) {
        NONE("sin cuello de botella visible"),
        DRAW("el dibujado (GPU o bomba de cuadros) no llega al ritmo pedido"),
        ENCODER("el codificador no sostiene el ritmo pedido"),
        THERMAL("el teléfono está limitado por temperatura"),
        NETWORK("la red entrega menos de lo que se produce"),
    }

    data class Sample(
        val encodedFps: Double, val renderedFps: Double?, val producedKbps: Int, val deliveredKbps: Int,
        val clients: Int, val thermalLimited: Boolean,
    )

    /** [exhausted] = ya está en el piso de FPS y sigue fallando: lo siguiente es bajar de resolución (decide [QualityAdapter]). */
    data class Decision(val targetFps: Int, val changed: Boolean, val bottleneck: Bottleneck, val note: String?, val exhausted: Boolean = false)

    var targetFps: Int = requestedFps
        private set
    var bottleneck: Bottleneck = Bottleneck.NONE
        private set

    /** Última causa observada distinta de NONE: sigue disponible para el informe aunque el ajuste ya la haya resuelto. */
    var lastCause: Bottleneck = Bottleneck.NONE
        private set

    private var badStreak = 0
    private var goodStreak = 0
    private var cooldown = WARMUP_S
    private var probing = false
    private var failedAt: Int? = null // escalón que falló: no se reintenta durante FAIL_TTL_S (un tropiezo aislado no lo bloquea para siempre)
    private var failedTtl = 0

    fun onSecond(s: Sample): Decision {
        if (failedTtl > 0 && --failedTtl == 0) failedAt = null
        if (cooldown > 0) { cooldown--; return keep() }
        val enc = s.encodedFps
        val ren = s.renderedFps
        val tooSlow = enc < targetFps * LOW_RATIO
        bottleneck = when {
            !tooSlow && !(s.clients > 0 && networkStarved(s)) -> Bottleneck.NONE
            s.thermalLimited -> Bottleneck.THERMAL
            ren != null && ren < targetFps * LOW_RATIO -> Bottleneck.DRAW
            tooSlow -> Bottleneck.ENCODER
            else -> Bottleneck.NETWORK
        }
        if (bottleneck != Bottleneck.NONE) { lastCause = bottleneck; badStreak++; goodStreak = 0 } else { goodStreak++; badStreak = 0 }

        if (badStreak >= DOWN_AFTER_S) {
            val lower = lowerStep(targetFps)
            badStreak = 0; cooldown = SETTLE_S
            failedAt = targetFps; failedTtl = FAIL_TTL_S; probing = false
            if (lower != null) {
                val was = targetFps; targetFps = lower
                return Decision(lower, true, bottleneck, "No se sostienen $was fps: ${bottleneck.label}. Se baja a $lower fps para que sea estable.")
            }
            return Decision(targetFps, false, bottleneck, "Ya está en el mínimo de ${targetFps} fps y sigue fallando: ${bottleneck.label}.", exhausted = true)
        }
        if (goodStreak >= UP_AFTER_S && targetFps < requestedFps && bottleneck == Bottleneck.NONE && !s.thermalLimited) {
            val higher = higherStep(targetFps)
            goodStreak = 0
            if (higher != null && higher != failedAt) {
                targetFps = higher; probing = true; cooldown = SETTLE_S
                return Decision(higher, true, Bottleneck.NONE, "Estable: se prueba subir a $higher fps.")
            }
        }
        if (probing && goodStreak >= SETTLE_S) probing = false
        return keep()
    }

    private fun keep() = Decision(targetFps, false, bottleneck, null)

    private fun networkStarved(s: Sample): Boolean = s.producedKbps > 500 && s.deliveredKbps < s.producedKbps * 0.5

    private fun lowerStep(from: Int): Int? = STEPS.filter { it < from && it >= floorFps }.maxOrNull()
    private fun higherStep(from: Int): Int? = STEPS.filter { it > from && it <= requestedFps }.minOrNull()

    companion object {
        val STEPS = listOf(60, 30, 24, 15)
        const val LOW_RATIO = 0.85
        const val WARMUP_S = 8
        const val SETTLE_S = 6
        const val DOWN_AFTER_S = 6
        const val UP_AFTER_S = 45
        const val FAIL_TTL_S = 180

        fun thermalLimited(status: String?): Boolean = status == "moderado" || status == "severo" || status == "crítico" || status == "emergencia"
    }
}
