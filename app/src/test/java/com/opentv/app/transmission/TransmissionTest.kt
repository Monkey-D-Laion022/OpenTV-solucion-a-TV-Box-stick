package com.opentv.app.transmission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransmissionTest {
    private fun info(id: String, tech: String, fps: Double? = 29.0, aspect: Double? = 16.0 / 9) =
        TransmissionInfo(id, tech, 1280, 720, 30, fps, 3100, "capturando y se detecta sonido", aspect)

    @Test fun sinTransmisiones() = assertEquals("Detenido.", TransmissionText.render(emptyList()).text)
    @Test fun unaTransmisionMuestraTodo() {
        val t = TransmissionText.render(listOf(info("iptv", "IPTV")))
        assertTrue(t.title.contains("IPTV"))
        assertTrue(t.text.contains("1280×720") && t.text.contains("pedido 30") && t.text.contains("29 fps") && t.text.contains("3.1 Mbit/s"))
        assertTrue(t.text.contains("pantalla en 16:9"))
    }
    @Test fun datosFaltantesDicenNoDisponible() {
        val line = TransmissionText.line(TransmissionInfo("x", "OpenTV Link", 1920, 1080, 60, null, null, "pista en silencio", null))
        assertTrue(line.contains("codificados: no disponible fps") && line.contains("no disponible") && line.contains("16:9: no determinado"))
    }
    @Test fun variasTransmisiones() {
        val t = TransmissionText.render(listOf(info("a", "IPTV"), info("b", "OpenTV Link")))
        assertTrue(t.title.contains("2 transmisiones")); assertEquals(2, t.bigText.lines().size)
    }
    @Test fun dieciseisNovenos() {
        assertTrue(TransmissionText.isSixteenNine(1920.0 / 1080)); assertTrue(TransmissionText.isSixteenNine(2400.0 / 1080 * 0.8))
        assertFalse(TransmissionText.isSixteenNine(720.0 / 1640)); assertFalse(TransmissionText.isSixteenNine(4.0 / 3))
    }
    @Test fun registroPublicaActualizaYDetiene() {
        var stopped = 0
        TransmissionRegistry.publish(info("t1", "IPTV")) { stopped++ }
        TransmissionRegistry.publish(info("t1", "IPTV", fps = 31.0)) { stopped += 100 } // actualiza, conserva el primer stop
        TransmissionRegistry.publish(info("t2", "OpenTV Link")) { stopped++ }
        assertEquals(2, TransmissionRegistry.state.value.size)
        assertEquals(31.0, TransmissionRegistry.state.value.first { it.id == "t1" }.encodedFps!!, 0.0)
        TransmissionRegistry.stopAll()
        assertEquals(2, stopped)
        TransmissionRegistry.remove("t1"); TransmissionRegistry.remove("t2")
        assertTrue(TransmissionRegistry.isEmpty)
    }
}
