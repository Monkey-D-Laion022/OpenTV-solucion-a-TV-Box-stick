package com.opentv.app.iptv

/**
 * Adaptación central de calidad del canal en vivo (IPTV y DLNA comparten la misma tubería de captura). Lógica pura.
 *
 * Escalera de resoluciones: 1080p → 720p → 480p (cada una con los FPS que pida el perfil).
 * Dentro de una resolución el [FpsGovernor] baja los FPS (60 → 30, p. ej. 1080p60 → 1080p30); cuando ya está en el piso y sigue fallando, se baja de
 * resolución. Para volver a subir hace falta un período largo sin fallos; un escalón que falló no se reintenta hasta que
 * pasa un tiempo que crece con cada fracaso (retroceso exponencial con tope). Distingue la causa: sobrecarga del codificador
 * o del dibujado (baja calidad), red (baja solo el bitrate) y temperatura (baja calidad y no sube mientras dure).
 * Nunca se decide por un pico aislado: todo pasa por rachas de segundos medidos.
 */
class QualityAdapter(
    requested: IptvPreset,
    /** Resolución máxima útil (la nativa del televisor, si se conoce): no se procesa más de lo que se puede mostrar. */
    private val maxHeight: Int = 2160,
) {
    enum class Kind { NONE, FPS, RESOLUTION, BITRATE }

    data class Action(
        val kind: Kind,
        val preset: IptvPreset,
        val fps: Int,
        val bitrateBps: Int,
        val cause: FpsGovernor.Bottleneck,
        val note: String?,
    ) { val changed: Boolean get() = kind != Kind.NONE }

    /** Lo medido en el último segundo. [backlog] = cuadros dibujados todavía sin codificar (cola del codificador). */
    data class Sample(
        val encodedFps: Double, val renderedFps: Double?, val producedKbps: Int, val deliveredKbps: Int,
        val clients: Int, val thermalLimited: Boolean, val backlog: Long = 0, val encodeLatencyP95Ms: Double = 0.0,
    )

    private var ladder: List<IptvPreset> = buildLadder(requested, maxHeight)
    private var index = 0
    var current: IptvPreset = ladder[0]; private set
    private var governor = FpsGovernor(current.fps, floorFps = floorFor(current))
    val targetFps: Int get() = governor.targetFps
    val lastCause: FpsGovernor.Bottleneck get() = governor.lastCause
    /** Cantidad de bajadas de resolución y de subidas probadas: sirve para el informe de estabilidad. */
    var resolutionDrops = 0; private set
    var resolutionProbes = 0; private set

    private var bitrateScale = 1.0
    private var netBad = 0
    private var netGood = 0
    private var backlogBad = 0
    private var stableSeconds = 0
    private var upBackoffS = UP_RES_AFTER_S
    private var failedIndex: Int? = null
    private var failedTtl = 0
    private var cooldown = 0

    fun retarget(preset: IptvPreset) { reset(preset) }

    private fun reset(preset: IptvPreset) {
        val l = buildLadder(preset, maxHeight)
        ladder = l; index = 0; current = l[0]
        governor = FpsGovernor(current.fps, floorFor(current)); bitrateScale = 1.0
        failedIndex = null; failedTtl = 0; stableSeconds = 0; upBackoffS = UP_RES_AFTER_S; cooldown = 0
    }

    fun onSecond(s: Sample): Action {
        if (failedTtl > 0 && --failedTtl == 0) failedIndex = null
        if (cooldown > 0) cooldown--
        // Cola del codificador: si crece y se mantiene, el codificador se queda atrás aunque los FPS medidos aún parezcan bien;
        // en ese caso el gobernador recibe una muestra con la mitad de FPS codificados para que aplique su racha, escalones y memoria.
        backlogBad = if (s.backlog > BACKLOG_LIMIT) backlogBad + 1 else 0
        val behind = backlogBad >= BACKLOG_STREAK_S
        val encForGov = if (behind) minOf(s.encodedFps, governor.targetFps * 0.5) else s.encodedFps
        val g = governor.onSecond(
            // La red la trata este adaptador (solo bitrate): el gobernador no debe bajar FPS por ella.
            FpsGovernor.Sample(encForGov, s.renderedFps, s.producedKbps, s.producedKbps, s.clients, s.thermalLimited),
        )
        val cause = g.bottleneck

        if (g.changed) {
            stableSeconds = 0
            val bps = bitrateFor(g.targetFps)
            return Action(Kind.FPS, current, g.targetFps, bps, cause, g.note)
        }
        if (g.exhausted) {
            val lower = ladder.getOrNull(index + 1)
            if (lower != null) {
                failedIndex = index; failedTtl = upBackoffS; upBackoffS = (upBackoffS * 2).coerceAtMost(MAX_BACKOFF_S)
                val was = current
                index++; current = lower; resolutionDrops++; backlogBad = 0; stableSeconds = 0; cooldown = SETTLE_S
                governor = FpsGovernor(current.fps, floorFor(current)); bitrateScale = 1.0
                return Action(
                    Kind.RESOLUTION, current, current.fps, current.bitrateBps, cause,
                    "No se sostiene ${was.shortLabel} (${cause.label}). Se pasa a ${current.shortLabel}; se volverá a probar tras ${failedTtl / 60} min sin fallos.",
                )
            }
        }

        // Red: solo se toca el bitrate (cambiar resolución no arregla una red que no entrega).
        val starved = s.clients > 0 && s.producedKbps > 500 && s.deliveredKbps < s.producedKbps * 0.5
        if (starved) { netBad++; netGood = 0 } else { netGood++; netBad = 0 }
        if (netBad >= NET_DOWN_S && bitrateScale > MIN_SCALE) {
            bitrateScale = (bitrateScale * 0.75).coerceAtLeast(MIN_SCALE); netBad = 0
            return Action(Kind.BITRATE, current, governor.targetFps, bitrateFor(governor.targetFps), FpsGovernor.Bottleneck.NETWORK,
                "La red entrega menos de lo que se produce: se baja el bitrate al ${(bitrateScale * 100).toInt()} % (no se toca la resolución).")
        }
        if (netGood >= NET_UP_S && bitrateScale < 1.0 && !starved) {
            bitrateScale = (bitrateScale + 0.1).coerceAtMost(1.0); netGood = 0
            return Action(Kind.BITRATE, current, governor.targetFps, bitrateFor(governor.targetFps), FpsGovernor.Bottleneck.NONE,
                "Red estable: se recupera el bitrate (${(bitrateScale * 100).toInt()} %).")
        }

        // Subir de resolución: solo tras mucho tiempo estable, sin calor y sin tropiezos recientes.
        val healthy = cause == FpsGovernor.Bottleneck.NONE && !s.thermalLimited && !starved && backlogBad == 0
        stableSeconds = if (healthy) stableSeconds + 1 else 0
        if (index > 0 && stableSeconds >= upBackoffS && cooldown == 0 && governor.targetFps >= current.fps) {
            val upIdx = index - 1
            if (upIdx != failedIndex) {
                val was = current
                index = upIdx; current = ladder[index]; resolutionProbes++; stableSeconds = 0; cooldown = SETTLE_S
                // Se prueba primero con la mitad de los FPS si el escalón superior tiene más FPS que el actual.
                governor = FpsGovernor(current.fps, floorFor(current)); bitrateScale = 1.0
                return Action(Kind.RESOLUTION, current, current.fps, current.bitrateBps, FpsGovernor.Bottleneck.NONE,
                    "Estable desde hace ${upBackoffS / 60} min: se prueba subir de ${was.shortLabel} a ${current.shortLabel}.")
            }
        }
        return Action(Kind.NONE, current, governor.targetFps, bitrateFor(governor.targetFps), cause, g.note)
    }

    /** Bitrate para [fps] en el escalón actual: proporcional a los FPS (con piso) y escalado por la red. */
    fun bitrateFor(fps: Int): Int =
        ((current.bitrateBps.toLong() * fps / current.fps) * bitrateScale).toInt().coerceAtLeast(current.bitrateBps / 4)

    companion object {
        const val SETTLE_S = 8
        const val UP_RES_AFTER_S = 120
        const val MAX_BACKOFF_S = 900
        const val NET_DOWN_S = 5
        const val NET_UP_S = 20
        const val MIN_SCALE = 0.4
        const val BACKLOG_LIMIT = 6L
        const val BACKLOG_STREAK_S = 4

        /** Piso de FPS por resolución: en 1080p el piso es 30 (más abajo conviene bajar de resolución, no de fluidez). */
        fun floorFor(p: IptvPreset): Int = if (p.height >= 1080) 30 else if (p.height >= 720) 30 else 15

        /**
         * Una resolución por escalón (los FPS los baja el [FpsGovernor] dentro de cada una), de mayor a menor, sin superar lo pedido
         * ni la resolución útil: p. ej. 1080p60 → 720p60 → 480p30. Si se pidieron menos FPS, ningún escalón pide más que eso.
         */
        fun buildLadder(requested: IptvPreset, maxHeight: Int): List<IptvPreset> {
            val cap = minOf(requested.height, maxHeight)
            val tiers = listOf(1080, 720, 480).filter { it <= cap }.ifEmpty { listOf(requested.height) }
            val presets = IptvPreset.values().filter { it != IptvPreset.SAFE && it != IptvPreset.COMPAT_360 }
            return tiers.mapNotNull { h ->
                val sameHeight = presets.filter { it.height == h }
                (sameHeight.filter { it.fps <= requested.fps }.maxByOrNull { it.fps } ?: sameHeight.minByOrNull { it.fps })
            }.ifEmpty { listOf(requested) }.let { list -> if (list.first().height == requested.height) listOf(requested) + list.drop(1) else list }
        }
    }
}
