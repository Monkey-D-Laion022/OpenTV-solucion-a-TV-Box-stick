package com.opentv.app.stream

/** [fps] es el tope: lo real lo marca la velocidad del teléfono para comprimir JPEG. Calidad del espejo por navegador: más baja = menos datos y menos retraso en teléfonos viejos o Wi‑Fi flojo. */
enum class MjpegQuality(val label: String, val maxSide: Int, val jpegQuality: Int, val fps: Int) {
    LOW("Baja (rápida)", 960, 45, 30),
    MEDIUM("Media", 1280, 60, 30),
    HIGH("Alta (1080p, pesada)", 1920, 70, 60);

    fun next(): MjpegQuality = values()[(ordinal + 1) % values().size]

    /** Tamaño de captura que conserva la proporción y no supera [maxSide]; ambos lados pares. */
    fun captureSize(screenW: Int, screenH: Int): Pair<Int, Int> {
        val longest = maxOf(screenW, screenH)
        val scale = if (longest <= maxSide) 1.0 else maxSide.toDouble() / longest
        val w = (screenW * scale).toInt() / 2 * 2
        val h = (screenH * scale).toInt() / 2 * 2
        return maxOf(w, 2) to maxOf(h, 2)
    }
}
