package com.opentv.app.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MjpegAdaptiveTest {
    @Test fun congestionBajaLaCalidadHastaElMinimo() {
        val a = MjpegAdaptive(70)
        repeat(3) { a.onSecond(25.0, 10.0, 1) }
        assertEquals(60, a.quality)
        repeat(60) { a.onSecond(25.0, 10.0, 1) }
        assertEquals(30, a.quality)
    }
    @Test fun pantallaQuietaNoEsCongestion() {
        val a = MjpegAdaptive(70)
        repeat(30) { a.onSecond(1.0, 1.0, 1) }
        assertEquals(70, a.quality)
    }
    @Test fun sinClientesNoCambia() {
        val a = MjpegAdaptive(70)
        repeat(30) { a.onSecond(25.0, 0.0, 0) }
        assertEquals(70, a.quality)
    }
    @Test fun conHolguraSostenidaRecuperaDeAPocoSinPasarDelMaximo() {
        val a = MjpegAdaptive(70)
        repeat(6) { a.onSecond(25.0, 5.0, 1) }
        val low = a.quality
        assertTrue(low < 70)
        repeat(200) { a.onSecond(25.0, 25.0, 1) }
        assertEquals(70, a.quality)
    }
    @Test fun elPasoDeSubidaEsMasChicoQueElDeBajada() {
        assertTrue(MjpegAdaptive.STEP_UP < MjpegAdaptive.STEP_DOWN)
    }
}
