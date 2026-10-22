package com.opentv.app.core

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Utilidades puras (sin dependencias de Android) para describir la relación de aspecto de una
 * pantalla. Son independientes de la orientación: 1080×1920 y 1920×1080 dan el mismo resultado.
 */
object AspectRatio {

    private const val TOLERANCE = 0.03f
    private const val RATIO_16_9 = 16f / 9f

    private val knownRatios = listOf(
        4f / 3f to "4:3",
        16f / 10f to "16:10",
        RATIO_16_9 to "16:9",
        2f to "18:9",
        19.5f / 9f to "19.5:9",
        20f / 9f to "20:9",
        21f / 9f to "21:9",
    )

    /** Lado largo dividido por el lado corto, o `null` si las medidas no son válidas. */
    fun ratioOf(width: Int, height: Int): Float? {
        if (width <= 0 || height <= 0) return null
        return max(width, height).toFloat() / min(width, height).toFloat()
    }

    /** Texto aproximado como "16:9"; si no coincide con una proporción conocida, "2.11:1". */
    fun describe(width: Int, height: Int): String {
        val ratio = ratioOf(width, height) ?: return "desconocida"
        val nearest = knownRatios.minByOrNull { abs(it.first - ratio) }
        return if (nearest != null && abs(nearest.first - ratio) <= TOLERANCE) {
            nearest.second
        } else {
            String.format(Locale.US, "%.2f:1", ratio)
        }
    }

    fun isApproximately16by9(width: Int, height: Int): Boolean {
        val ratio = ratioOf(width, height) ?: return false
        return abs(ratio - RATIO_16_9) <= TOLERANCE
    }
}
