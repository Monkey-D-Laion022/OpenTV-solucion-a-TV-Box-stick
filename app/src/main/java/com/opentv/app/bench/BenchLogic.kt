package com.opentv.app.bench

/**
 * Lógica PURA (sin Android) de la medición de captura y codificación. Nada de acá toca la resolución, la densidad ni
 * la rotación del teléfono: solo cuenta cuadros y arma textos.
 */

/** Configuración de una prueba: lo que se SOLICITA. Lo que se mide aparece en [PhaseStats]. */
data class BenchConfig(val width: Int, val height: Int, val fps: Int, val bitrate: Int) {
    val label: String get() = "${width}x$height a $fps FPS"
}

/** Lo que Android informa sobre el codificador elegido para una configuración (todo opcional: puede no estar). */
data class EncoderFacts(
    val name: String,
    val hardware: Boolean?,
    val widthRange: String?,
    val heightRange: String?,
    val frameRateForSize: String?,
    val bitrateRange: String?,
    val profileLevel: String?,
)

/**
 * Consulta de compatibilidad: la implementación real usa MediaCodecList; en las pruebas es un doble.
 * Devuelve `null` si el dispositivo NO admite esa combinación (no se ofrece ni se fuerza).
 */
fun interface EncoderLookup {
    fun find(width: Int, height: Int, fps: Int, bitrate: Int): EncoderFacts?
}

object BenchPlan {
    private val SIZES = listOf(1280 to 720, 1920 to 1080)
    private val RATES = listOf(30, 60)

    const val MIN_BITRATE = 1_000_000
    const val MAX_BITRATE = 20_000_000

    /** Bits por píxel y cuadro: valor razonable para pantalla en vivo; se acota a un rango fijo. */
    fun bitrateFor(width: Int, height: Int, fps: Int): Int {
        val raw = width.toLong() * height * fps / 10 // 0,1 bit por píxel
        return raw.coerceIn(MIN_BITRATE.toLong(), MAX_BITRATE.toLong()).toInt()
    }

    data class Option(val config: BenchConfig, val encoder: EncoderFacts)

    /** Solo las configuraciones que el codificador informa como compatibles, de menor a mayor exigencia. */
    fun compatible(lookup: EncoderLookup): List<Option> =
        SIZES.flatMap { (w, h) ->
            RATES.mapNotNull { fps ->
                val bitrate = bitrateFor(w, h, fps)
                lookup.find(w, h, fps, bitrate)?.let { Option(BenchConfig(w, h, fps, bitrate), it) }
            }
        }

    /** Las que el dispositivo NO admite, para decirlo claramente en pantalla (no se prueban). */
    fun unsupported(lookup: EncoderLookup): List<BenchConfig> =
        SIZES.flatMap { (w, h) ->
            RATES.mapNotNull { fps ->
                val bitrate = bitrateFor(w, h, fps)
                if (lookup.find(w, h, fps, bitrate) == null) BenchConfig(w, h, fps, bitrate) else null
            }
        }
}

/**
 * Cuenta cuadros con su marca de tiempo (nanosegundos). Los FPS se calculan sobre una ventana fija de la prueba
 * (sin el arranque), así un tramo sin cuadros baja el promedio en lugar de ocultarse.
 */
class FpsMeter {
    private val ticks = ArrayList<Long>(2_048)

    @Synchronized
    fun tick(nowNanos: Long) {
        if (ticks.size < MAX_TICKS) ticks.add(nowNanos)
    }

    @Synchronized
    fun total(): Int = ticks.size

    /** Cuadros por segundo dentro de [startNanos, endNanos); `null` si la ventana no es válida. */
    @Synchronized
    fun fpsIn(startNanos: Long, endNanos: Long): Double? {
        if (endNanos <= startNanos) return null
        val n = ticks.count { it in startNanos until endNanos }
        return n * 1_000_000_000.0 / (endNanos - startNanos)
    }

    /** Cuadros en cada segundo completo de la ventana (para ver la estabilidad). */
    @Synchronized
    fun perSecond(startNanos: Long, endNanos: Long): List<Int> {
        val seconds = ((endNanos - startNanos) / 1_000_000_000L).toInt()
        if (seconds <= 0) return emptyList()
        val bins = IntArray(seconds)
        for (t in ticks) {
            if (t < startNanos) continue
            val i = ((t - startNanos) / 1_000_000_000L).toInt()
            if (i in 0 until seconds) bins[i]++
        }
        return bins.toList()
    }

    private companion object {
        const val MAX_TICKS = 20_000
    }
}

/** Resultado MEDIDO de una fase (captura o codificación). `null` en un campo = no disponible. */
data class PhaseStats(
    val avgFps: Double?,
    val minFpsPerSecond: Int?,
    val maxFpsPerSecond: Int?,
    val frames: Int,
    val bytes: Long? = null,
    val measuredKbps: Int? = null,
)

data class ConfigResult(
    val config: BenchConfig,
    val encoder: EncoderFacts,
    val capture: PhaseStats?,
    val encode: PhaseStats?,
    val error: String? = null,
)

/** Foto de los indicadores térmicos públicos antes/después. Todo opcional. */
data class ThermalSnapshot(val androidStatus: String?, val batteryCelsius: Double?)

object BenchFormat {
    const val NA = "no disponible"

    fun fps(value: Double?): String = if (value == null) NA else String.format(java.util.Locale.US, "%.1f", value)

    fun stats(s: PhaseStats?): String {
        if (s == null) return NA
        val range = if (s.minFpsPerSecond != null && s.maxFpsPerSecond != null) {
            " (por segundo: mín ${s.minFpsPerSecond}, máx ${s.maxFpsPerSecond})"
        } else {
            ""
        }
        return "${fps(s.avgFps)} FPS$range, ${s.frames} cuadros"
    }

    fun bitrate(bitsPerSecond: Int): String = String.format(java.util.Locale.US, "%.1f Mbps", bitsPerSecond / 1_000_000.0)

    fun thermal(t: ThermalSnapshot?): String {
        if (t == null) return NA
        val a = t.androidStatus ?: NA
        val b = t.batteryCelsius?.let { String.format(java.util.Locale.US, "%.1f °C", it) } ?: NA
        return "estado térmico de Android: $a; temperatura de la batería: $b (no es la del procesador)"
    }

    /** Texto de un resultado. Distingue SOLICITADO de MEDIDO y nunca presenta una estimación como medida. */
    fun result(r: ConfigResult): List<String> {
        val lines = mutableListOf<String>()
        lines += "Solicitado: ${r.config.label}, ${bitrate(r.config.bitrate)}"
        lines += "Codificador: ${r.encoder.name}" + when (r.encoder.hardware) {
            true -> " (por hardware)"
            false -> " (por software)"
            null -> " (hardware: $NA)"
        }
        if (r.error != null) {
            lines += "Error: ${r.error}"
            return lines
        }
        lines += "Captura medida (solo captura): ${stats(r.capture)}"
        lines += "Codificación medida (captura + codificador): ${stats(r.encode)}"
        lines += "Bitrate medido: " + (r.encode?.measuredKbps?.let { "$it kbps" } ?: NA)
        lines += "Cuadros perdidos: $NA (la captura directa al codificador no informa pérdidas de forma fiable)"
        return lines
    }
}
