package com.opentv.app.miracast

/**
 * Decodificador PURO (sin Android) del parámetro RTSP `wfd_video_formats` de Wi-Fi Display (Miracast).
 *
 * Tablas verificadas contra el código de referencia de Android (frameworks/av, wifi-display/VideoFormats.cpp):
 * el bit `j` de cada máscara (CEA, VESA, HH) es la fila `j` de su tabla. No toca resolución, densidad ni rotación del
 * teléfono, ni usa red: solo interpreta un texto. Decodificar un texto NO demuestra nada sobre un televisor concreto
 * hasta que ese texto provenga de una respuesta real del televisor.
 */
enum class WfdTable { CEA, VESA, HH }

data class WfdMode(
    val table: WfdTable,
    val bit: Int,
    val width: Int,
    val height: Int,
    val fps: Int,
    val interlaced: Boolean,
) {
    val label: String get() = "${width}x$height${if (interlaced) "i" else "p"}$fps"

    /** Puntaje que usa la referencia de Android para elegir (ancho × alto × cuadros). */
    val score: Long get() = width.toLong() * height * fps
}

data class WfdVideoFormats(
    /** Modo nativo declarado (tabla, bit), o `null` si no se pudo interpretar. */
    val native: Pair<WfdTable, Int>?,
    /** Máscara de perfiles H.264 (bit 0 = CBP, bit 1 = CHP) acumulada de todos los códecs. */
    val profiles: Int,
    /** Máscara de niveles H.264 acumulada (bit 0 = 3.1, 1 = 3.2, 2 = 4.0, 3 = 4.1, 4 = 4.2). */
    val levels: Int,
    val modes: List<WfdMode>,
    /** `true` si el texto era `none` (el dispositivo no declara video). */
    val noVideo: Boolean = false,
) {
    fun supports(width: Int, height: Int, fps: Int, allowInterlaced: Boolean = false): Boolean =
        modes.any { it.width == width && it.height == height && it.fps == fps && (allowInterlaced || !it.interlaced) }

    fun maxFpsAt(width: Int, height: Int): Int? =
        modes.filter { it.width == width && it.height == height && !it.interlaced }.maxOfOrNull { it.fps }

    val has1080p60: Boolean get() = supports(1920, 1080, 60)

    /** Mayor cantidad de cuadros por segundo de cualquier modo progresivo declarado. */
    val maxProgressiveFps: Int? get() = modes.filter { !it.interlaced }.maxOfOrNull { it.fps }

    companion object {
        private val CEA = listOf(
            m(WfdTable.CEA, 0, 640, 480, 60), m(WfdTable.CEA, 1, 720, 480, 60), m(WfdTable.CEA, 2, 720, 480, 60, true),
            m(WfdTable.CEA, 3, 720, 576, 50), m(WfdTable.CEA, 4, 720, 576, 50, true), m(WfdTable.CEA, 5, 1280, 720, 30),
            m(WfdTable.CEA, 6, 1280, 720, 60), m(WfdTable.CEA, 7, 1920, 1080, 30), m(WfdTable.CEA, 8, 1920, 1080, 60),
            m(WfdTable.CEA, 9, 1920, 1080, 60, true), m(WfdTable.CEA, 10, 1280, 720, 25), m(WfdTable.CEA, 11, 1280, 720, 50),
            m(WfdTable.CEA, 12, 1920, 1080, 25), m(WfdTable.CEA, 13, 1920, 1080, 50), m(WfdTable.CEA, 14, 1920, 1080, 50, true),
            m(WfdTable.CEA, 15, 1280, 720, 24), m(WfdTable.CEA, 16, 1920, 1080, 24),
        )
        private val VESA = listOf(
            m(WfdTable.VESA, 0, 800, 600, 30), m(WfdTable.VESA, 1, 800, 600, 60), m(WfdTable.VESA, 2, 1024, 768, 30),
            m(WfdTable.VESA, 3, 1024, 768, 60), m(WfdTable.VESA, 4, 1152, 864, 30), m(WfdTable.VESA, 5, 1152, 864, 60),
            m(WfdTable.VESA, 6, 1280, 768, 30), m(WfdTable.VESA, 7, 1280, 768, 60), m(WfdTable.VESA, 8, 1280, 800, 30),
            m(WfdTable.VESA, 9, 1280, 800, 60), m(WfdTable.VESA, 10, 1360, 768, 30), m(WfdTable.VESA, 11, 1360, 768, 60),
            m(WfdTable.VESA, 12, 1366, 768, 30), m(WfdTable.VESA, 13, 1366, 768, 60), m(WfdTable.VESA, 14, 1280, 1024, 30),
            m(WfdTable.VESA, 15, 1280, 1024, 60), m(WfdTable.VESA, 16, 1400, 1050, 30), m(WfdTable.VESA, 17, 1400, 1050, 60),
            m(WfdTable.VESA, 18, 1440, 900, 30), m(WfdTable.VESA, 19, 1440, 900, 60), m(WfdTable.VESA, 20, 1600, 900, 30),
            m(WfdTable.VESA, 21, 1600, 900, 60), m(WfdTable.VESA, 22, 1600, 1200, 30), m(WfdTable.VESA, 23, 1600, 1200, 60),
            m(WfdTable.VESA, 24, 1680, 1024, 30), m(WfdTable.VESA, 25, 1680, 1024, 60), m(WfdTable.VESA, 26, 1680, 1050, 30),
            m(WfdTable.VESA, 27, 1680, 1050, 60), m(WfdTable.VESA, 28, 1920, 1200, 30), m(WfdTable.VESA, 29, 1920, 1200, 60),
        )
        private val HH = listOf(
            m(WfdTable.HH, 0, 800, 480, 30), m(WfdTable.HH, 1, 800, 480, 60), m(WfdTable.HH, 2, 854, 480, 30),
            m(WfdTable.HH, 3, 854, 480, 60), m(WfdTable.HH, 4, 864, 480, 30), m(WfdTable.HH, 5, 864, 480, 60),
            m(WfdTable.HH, 6, 640, 360, 30), m(WfdTable.HH, 7, 640, 360, 60), m(WfdTable.HH, 8, 960, 540, 30),
            m(WfdTable.HH, 9, 960, 540, 60), m(WfdTable.HH, 10, 848, 480, 30), m(WfdTable.HH, 11, 848, 480, 60),
        )

        private fun m(t: WfdTable, bit: Int, w: Int, h: Int, fps: Int, interlaced: Boolean = false) =
            WfdMode(t, bit, w, h, fps, interlaced)

        private fun table(t: WfdTable) = when (t) { WfdTable.CEA -> CEA; WfdTable.VESA -> VESA; WfdTable.HH -> HH }

        /**
         * Interpreta el valor de `wfd_video_formats`. Estructura (AOSP): nativo, preferred-display-mode, y por cada
         * códec H.264: perfil, nivel, máscara CEA, VESA, HH, latencia, min-slice, slice-enc, frame-rate-control,
         * max-hres, max-vres (11 campos). Acepta separador de códecs con coma o solo espacios.
         * Devuelve `null` si el texto no tiene la forma esperada (no se adivina).
         */
        fun parse(value: String): WfdVideoFormats? {
            val text = value.trim()
            if (text.isEmpty()) return null
            if (text.equals("none", ignoreCase = true)) return WfdVideoFormats(null, 0, 0, emptyList(), noVideo = true)
            val tokens = text.replace(',', ' ').split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (tokens.size < 2 + 11 || (tokens.size - 2) % 11 != 0) return null
            val nativeByte = hex(tokens[0], 2) ?: return null
            hex(tokens[1], 2) ?: return null
            val nativeTable = WfdTable.values().getOrNull(nativeByte and 7)
            val native = nativeTable?.let { it to (nativeByte shr 3) }

            var profiles = 0
            var levels = 0
            val found = LinkedHashMap<Pair<WfdTable, Int>, WfdMode>()
            var i = 2
            while (i < tokens.size) {
                val profile = hex(tokens[i], 2) ?: return null
                val level = hex(tokens[i + 1], 2) ?: return null
                val cea = hex(tokens[i + 2], 8) ?: return null
                val vesa = hex(tokens[i + 3], 8) ?: return null
                val hh = hex(tokens[i + 4], 8) ?: return null
                profiles = profiles or profile
                levels = levels or level
                for ((t, mask) in listOf(WfdTable.CEA to cea, WfdTable.VESA to vesa, WfdTable.HH to hh)) {
                    table(t).forEach { mode ->
                        if (mask.toLong() and (1L shl mode.bit) != 0L) found[t to mode.bit] = mode
                    }
                }
                i += 11
            }
            return WfdVideoFormats(native, profiles, levels, found.values.toList())
        }

        private fun hex(token: String, digits: Int): Int? {
            if (token.length != digits) return null
            return token.toLongOrNull(16)?.toInt()
        }

        /**
         * Formato que elegiría la referencia de Android entre los admitidos por AMBOS lados: mayor puntaje.
         * Solo considera modos progresivos (el tratamiento de entrelazados no está verificado).
         */
        fun pickBest(a: WfdVideoFormats, b: WfdVideoFormats): WfdMode? =
            a.modes.filter { x -> !x.interlaced && b.modes.any { it.table == x.table && it.bit == x.bit } }
                .maxByOrNull { it.score }
    }
}
