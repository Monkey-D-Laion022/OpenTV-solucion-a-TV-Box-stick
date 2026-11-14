package com.opentv.app.miracast

import com.opentv.app.iptv.IptvPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WfdSourceTest {
    // CEA: bit 6 = 1280x720p60 (0x40), bit 7 = 1080p30 (0x80), bit 8 = 1080p60 (0x100). Niveles: 0x07 = 3.1, 3.2 y 4.0.
    private fun m3(cea: String, level: String = "04", audio: String = "AAC 00000001 00, LPCM 00000003 00"): Map<String, String> = mapOf(
        "wfd_video_formats" to "00 00 01 $level $cea 00000000 00000000 00 0000 0000 00 none none",
        "wfd_audio_codecs" to audio,
        "wfd_client_rtp_ports" to "RTP/AVP/UDP;unicast 19000 0 mode=play",
    )
    private fun req(method: String, headers: Map<String, String> = emptyMap(), body: String = "", cseq: Int = 5) =
        RtspMessage(true, method, "rtsp://x", null, null, headers + ("cseq" to cseq.toString()), body)
    private fun resp(cseq: Int, code: Int = 200) = RtspMessage(false, null, null, code, "OK", mapOf("cseq" to cseq.toString()), "")

    @Test fun eligeElMejorModoQueElTelevisorDeclaraYElNivelMinimo() {
        val (c, _) = WfdFormatChooser.choose(m3("000001C0", level = "04"))
        assertNotNull(c); assertEquals("1920x1080p30", c!!.mode.label); assertEquals(IptvPreset.FULL_HD, c.preset); assertEquals(0x04, c.levelBit)
    }
    @Test fun no1080p60SinNivel42() {
        val (c, _) = WfdFormatChooser.choose(m3("00000140", level = "04")) // 1080p60 y 720p60 pero solo hasta nivel 4.0
        assertEquals("1280x720p60", c!!.mode.label)
    }
    @Test fun con1080p60YNivel42ElegiraSesenta() {
        val (c, _) = WfdFormatChooser.choose(m3("00000100", level = "1f"))
        assertEquals(IptvPreset.FULL_HD_60, c!!.preset); assertEquals(0x10, c.levelBit)
    }
    @Test fun respetaElTopeDePerfilPedido() {
        val (c, _) = WfdFormatChooser.choose(m3("000001C0", level = "1f"), maxPreset = IptvPreset.HD_60)
        assertEquals(IptvPreset.HD_60, c!!.preset)
    }
    @Test fun sinAacElAudioQuedaSinAudioYSeAvisa() {
        val (c, _) = WfdFormatChooser.choose(m3("00000040", audio = "LPCM 00000003 00"))
        assertEquals(WfdAudioChoice.NONE, c!!.audio)
    }
    @Test fun sinModoCompatibleExplicaPorQue() {
        val (c, why) = WfdFormatChooser.choose(m3("00000001")) // solo 640x480
        assertNull(c); assertTrue(why.contains("Ningún modo"))
    }
    @Test fun textoNoInterpretableNoSeAdivina() {
        assertNull(WfdFormatChooser.choose(mapOf("wfd_video_formats" to "basura")).first)
        assertNull(WfdFormatChooser.choose(emptyMap()).first)
    }
    @Test fun m4DeclaraUnSoloModoYLosPuertosDelTelevisor() {
        val c = WfdFormatChooser.choose(m3("000001C0")).first!!
        val text = WfdSourceRtsp.m4(4, "192.168.49.1", 19000, c)
        assertTrue(text.startsWith("SET_PARAMETER rtsp://localhost/wfd1.0 RTSP/1.0\r\nCSeq: 4"))
        assertTrue(text.contains("wfd_video_formats: 00 00 01 04 00000080 00000000 00000000 00 0000 0000 00 none none"))
        assertTrue(text.contains("wfd_audio_codecs: AAC 00000001 00"))
        assertTrue(text.contains("wfd_presentation_URL: rtsp://192.168.49.1/wfd1.0/streamid=0 none"))
        assertTrue(text.contains("wfd_client_rtp_ports: RTP/AVP/UDP;unicast 19000 0 mode=play"))
        val body = text.substringAfter("\r\n\r\n")
        assertTrue(text.contains("Content-Length: ${body.length}"))
    }
    @Test fun elFlujoCompletoHastaTransmitir() {
        val f = WfdSourceFlow("192.168.49.1")
        var (n, acts) = f.start(m3("000001C0"), 4)
        assertEquals(WfdSourceFlow.State.WAIT_M4, f.state); assertTrue(acts.any { it is WfdAction.Send && it.text.startsWith("SET_PARAMETER") })
        var r = f.onMessage(resp(4), n); n = r.first
        assertEquals(WfdSourceFlow.State.WAIT_M5, f.state); assertTrue(r.second.any { it is WfdAction.Send && it.text.contains("wfd_trigger_method: SETUP") })
        r = f.onMessage(resp(5), n); n = r.first
        assertEquals(WfdSourceFlow.State.WAIT_SETUP, f.state)
        r = f.onMessage(req("SETUP", mapOf("transport" to "RTP/AVP/UDP;unicast;client_port=19004"), cseq = 3), n)
        val setup = r.second.filterIsInstance<WfdAction.Send>().single().text
        assertTrue(setup.contains("client_port=19004;server_port=19000")); assertTrue(setup.contains("Session: "))
        assertEquals(WfdSourceFlow.State.WAIT_PLAY, f.state)
        r = f.onMessage(req("PLAY", cseq = 4), r.first)
        val start = r.second.filterIsInstance<WfdAction.StartStream>().single()
        assertEquals(19004, start.clientPort); assertEquals(WfdSourceFlow.State.STREAMING, f.state)
    }
    @Test fun siElTelevisorRechazaM4ElMotivoQuedaClaro() {
        val f = WfdSourceFlow("192.168.49.1"); val (n, _) = f.start(m3("000001C0"), 4)
        val r = f.onMessage(resp(4, 400), n)
        val fail = r.second.filterIsInstance<WfdAction.Fail>().single()
        assertTrue(fail.reason.contains("rechazó M4")); assertEquals(WfdSourceFlow.State.FAILED, f.state)
    }
    @Test fun mantenimientoCuadroClaveYTeardown() {
        val f = WfdSourceFlow("192.168.49.1"); var n = f.start(m3("000001C0"), 4).first
        n = f.onMessage(resp(4), n).first; n = f.onMessage(resp(5), n).first
        n = f.onMessage(req("SETUP", mapOf("transport" to "RTP/AVP/UDP;unicast;client_port=19000")), n).first
        n = f.onMessage(req("PLAY"), n).first
        assertTrue(f.onMessage(req("GET_PARAMETER"), n).second.single() is WfdAction.Send)
        val idr = f.onMessage(req("SET_PARAMETER", body = "wfd_idr_request\r\n"), n).second
        assertTrue(idr.any { it is WfdAction.KeyFrame })
        val td = f.onMessage(req("TEARDOWN"), n).second
        assertTrue(td.any { it is WfdAction.StopStream }); assertTrue(td.any { it is WfdAction.Done }); assertEquals(WfdSourceFlow.State.DONE, f.state)
    }
    @Test fun sinPuertosRtpDeclaradosFalla() {
        val f = WfdSourceFlow("1.1.1.1")
        val bad = m3("00000040") - "wfd_client_rtp_ports"
        assertTrue(f.start(bad, 1).second.single() is WfdAction.Fail)
    }
    @Test fun parseaPuertos() {
        assertEquals(19000, parseClientRtpPort("RTP/AVP/UDP;unicast 19000 0 mode=play"))
        assertEquals(1028, parseTransportClientPort("RTP/AVP/UDP;unicast;client_port=1028-1029"))
        assertNull(parseClientRtpPort("x"))
    }

    // ---- RTP ----
    private fun ts(n: Int) = ByteArray(n * 188) { 0x47 }
    @Test fun rtpAgrupaSietePaquetesTsConCabeceraCorrecta() {
        val p = RtpTsPacketizer(ssrc = 0x01020304, startSeq = 65535)
        val out = p.add(ts(8), 1000)
        assertEquals(1, out.size); assertEquals(12 + 7 * 188, out[0].size)
        assertEquals(0x80, out[0][0].toInt() and 0xFF); assertEquals(33, out[0][1].toInt())
        assertEquals(0xFFFF, ((out[0][2].toInt() and 0xFF) shl 8) or (out[0][3].toInt() and 0xFF))
        assertEquals(90_000, ((out[0][4].toInt() and 0xFF) shl 24) or ((out[0][5].toInt() and 0xFF) shl 16) or ((out[0][6].toInt() and 0xFF) shl 8) or (out[0][7].toInt() and 0xFF))
        assertEquals(0x04, out[0][11].toInt())
        val rest = p.flush(1010)
        assertEquals(1, rest.size); assertEquals(12 + 188, rest[0].size)
        assertEquals(0, ((rest[0][2].toInt() and 0xFF) shl 8) or (rest[0][3].toInt() and 0xFF)) // la secuencia da la vuelta
    }
    @Test fun rtpNoRetieneNadaTrasFlush() { val p = RtpTsPacketizer(); p.add(ts(3), 0); p.flush(0); assertTrue(p.flush(1).isEmpty()) }
    @Test fun rtpRechazaBloquesQueNoSonTs() {
        var failed = false
        try { RtpTsPacketizer().add(ByteArray(100), 0) } catch (e: IllegalArgumentException) { failed = true }
        assertTrue(failed)
    }
    @Test fun elDiagnosticoSinCapturaSigueSinTransmitir() {
        assertFalse(WfdSourceFlow("1.1.1.1").state != WfdSourceFlow.State.IDLE)
    }

    @Test fun nivelBajoDeclaradoNoCortaLaNegociacion() {
        // 1080p30 (bit 7 de CEA = 0x80) con solo nivel 3.1 declarado: antes se rechazaba y M4 no se enviaba
        val (c, _) = WfdFormatChooser.choose(m3("00000080", level = "01"))
        assertNotNull(c); assertEquals(IptvPreset.FULL_HD, c!!.preset); assertEquals(0x01, c.levelBit); assertTrue(c.note.contains("menor al habitual"))
    }
    @Test fun elFalloEnumeraLosModosDeclarados() {
        val (c, why) = WfdFormatChooser.choose(m3("00000003")) // 640x480p60 y 720x480p60
        assertNull(c); assertTrue(why.contains("640x480p60") && why.contains("720x480p60"))
    }
}
