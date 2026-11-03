package com.opentv.app.iptv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamHealthTest {
    private fun judge(
        sinceFrame: Long = 100, elapsed: Long = 60_000, pump: Boolean = true, captured: Double? = 30.0,
        sinceDelivery: Long = 500, delivered: Int = 3000, clients: Int = 1, net: Boolean = true,
    ): StreamHealth.Verdict = StreamHealth.judge(sinceFrame, elapsed, pump, captured, sinceDelivery, delivered, clients, net)

    @Test fun saludableConClienteYDatos() {
        val v = judge()
        assertTrue(v.healthy); assertTrue(v.text.contains("entregando datos"))
    }

    @Test fun sinCuadrosNoEsSaludableAunqueElServicioSigaVivo() {
        val v = judge(sinceFrame = 5_000)
        assertFalse(v.healthy); assertTrue(v.text.contains("5 s"))
    }

    @Test fun sinCuadrosConCapturaDirectaSugiereRitmoFijo() {
        val v = judge(sinceFrame = 4_000, pump = false)
        assertFalse(v.healthy); assertTrue(v.text.contains("ritmo fijo"))
    }

    @Test fun clienteConectadoSinDatosEsProblema() {
        val v = judge(sinceDelivery = 12_000, delivered = 0)
        assertFalse(v.healthy); assertTrue(v.text.contains("no se entregan datos"))
    }

    @Test fun sinClientesEsSaludablePeroLoDice() {
        val v = judge(clients = 0, sinceDelivery = 60_000, delivered = 0)
        assertTrue(v.healthy); assertTrue(v.text.contains("ningún equipo"))
    }

    @Test fun sinRedNoEsSaludable() {
        assertFalse(judge(net = false).healthy)
    }

    @Test fun alPrincipioSoloDiceIniciando() {
        assertEquals("Iniciando…", judge(sinceFrame = 9_000, elapsed = 2_000).text)
    }

    @Test fun pantallaQuietaSeAnotaSinSerFallo() {
        val v = judge(captured = 0.2)
        assertTrue(v.healthy); assertTrue(v.text.contains("se repiten cuadros"))
    }

    @Test fun todosLosMotivosDeParadaTienenTexto() {
        for (r in StopReason.values()) assertTrue(r.name, r.label.isNotBlank())
        assertTrue(StopReason.values().size >= 9)
    }

    @Test fun soloLosPerfilesViejosPidenBaseline() {
        assertTrue(IptvPreset.COMPAT_480.baselineProfile)
        assertTrue(IptvPreset.COMPAT_360.baselineProfile)
        assertFalse(IptvPreset.FULL_HD_60.baselineProfile)
        assertFalse(IptvPreset.BALANCED.baselineProfile)
    }
}
