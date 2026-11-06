package com.opentv.app.iptv

/** Estadística de una ventana de intervalos entre cuadros, en milisegundos. */
data class IntervalStats(val count: Int, val p50: Double, val p95: Double, val p99: Double, val max: Double) {
    fun describe(): String =
        if (count == 0) "sin datos" else "p50 ${fmt(p50)} ms · p95 ${fmt(p95)} ms · p99 ${fmt(p99)} ms · máx ${fmt(max)} ms"

    private fun fmt(v: Double) = String.format(java.util.Locale.US, "%.1f", v)
}

/**
 * Ventana deslizante de intervalos entre cuadros (más reciente al final). Segura entre hilos.
 * Para un flujo parejo a 60 fps el p50 ronda 16,7 ms y el p99 no se aleja mucho; un p99 grande con p50 normal indica tirones.
 */
class IntervalWindow(private val capacity: Int = 600) {
    private val values = ArrayDeque<Double>()

    fun add(ms: Double) = synchronized(values) {
        values.addLast(ms)
        while (values.size > capacity) values.removeFirst()
    }

    fun clear() = synchronized(values) { values.clear() }

    fun snapshot(): IntervalStats {
        val sorted: List<Double> = synchronized(values) { values.sorted() }
        if (sorted.isEmpty()) return IntervalStats(0, 0.0, 0.0, 0.0, 0.0)
        fun pct(p: Double): Double = sorted[((sorted.size - 1) * p).toInt().coerceIn(0, sorted.size - 1)]
        return IntervalStats(sorted.size, pct(0.50), pct(0.95), pct(0.99), sorted.last())
    }
}

/** Una entrega de segmento HLS a un cliente. */
data class SegmentDelivery(
    val sequence: Long, val bytes: Int, val serveMs: Long, val durationMs: Long, val frames: Int, val atMs: Long,
) {
    /** Cuadros de video por segundo realmente contenidos en el segmento (`null` si no se contaron). */
    val fps: Double? get() = if (frames > 0 && durationMs > 0) frames * 1000.0 / durationMs else null

    /** Tiempo de envío dividido por la duración del segmento: sostenido > 1 significa que el televisor se queda sin datos. */
    val ratio: Double get() = if (durationMs > 0) serveMs.toDouble() / durationMs else 0.0
}

/** Registro de las últimas entregas de segmentos: tiempo de envío, tamaño, cuadros y separación entre pedidos. */
class SegmentDeliveryLog(private val capacity: Int = 24) {
    private val items = ArrayDeque<SegmentDelivery>()

    fun record(d: SegmentDelivery) = synchronized(items) {
        items.addLast(d)
        while (items.size > capacity) items.removeFirst()
    }

    data class Summary(
        val count: Int, val lastFps: Double?, val lastKb: Int, val worstRatio: Double, val avgGapMs: Long?, val maxGapMs: Long?,
    ) {
        fun describe(): String {
            if (count == 0) return "Segmentos: todavía no hubo pedidos."
            val fpsText = lastFps?.let { String.format(java.util.Locale.US, "%.1f", it) + " cuadros/s en el último segmento" } ?: "cuadros por segmento sin contar"
            val gap = if (avgGapMs != null && maxGapMs != null) " · separación entre pedidos: media $avgGapMs ms, máx $maxGapMs ms" else ""
            return "Segmentos: $fpsText · último ${lastKb} KB · peor envío ${String.format(java.util.Locale.US, "%.2f", worstRatio)}× su duración$gap"
        }
    }

    fun summary(): Summary {
        val list: List<SegmentDelivery> = synchronized(items) { items.toList() }
        if (list.isEmpty()) return Summary(0, null, 0, 0.0, null, null)
        val gaps = list.zipWithNext { a, b -> b.atMs - a.atMs }
        return Summary(
            list.size, list.lastOrNull { it.fps != null }?.fps, list.last().bytes / 1024, list.maxOf { it.ratio },
            if (gaps.isEmpty()) null else gaps.average().toLong(), gaps.maxOrNull(),
        )
    }
}
