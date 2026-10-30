package com.opentv.app.iptv

/**
 * Nivel H.264 mínimo que cubre un tamaño y una frecuencia (tabla A-1 de la norma H.264: MaxFS y MaxMBPS). Los valores
 * devueltos son los de `MediaCodecInfo.CodecProfileLevel.AVCLevel*` (constantes numéricas del SDK). Lógica pura.
 * Declarar un nivel demasiado bajo (p. ej. 3.1 con 720p60) puede hacer que el codificador lo rechace o lo ignore.
 */
object AvcLevels {
    const val L31 = 0x200
    const val L32 = 0x400
    const val L40 = 0x800
    const val L41 = 0x1000
    const val L42 = 0x2000
    const val L50 = 0x4000
    const val L51 = 0x8000

    private class Limit(val code: Int, val name: String, val maxFs: Int, val maxMbps: Int)

    private val limits = listOf(
        Limit(L31, "3.1", 3_600, 108_000),
        Limit(L32, "3.2", 5_120, 216_000),
        Limit(L40, "4.0", 8_192, 245_760),
        Limit(L41, "4.1", 8_192, 245_760),
        Limit(L42, "4.2", 8_704, 522_240),
        Limit(L50, "5.0", 22_080, 589_824),
        Limit(L51, "5.1", 36_864, 983_040),
    )

    private fun mbs(v: Int) = (v + 15) / 16

    /** Nivel mínimo para [width]×[height] a [fps]; `null` si ninguno lo cubre. */
    fun forStream(width: Int, height: Int, fps: Int): Int? {
        val fs = mbs(width) * mbs(height)
        val mbps = fs.toLong() * fps
        return limits.firstOrNull { fs <= it.maxFs && mbps <= it.maxMbps }?.code
    }

    fun name(code: Int): String = limits.firstOrNull { it.code == code }?.name ?: "?"
}

/** Resultado de ajustar una calidad pedida a lo que el teléfono sabe codificar. */
data class ProfileChoice(val preset: IptvPreset, val note: String?)

object IptvProfilePicker {
    /**
     * [supports] responde si el codificador H.264 del teléfono declara soportar ancho, alto y FPS. Si el perfil pedido no
     * se admite se baja, en orden, a un perfil menos exigente que sí se admita. Declarar soporte no garantiza que el
     * teléfono sostenga ese ritmo de verdad: eso solo lo muestran los FPS medidos.
     */
    fun pick(requested: IptvPreset, supports: (Int, Int, Int) -> Boolean): ProfileChoice {
        val order = IptvPreset.values().sortedByDescending { it.width * it.height * it.fps }
        val candidates = order.filter { it.width * it.height * it.fps <= requested.width * requested.height * requested.fps }
        val chosen = candidates.firstOrNull { supports(it.width, it.height, it.fps) } ?: return ProfileChoice(
            requested, "No se pudo comprobar el soporte del codificador; se intenta con lo pedido (${requested.shortLabel}).",
        )
        return if (chosen == requested) ProfileChoice(requested, null)
        else ProfileChoice(chosen, "El codificador del teléfono no declara soportar ${requested.shortLabel}; se usa ${chosen.shortLabel}.")
    }
}

/** Mide si en el audio capturado hay sonido real (no ceros). Lógica pura sobre PCM de 16 bits little-endian. */
object AudioActivityMeter {
    /** Nivel RMS en dBFS (−96 = silencio absoluto). */
    fun rmsDb(pcm: ByteArray, length: Int = pcm.size): Double {
        var sum = 0.0
        var n = 0
        var i = 0
        while (i + 1 < length) {
            val v = (pcm[i].toInt() and 0xFF) or (pcm[i + 1].toInt() shl 8)
            val s = v.toShort().toInt()
            sum += s.toDouble() * s
            n++
            i += 2
        }
        if (n == 0 || sum == 0.0) return -96.0
        val rms = Math.sqrt(sum / n) / 32768.0
        return (20 * Math.log10(rms)).coerceAtLeast(-96.0)
    }

    /** Umbral: por debajo de −70 dBFS se considera silencio (ruido de cuantización). */
    const val SOUND_THRESHOLD_DB = -70.0
    fun isSound(pcm: ByteArray, length: Int = pcm.size) = rmsDb(pcm, length) > SOUND_THRESHOLD_DB

    /** Estado de audio según cuándo se oyó sonido por última vez. */
    fun state(capturing: Boolean, lastSoundAtMs: Long, nowMs: Long, windowMs: Long = 5_000): IptvAudio = when {
        !capturing -> IptvAudio.SILENT_TRACK
        lastSoundAtMs > 0 && nowMs - lastSoundAtMs <= windowMs -> IptvAudio.CAPTURING_SOUND
        else -> IptvAudio.CAPTURING_NO_SOUND
    }
}
