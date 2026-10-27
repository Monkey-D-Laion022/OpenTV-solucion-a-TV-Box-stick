package com.opentv.app.bench

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchLogicTest {

    private fun facts(name: String = "c2.fake.avc.encoder") =
        EncoderFacts(name, true, "96-4096", "96-4096", "1.0-60.0", "1-20000000", null)

    @Test
    fun onlyConfigurationsTheEncoderAcceptsAreOffered() {
        // El codificador falso admite hasta 1080p30 y 720p60, pero NO 1080p60.
        val lookup = EncoderLookup { w, h, fps, _ -> if (w * h * fps <= 1920 * 1080 * 30) facts() else null }
        val ok = BenchPlan.compatible(lookup).map { it.config.label }
        assertEquals(listOf("1280x720 a 30 FPS", "1280x720 a 60 FPS", "1920x1080 a 30 FPS"), ok)
        assertEquals(listOf("1920x1080 a 60 FPS"), BenchPlan.unsupported(lookup).map { it.label })
    }

    @Test
    fun nothingIsOfferedWhenThereIsNoEncoder() {
        val none = EncoderLookup { _, _, _, _ -> null }
        assertTrue(BenchPlan.compatible(none).isEmpty())
        assertEquals(4, BenchPlan.unsupported(none).size)
    }

    @Test
    fun sixtyFpsIsNeverOfferedUnlessTheEncoderReportsIt() {
        val only30 = EncoderLookup { _, _, fps, _ -> if (fps <= 30) facts() else null }
        assertTrue(BenchPlan.compatible(only30).none { it.config.fps == 60 })
    }

    @Test
    fun bitrateIsClampedToAFixedRange() {
        assertEquals(BenchPlan.MIN_BITRATE, BenchPlan.bitrateFor(160, 90, 30))
        assertEquals(BenchPlan.MAX_BITRATE, BenchPlan.bitrateFor(3840, 2160, 60))
        assertEquals(2_764_800, BenchPlan.bitrateFor(1280, 720, 30))
    }

    @Test
    fun meterCountsOnlyTheFramesInsideTheWindow() {
        val m = FpsMeter()
        val s = 1_000_000_000L
        // Arranque lento (se ignora): 5 cuadros en el primer segundo; luego 30 por segundo durante 3 s.
        repeat(5) { m.tick(it * s / 5) }
        for (sec in 1..3) repeat(30) { m.tick(sec * s + it * s / 30) }
        assertEquals(30.0, m.fpsIn(1 * s, 4 * s)!!, 0.001)
        assertEquals(listOf(30, 30, 30), m.perSecond(1 * s, 4 * s))
        assertEquals(95, m.total())
    }

    @Test
    fun aStallLowersTheAverageInsteadOfHidingIt() {
        val m = FpsMeter()
        val s = 1_000_000_000L
        repeat(30) { m.tick(it * s / 30) } // solo el primer segundo trae cuadros
        assertEquals(15.0, m.fpsIn(0, 2 * s)!!, 0.001)
        assertEquals(listOf(30, 0), m.perSecond(0, 2 * s))
    }

    @Test
    fun invalidWindowGivesNoValue() {
        val m = FpsMeter()
        assertNull(m.fpsIn(10, 10))
        assertNull(m.fpsIn(20, 10))
        assertTrue(m.perSecond(0, 500_000_000L).isEmpty())
    }

    @Test
    fun unavailableMetricsSayNoDisponibleNeverAnEstimate() {
        assertEquals("no disponible", BenchFormat.fps(null))
        assertEquals("29.9", BenchFormat.fps(29.94))
        assertEquals("no disponible", BenchFormat.stats(null))
        assertEquals("no disponible", BenchFormat.thermal(null))
        assertTrue(BenchFormat.thermal(ThermalSnapshot(null, null)).contains("no disponible"))
        assertTrue(BenchFormat.thermal(ThermalSnapshot("LIGHT", 31.25)).contains("no es la del procesador"))
    }

    @Test
    fun resultTextSeparatesRequestedFromMeasuredAndAdmitsUnknownLosses() {
        val cfg = BenchConfig(1920, 1080, 60, BenchPlan.bitrateFor(1920, 1080, 60))
        val r = ConfigResult(
            cfg, facts(),
            capture = PhaseStats(58.2, 55, 60, 349),
            encode = PhaseStats(41.0, 38, 44, 246, bytes = 3_000_000, measuredKbps = 3000),
        )
        val text = BenchFormat.result(r).joinToString("\n")
        assertTrue(text.contains("Solicitado: 1920x1080 a 60 FPS"))
        assertTrue(text.contains("Captura medida"))
        assertTrue(text.contains("41.0 FPS"))
        assertTrue(text.contains("Bitrate medido: 3000 kbps"))
        assertTrue(text.contains("Cuadros perdidos: no disponible"))
        assertFalse(text.contains("estimad"))
    }

    @Test
    fun anErrorIsShownAndNoMetricsAreInvented() {
        val r = ConfigResult(BenchConfig(1280, 720, 30, 2_000_000), facts(), null, null, error = "el codificador no arrancó")
        val text = BenchFormat.result(r).joinToString("\n")
        assertTrue(text.contains("Error: el codificador no arrancó"))
        assertFalse(text.contains("Captura medida"))
    }
}
