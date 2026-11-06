package com.opentv.app.media.dlna

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DlnaLogicTest {
    private fun pi(s: String): ProtocolInfo { val p: ProtocolInfo? = ProtocolInfo.parse(s); assertNotNull(s, p); return p!! }

    @Test fun parseaProtocolInfoYSuPerfil() {
        val p = pi("http-get:*:video/vnd.dlna.mpeg-tts:DLNA.ORG_PN=AVC_TS_HD_50_AAC;DLNA.ORG_OP=01")
        assertEquals("AVC_TS_HD_50_AAC", p.profileName); assertTrue(p.isHttp)
        assertNull(ProtocolInfo.parse("basura"))
    }
    @Test fun leeElSinkDeUnaRespuestaSoap() {
        val xml = "<s:Envelope><s:Body><u:GetProtocolInfoResponse><Source></Source><Sink>http-get:*:video/mpeg:*, http-get:*:audio/mpeg:DLNA.ORG_PN=MP3</Sink></u:GetProtocolInfoResponse></s:Body></s:Envelope>"
        val sink = DlnaXml.parseSink(xml)
        assertEquals(2, sink.size); assertEquals("video/mpeg", sink[0].contentFormat)
    }
    @Test fun sinkVacioODesconocidoDaListaVacia() {
        assertTrue(DlnaXml.parseSink("<x/>").isEmpty())
    }
    @Test fun descripcionDelTelevisorResuelveElControl() {
        val xml = """<root><URLBase>http://192.168.1.50:8080/</URLBase><device><deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
            <friendlyName>[LG] webOS TV</friendlyName><manufacturer>LG</manufacturer><modelName>42LB5800</modelName><UDN>uuid:abc</UDN>
            <serviceList><service><serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType><controlURL>/dmr/upnp/control/AVTransport1</controlURL></service>
            <service><serviceType>urn:schemas-upnp-org:service:ConnectionManager:1</serviceType><controlURL>/dmr/upnp/control/CM1</controlURL></service></serviceList></device></root>"""
        val r: Renderer? = DlnaXml.parseRenderer(xml, "http://192.168.1.50:8080/dd.xml")
        assertNotNull(r); assertEquals("http://192.168.1.50:8080/dmr/upnp/control/AVTransport1", r!!.avTransport.controlUrl)
        assertEquals("[LG] webOS TV", r.friendlyName); assertNotNull(r.connectionManager); assertNull(r.renderingControl)
    }
    @Test fun unDispositivoQueNoEsRenderizadorSeIgnora() {
        assertNull(DlnaXml.parseRenderer("<root><device><deviceType>InternetGatewayDevice</deviceType></device></root>", "http://x/dd.xml"))
    }
    @Test fun escapaElXmlDelEnlace() {
        val soap = DlnaXml.setUri("urn:schemas-upnp-org:service:AVTransport:1", "http://a/b?x=1&y=2", DlnaXml.didl("T", "http://a/b?x=1&y=2", "http-get:*:video/mpeg:*"))
        assertTrue(soap.contains("x=1&amp;y=2")); assertFalse(soap.contains("x=1&y=2"))
    }
    @Test fun tipoDeServicioInvalidoSeRechaza() {
        var failed = false
        try { DlnaXml.play("evil\"><x") } catch (e: IllegalArgumentException) { failed = true }
        assertTrue(failed)
    }
    @Test fun estadoDeTransporte() {
        val info = DlnaXml.parseTransportInfo("<r><CurrentTransportState>PLAYING</CurrentTransportState><CurrentTransportStatus>OK</CurrentTransportStatus></r>")
        assertEquals(TransportState.PLAYING, info.state); assertEquals("OK", info.status)
        assertEquals(TransportState.UNKNOWN, TransportState.parse("RARO"))
    }
    @Test fun detalleDeErrorUpnp() {
        assertEquals("Invalid Action (código 401)", DlnaXml.errorDetail("<errorCode>401</errorCode><errorDescription>Invalid Action</errorDescription>"))
    }

    // ---- selector ----
    @Test fun prefiereAvcConAacYDescartaAc3() {
        val sel = ProfileSelector.select(listOf(
            pi("http-get:*:video/mpeg:DLNA.ORG_PN=AVC_TS_HD_50_AC3"),
            pi("http-get:*:video/vnd.dlna.mpeg-tts:DLNA.ORG_PN=AVC_TS_HD_50_AAC"),
            pi("http-get:*:video/mpeg:DLNA.ORG_PN=MPEG_TS_SD_EU")))
        val first = sel.attempts.first()
        assertEquals("AVC_TS_HD_50_AAC", first.profileName); assertTrue(first.declaredByRenderer); assertEquals(1080, first.height)
        assertFalse(sel.attempts.any { it.profileName == "AVC_TS_HD_50_AC3" })
    }
    @Test fun elGenericoSiempreVaAlFinal() {
        val sel = ProfileSelector.select(listOf(pi("http-get:*:video/mpeg:*")))
        assertFalse(sel.attempts.last().declaredByRenderer); assertTrue(sel.attempts.size >= 3)
        assertTrue(sel.attempts.first().declaredByRenderer)
    }
    @Test fun sinListaSoloGenericoPrimero1080Luego480() {
        val sel = ProfileSelector.select(null)
        assertEquals(listOf(1080, 480), sel.attempts.map { it.height }); assertTrue(sel.reasons.first().contains("No se pudo leer"))
    }
    @Test fun noOfreceSesentaFps() {
        val sel = ProfileSelector.select(listOf(pi("http-get:*:video/mpeg:DLNA.ORG_PN=AVC_TS_HD_60_AAC")))
        assertTrue(sel.attempts.all { it.fps <= 30 })
    }
    @Test fun descartaPerfilesCifYFormatosQueNoSonTs() {
        val sel = ProfileSelector.select(listOf(pi("http-get:*:video/mp4:DLNA.ORG_PN=AVC_MP4_BL_CIF15_AAC"), pi("http-get:*:video/mpeg:DLNA.ORG_PN=AVC_TS_BL_CIF15_AAC")))
        assertTrue(sel.attempts.none { it.declaredByRenderer })
    }
    @Test fun elLiteralDeclaradoConservaBanderasDeVivo() {
        val sel = ProfileSelector.select(listOf(pi("http-get:*:video/mpeg:DLNA.ORG_PN=AVC_TS_HD_50_AAC")))
        assertTrue(sel.attempts.first().protocolInfo.contains("DLNA.ORG_OP=00"))
    }

    // ---- supervisor ----
    @Test fun llegaAPlayingYSeEstabiliza() {
        val s = SessionSupervisor(2)
        s.begin(); assertEquals(DlnaAction.Send(0, "capacidades leídas"), s.onCapabilitiesRead()); s.onSent(0)
        assertEquals(DlnaAction.None, s.onTransport(TransportState.TRANSITIONING, 1_000))
        s.onTransport(TransportState.PLAYING, 3_000)
        assertEquals(DlnaPhase.PLAYING, s.phase)
        s.onTransport(TransportState.PLAYING, 13_000); assertEquals(10_000L, s.playedMs)
    }
    @Test fun sinPlayingReintientaYLuegoPasaAlSiguienteFormato() {
        val s = SessionSupervisor(2, playTimeoutMs = 10_000, maxRetries = 1)
        s.begin(); s.onCapabilitiesRead(); s.onSent(0)
        val a = s.onTransport(TransportState.STOPPED, 10_000)
        assertTrue(a is DlnaAction.Retry); s.onSent(12_000)
        val b = s.onTransport(TransportState.STOPPED, 22_000)
        assertTrue(b is DlnaAction.Send); assertEquals(1, (b as DlnaAction.Send).index)
    }
    @Test fun agotadosLosIntentosTerminaConMotivo() {
        val s = SessionSupervisor(1, maxRetries = 0)
        s.begin(); s.onCapabilitiesRead(); s.onSent(0)
        val a = s.onCommandFailed("HTTP 500")
        assertTrue(a is DlnaAction.GiveUp); assertTrue((a as DlnaAction.GiveUp).reason.contains("HTTP 500")); assertEquals(DlnaPhase.FAILED, s.phase)
    }
    @Test fun unCorteDuranteLaReproduccionReintenta() {
        val s = SessionSupervisor(1, maxRetries = 2)
        s.begin(); s.onCapabilitiesRead(); s.onSent(0); s.onTransport(TransportState.PLAYING, 1_000)
        val a = s.onTransport(TransportState.STOPPED, 9_000)
        assertTrue(a is DlnaAction.Retry); assertTrue((a as DlnaAction.Retry).reason.contains("se cortó"))
    }
    @Test fun elEsperaCreceEnCadaReintento() {
        val s = SessionSupervisor(1, maxRetries = 2)
        s.begin(); s.onCapabilitiesRead(); s.onSent(0)
        val a = s.onCommandFailed("x") as DlnaAction.Retry; s.onSent(1)
        val b = s.onCommandFailed("x") as DlnaAction.Retry
        assertTrue(b.afterMs > a.afterMs)
    }
}
