package com.opentv.app.ui.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas de la geometría pura. Los tamaños de pantalla son representativos (NO son los de un dispositivo concreto):
 * que nada se corte en la pantalla real solo se confirma mirándola.
 */
class TvLayoutTest {
    private val screens = listOf(
        "teléfono horizontal" to (891 to 411),
        "teléfono compacto" to (640 to 360),
        "TV 1080p (960×540 dp)" to (960 to 540),
        "TV 720p (1280×720 dp)" to (1280 to 720),
    )
    private val scales = listOf(0.85f, 1.0f, 1.15f, 1.3f, 1.5f)

    @Test
    fun actionCardsAlwaysFitTheirWorstCaseText() {
        for ((name, wh) in screens) for (fs in scales) {
            val m = TvLayout.metrics(wh.first, wh.second, fs)
            val textNeed = TvLayout.actionCardTextNeed(fs)
            // 16+16 de relleno, fila superior y separación: la altura debe superar el texto con holgura.
            assertTrue("$name x$fs: tarjeta ${m.actionCardHeightDp} < texto $textNeed", m.actionCardHeightDp >= textNeed + 32 + 28)
        }
    }

    @Test
    fun cardsAndPostersNeverExceedTheContentWidth() {
        for ((name, wh) in screens) for (fs in scales) {
            val m = TvLayout.metrics(wh.first, wh.second, fs)
            assertTrue("$name: tarjeta más ancha que el contenido", m.actionCardWidthDp <= m.contentWidthDp)
            assertTrue("$name: póster más ancho que el contenido", m.posterWidthDp <= m.contentWidthDp)
            assertTrue("$name: dos tarjetas deben asomar", m.actionCardWidthDp * 2 < m.contentWidthDp)
        }
    }

    @Test
    fun heroGrowsWithFontScaleAndNeverCutsItsText() {
        for ((name, wh) in screens) {
            var previous = 0
            for (fs in scales) {
                val m = TvLayout.metrics(wh.first, wh.second, fs)
                assertTrue("$name x$fs: banner no crece con la letra", m.heroHeightDp >= previous)
                previous = m.heroHeightDp
                val need = Math.ceil((20 + 42 + 28 * m.heroBodyLines) * fs.toDouble()).toInt() + 40
                assertTrue("$name x$fs: banner ${m.heroHeightDp} < $need", m.heroHeightDp >= need)
            }
        }
    }

    @Test
    fun appTilesFitIconAndTwoLineLabel() {
        for (fs in scales) {
            val m = TvLayout.metrics(891, 411, fs)
            val label = Math.ceil(26 * 2 * fs.toDouble()).toInt()
            assertTrue(m.appTileHeightDp >= 72 + 8 + label + 24)
            assertTrue(m.appTileWidthDp in 120..170)
        }
    }

    @Test
    fun postersKeepTheTwoByThreeRatio() {
        val m = TvLayout.metrics(960, 540, 1.0f)
        assertEquals(1.5f, m.posterHeightDp / m.posterWidthDp.toFloat(), 0.02f)
        assertTrue(m.posterWidthDp in 110..190)
    }

    @Test
    fun bannerIsAtLeastFortyPercentOfTheContentHeightOnNormalLetter() {
        for ((name, wh) in screens) {
            val m = TvLayout.metrics(wh.first, wh.second, 1.0f)
            assertTrue("$name: banner pequeño", m.heroHeightDp >= (m.contentHeightDp * 0.40f).toInt() - 1)
        }
    }
}
