package com.opentv.app.iptv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameTimingTest {
    @Test fun ventanaVaciaNoInventaNumeros() {
        val s = IntervalWindow().snapshot()
        assertEquals(0, s.count); assertEquals("sin datos", s.describe())
    }
    @Test fun percentilesDeUnFlujoParejoConUnTiron() {
        val w = IntervalWindow()
        repeat(99) { w.add(16.7) }
        w.add(120.0)
        val s = w.snapshot()
        assertEquals(100, s.count); assertEquals(16.7, s.p50, 0.001); assertEquals(16.7, s.p95, 0.001)
        assertEquals(120.0, s.max, 0.001); assertTrue(s.describe().contains("máx 120.0"))
    }
    @Test fun laVentanaDescartaLoMasViejo() {
        val w = IntervalWindow(capacity = 10)
        repeat(10) { w.add(100.0) }
        repeat(10) { w.add(10.0) }
        assertEquals(10.0, w.snapshot().max, 0.001)
    }
    @Test fun clearVaciaLaVentana() {
        val w = IntervalWindow(); w.add(5.0); w.clear()
        assertEquals(0, w.snapshot().count)
    }
    @Test fun fpsDelSegmentoSalenDeCuadrosYDuracion() {
        val d = SegmentDelivery(1, 500_000, 800, 4_000, 240, 0)
        val fps: Double? = d.fps
        assertNotNull(fps); assertEquals(60.0, fps!!, 0.001); assertEquals(0.2, d.ratio, 0.001)
    }
    @Test fun sinCuadrosContadosNoHayFps() {
        assertNull(SegmentDelivery(1, 10, 1, 4_000, 0, 0).fps)
    }
    @Test fun resumenSeparacionYPeorEnvio() {
        val log = SegmentDeliveryLog()
        log.record(SegmentDelivery(1, 400_000, 500, 4_000, 240, 1_000))
        log.record(SegmentDelivery(2, 410_000, 5_000, 4_000, 236, 5_000))
        log.record(SegmentDelivery(3, 405_000, 600, 4_000, 238, 12_000))
        val s = log.summary()
        assertEquals(3, s.count); assertEquals(1.25, s.worstRatio, 0.001); assertEquals(7_000L, s.maxGapMs)
        assertTrue(s.describe().contains("peor envío 1.25"))
    }
    @Test fun sinPedidosLoDice() {
        assertTrue(SegmentDeliveryLog().summary().describe().contains("todavía no hubo pedidos"))
    }
    @Test fun elSegmentadorCuentaSoloCuadrosDeVideo() {
        val seg = HlsSegmenter(HlsConfig(targetSegmentMs = 1_000))
        seg.onChunk(byteArrayOf(1), true, 0, video = true)
        seg.onChunk(byteArrayOf(2), false, 100, video = false) // audio
        seg.onChunk(byteArrayOf(3), false, 200, video = true)
        seg.onChunk(byteArrayOf(4), true, 1_000, video = true) // cierra el segmento 0 y abre otro
        val first: HlsSegment? = seg.segment(0)
        assertNotNull(first); assertEquals(2, first!!.frames)
    }
}
