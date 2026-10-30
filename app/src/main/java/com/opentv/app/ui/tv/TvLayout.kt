package com.opentv.app.ui.tv

import kotlin.math.ceil
import kotlin.math.roundToInt

/** Medidas (en dp) de las pantallas rediseñadas. Todas salen de [TvLayout.metrics]. */
data class TvMetrics(
    val contentWidthDp: Int,
    val contentHeightDp: Int,
    val heroHeightDp: Int,
    val heroBodyLines: Int,
    val actionCardWidthDp: Int,
    val actionCardHeightDp: Int,
    val posterWidthDp: Int,
    val posterHeightDp: Int,
    val appTileWidthDp: Int,
    val appTileHeightDp: Int,
)

/**
 * Lógica PURA de medidas. Regla de fondo: la altura de cada tarjeta se calcula para el PEOR caso de texto (el máximo de
 * líneas permitido, con la escala de letra del sistema), de modo que nada se corte. Los textos de las tarjetas llevan
 * `maxLines` y puntos suspensivos; lo que no entra se abrevia, no se recorta a mitad de renglón.
 */
object TvLayout {
    /** Margen de seguridad de las pantallas con barra superior (antes 5%). */
    const val MARGIN_FRACTION = 0.03f
    const val TOP_BAR_DP = 56
    const val GAP_DP = 14

    // Alturas de línea (sp) de la tipografía de OpenTV (ver Type.kt).
    private const val TITLE_MEDIUM_LINE = 28
    private const val BODY_MEDIUM_LINE = 26
    private const val BODY_LARGE_LINE = 28
    private const val HEADLINE_MEDIUM_LINE = 42
    private const val LABEL_LARGE_LINE = 20

    private fun scaled(sp: Int, fontScale: Float): Int = ceil(sp * fontScale.toDouble()).toInt()

    fun metrics(widthDp: Int, heightDp: Int, fontScale: Float): TvMetrics {
        val fs = fontScale.coerceIn(0.85f, 2.0f)
        val contentW = (widthDp * (1f - 2f * MARGIN_FRACTION)).toInt()
        val contentH = (heightDp * (1f - 2f * MARGIN_FRACTION)).toInt() - TOP_BAR_DP

        // Banner: etiqueta + título (1 línea) + texto (2 o 3 líneas) + relleno.
        val bodyLines = if (contentH < 360) 2 else 3
        val heroNeed = scaled(LABEL_LARGE_LINE, fs) + scaled(HEADLINE_MEDIUM_LINE, fs) + scaled(BODY_LARGE_LINE * bodyLines, fs) + 40
        val hero = maxOf((contentH * 0.40f).roundToInt(), heroNeed)

        // Tarjeta de acción: fila superior (barrita/insignia) + título (2 líneas) + subtítulo (2 líneas) + relleno.
        val cardW = (contentW / 3.6f).roundToInt().coerceIn(200, 300)
        val cardH = scaled(LABEL_LARGE_LINE + 8, fs) + scaled(TITLE_MEDIUM_LINE * 2, fs) + scaled(BODY_MEDIUM_LINE * 2, fs) + 32 + 8

        // Póster 2:3.
        val posterW = (contentW / 6.5f).roundToInt().coerceIn(110, 190)
        val posterH = (posterW * 1.5f).roundToInt()

        // Mosaico de app: ícono de 72 dp + etiqueta (2 líneas) + relleno de AppTile.
        val tileW = (contentW / 7f).roundToInt().coerceIn(120, 170)
        val tileH = 72 + 8 + scaled(BODY_MEDIUM_LINE * 2, fs) + 24 + 4

        return TvMetrics(
            contentWidthDp = contentW, contentHeightDp = contentH,
            heroHeightDp = hero, heroBodyLines = bodyLines,
            actionCardWidthDp = cardW, actionCardHeightDp = cardH,
            posterWidthDp = posterW, posterHeightDp = posterH,
            appTileWidthDp = tileW, appTileHeightDp = tileH,
        )
    }

    /** Altura mínima que necesita el texto de una tarjeta de acción (para comprobar que [metrics] no se queda corto). */
    fun actionCardTextNeed(fontScale: Float): Int {
        val fs = fontScale.coerceIn(0.85f, 2.0f)
        return scaled(TITLE_MEDIUM_LINE * 2, fs) + scaled(BODY_MEDIUM_LINE * 2, fs)
    }
}
