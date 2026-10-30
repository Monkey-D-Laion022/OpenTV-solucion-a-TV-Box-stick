package com.opentv.app.iptv

/** Un trozo de video/audio MPEG-TS listo para servir por HLS. */
class HlsSegment(val sequence: Long, val durationMs: Long, val data: ByteArray) {
    val name: String get() = HlsNames.segment(sequence)
}

object HlsNames {
    fun segment(sequence: Long) = "seg-$sequence.ts"
    fun parseSegment(name: String): Long? =
        Regex("^seg-(\\d{1,18})\\.ts$").find(name)?.groupValues?.get(1)?.toLongOrNull()
}

/** Ajustes del canal HLS. Los valores por defecto son conservadores para reproductores viejos (HLS versión 3). */
data class HlsConfig(
    /** Duración objetivo de cada segmento; el corte real ocurre en el primer fotograma clave desde (objetivo − 12 %), para absorber la variación del reloj. */
    val targetSegmentMs: Long = 2_000,
    /** Segmentos que lista la playlist. Los reproductores empiezan ~3 segmentos antes del final: más segmentos = más retraso. */
    val windowSegments: Int = 4,
    /** Segmentos extra que se conservan en memoria por si un reproductor lento pide uno que ya salió de la lista. */
    val extraKept: Int = 2,
    /** Tope de memoria para los segmentos retenidos. */
    val maxBytes: Long = 24L * 1024 * 1024,
) {
    init {
        require(targetSegmentMs in 500..10_000) { "Duración de segmento fuera de rango" }
        require(windowSegments in 2..10) { "Ventana fuera de rango" }
    }
}

/**
 * Corta el flujo TS que entrega el codificador en segmentos que empiezan en un fotograma clave (los bloques con
 * `keyStart`, que ya llevan PAT/PMT al principio). Lógica pura y segura entre hilos: el codificador publica y el
 * servidor HTTP lee. No toca el reloj: el tiempo se pasa en cada llamada, así se prueba sin esperar.
 */
class HlsSegmenter(private val config: HlsConfig = HlsConfig()) {

    private val lock = Any()
    private val done = ArrayDeque<HlsSegment>()
    private var current = java.io.ByteArrayOutputStream()
    private var currentStartMs = -1L
    private var nextSequence = 0L
    private var retainedBytes = 0L
    private var ended = false

    /** Segmentos completos publicados desde el inicio (sirve para mostrar actividad). */
    val publishedSegments: Long get() = synchronized(lock) { nextSequence }

    /** [keyStart]: el bloque empieza en un fotograma clave. Antes del primero no se guarda nada (no se podría decodificar). */
    fun onChunk(chunk: ByteArray, keyStart: Boolean, nowMs: Long) {
        synchronized(lock) {
            if (ended) return
            if (currentStartMs < 0) {
                if (!keyStart) return
                currentStartMs = nowMs
            } else if (keyStart && nowMs - currentStartMs >= config.targetSegmentMs - config.targetSegmentMs / 8) {
                closeCurrent(nowMs)
                currentStartMs = nowMs
            }
            current.write(chunk)
        }
    }

    private fun closeCurrent(nowMs: Long) {
        val data = current.toByteArray()
        current = java.io.ByteArrayOutputStream()
        if (data.isEmpty()) return
        val seg = HlsSegment(nextSequence++, (nowMs - currentStartMs).coerceAtLeast(1), data)
        done.addLast(seg)
        retainedBytes += data.size
        val keep = config.windowSegments + config.extraKept
        while (done.size > keep || (retainedBytes > config.maxBytes && done.size > config.windowSegments)) {
            retainedBytes -= done.removeFirst().data.size
        }
    }

    /** Cierra la emisión: la playlist pasa a incluir `#EXT-X-ENDLIST`. */
    fun end() = synchronized(lock) { ended = true }

    val isEnded: Boolean get() = synchronized(lock) { ended }

    /** Playlist actual, o `null` si todavía no hay ningún segmento completo. */
    fun playlist(): String? = synchronized(lock) {
        if (done.isEmpty()) return null
        val listed = done.toList().takeLast(config.windowSegments)
        HlsPlaylist.render(listed, ended)
    }

    fun segment(sequence: Long): HlsSegment? = synchronized(lock) { done.firstOrNull { it.sequence == sequence } }

    /** Para pruebas y diagnóstico: secuencias retenidas. */
    fun retainedSequences(): List<Long> = synchronized(lock) { done.map { it.sequence } }

    fun retainedBytes(): Long = synchronized(lock) { retainedBytes }
}

object HlsPlaylist {
    /**
     * Playlist HLS versión 3 (la más compatible: duraciones decimales, sin etiquetas modernas).
     * TARGETDURATION es el entero que cubre al segmento más largo (redondeo hacia arriba, como exige la norma).
     */
    fun render(segments: List<HlsSegment>, ended: Boolean): String {
        require(segments.isNotEmpty())
        val target = segments.maxOf { (it.durationMs + 999) / 1000 }.coerceAtLeast(1)
        val sb = StringBuilder()
        sb.append("#EXTM3U\n#EXT-X-VERSION:3\n")
        sb.append("#EXT-X-TARGETDURATION:").append(target).append('\n')
        sb.append("#EXT-X-MEDIA-SEQUENCE:").append(segments.first().sequence).append('\n')
        for (s in segments) {
            sb.append("#EXTINF:").append(String.format(java.util.Locale.US, "%.3f", s.durationMs / 1000.0)).append(",\n")
            sb.append(s.name).append('\n')
        }
        if (ended) sb.append("#EXT-X-ENDLIST\n")
        return sb.toString()
    }
}
