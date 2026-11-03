package com.opentv.app.iptv

import com.opentv.app.iptv.FpsGovernor.Bottleneck
import com.opentv.app.iptv.FpsGovernor.Sample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FpsGovernorTest {
    private fun ok(fps: Int) = Sample(fps.toDouble(), fps.toDouble(), 8000, 8000, 1, false)
    private fun run(g: FpsGovernor, s: Sample, seconds: Int): FpsGovernor.Decision {
        var last = g.onSecond(s)
        repeat(seconds - 1) { val d = g.onSecond(s); if (d.changed) last = d }
        return last
    }

    @Test fun estableNoCambiaNada() {
        val g = FpsGovernor(60)
        val d = run(g, ok(60), 60)
        assertFalse(d.changed); assertEquals(60, g.targetFps); assertEquals(Bottleneck.NONE, g.bottleneck)
    }
    @Test fun noActuaDuranteElCalentamiento() {
        val g = FpsGovernor(60)
        repeat(FpsGovernor.WARMUP_S) { assertFalse(g.onSecond(Sample(10.0, 60.0, 1, 1, 0, false)).changed) }
    }
    @Test fun codificadorQueSoloDa38BajaDe60A30() {
        val g = FpsGovernor(60)
        val d = run(g, Sample(38.0, 60.0, 8000, 8000, 1, false), 40)
        assertEquals(30, g.targetFps); assertTrue(d.note!!.contains("codificador"))
    }
    @Test fun siElDibujadoNoLlegaElCuelloEsElDibujado() {
        val g = FpsGovernor(60)
        run(g, Sample(40.0, 40.0, 8000, 8000, 1, false), 40)
        assertEquals(Bottleneck.DRAW, g.lastCause)
    }
    @Test fun temperaturaTienePrioridadComoCausa() {
        val g = FpsGovernor(60)
        run(g, Sample(35.0, 35.0, 8000, 8000, 1, true), 40)
        assertEquals(Bottleneck.THERMAL, g.lastCause)
    }
    @Test fun redQueEntregaLaMitadEsRed() {
        val g = FpsGovernor(30)
        run(g, Sample(30.0, 30.0, 6000, 1000, 1, false), 20)
        assertEquals(Bottleneck.NETWORK, g.lastCause)
    }
    @Test fun sinClientesLaRedNoCuenta() {
        val g = FpsGovernor(30)
        run(g, Sample(30.0, 30.0, 6000, 0, 0, false), 30)
        assertEquals(Bottleneck.NONE, g.bottleneck)
    }
    @Test fun noBajaDelMinimoYLoDice() {
        val g = FpsGovernor(15)
        val notes = (1..30).mapNotNull { g.onSecond(Sample(5.0, 5.0, 100, 100, 1, false)).note }
        assertEquals(15, g.targetFps); assertTrue(notes.any { it.contains("mínimo") })
    }
    @Test fun tras45sEstablesPruebaSubirPeroNuncaPorEncimaDeLoPedido() {
        val g = FpsGovernor(60)
        run(g, Sample(38.0, 60.0, 8000, 8000, 1, false), 40)
        assertEquals(30, g.targetFps)
        var up = false
        repeat(260) { if (g.onSecond(ok(g.targetFps)).changed) up = true }
        assertTrue(up); assertTrue(g.targetFps <= 60)
    }
    @Test fun siLaSubidaFallaNoReintentaElMismoEscalon() {
        val g = FpsGovernor(60)
        run(g, Sample(38.0, 60.0, 8000, 8000, 1, false), 40)
        repeat(260) { g.onSecond(ok(g.targetFps)) } // vence el bloqueo de 180 s y sube a 60
        assertEquals(60, g.targetFps)
        run(g, Sample(38.0, 60.0, 8000, 8000, 1, false), 20) // falla y vuelve a 30
        assertEquals(30, g.targetFps)
        repeat(120) { g.onSecond(ok(g.targetFps)) }
        assertEquals(30, g.targetFps)
    }
    @Test fun pedidoMenorQueUnEscalonNoSube() {
        val g = FpsGovernor(30)
        run(g, Sample(20.0, 30.0, 3000, 3000, 1, false), 40)
        repeat(100) { g.onSecond(ok(g.targetFps)) }
        assertTrue(g.targetFps <= 30)
    }
    @Test fun estadoTermicoSeInterpreta() {
        assertTrue(FpsGovernor.thermalLimited("severo")); assertFalse(FpsGovernor.thermalLimited("normal")); assertFalse(FpsGovernor.thermalLimited(null))
    }
}
