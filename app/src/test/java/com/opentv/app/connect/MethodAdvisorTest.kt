package com.opentv.app.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MethodAdvisorTest {
    private fun o(k: LinkKind, id: String = k.name) = DeviceOffer(k, id)

    @Test fun elMismoTvPorVariosProtocolosEsUnSoloDispositivo() {
        val m = MethodAdvisor.merge(listOf("[LG] webOS TV" to o(LinkKind.CAST), "LG WebOS TV" to o(LinkKind.DLNA), "Sala" to o(LinkKind.MIRACAST)))
        assertEquals(2, m.size)
        assertEquals(setOf(LinkKind.CAST, LinkKind.DLNA), m.first { it.name.contains("webOS", true) }.kinds)
    }
    @Test fun sinTildesNiMayusculas() = assertEquals(MethodAdvisor.normalize("Televisión  Sála"), MethodAdvisor.normalize("television sala"))
    @Test fun prefiereEspejoNativoSobreEnlaces() {
        val d = UnifiedDevice("TV", listOf(o(LinkKind.DLNA), o(LinkKind.MIRACAST), o(LinkKind.CAST)))
        assertEquals(LinkKind.MIRACAST, MethodAdvisor.best(d))
    }
    @Test fun unMetodoFallidoPasaAlFinal() {
        val d = UnifiedDevice("TV", listOf(o(LinkKind.MIRACAST), o(LinkKind.DLNA), o(LinkKind.CAST)))
        assertEquals(listOf(LinkKind.CAST, LinkKind.DLNA, LinkKind.MIRACAST), MethodAdvisor.ranked(d, setOf(LinkKind.MIRACAST)))
    }
    @Test fun ConSoloDlnaYNavegadorGanaDlna() = assertEquals(LinkKind.DLNA, MethodAdvisor.best(UnifiedDevice("LG", listOf(o(LinkKind.BROWSER), o(LinkKind.DLNA)))))
    @Test fun opentvLinkTieneLaMayorPrioridad() = assertEquals(LinkKind.OPENTV_LINK, MethodAdvisor.best(UnifiedDevice("PS3", listOf(o(LinkKind.DLNA), o(LinkKind.OPENTV_LINK)))))
    @Test fun sinOfertasNoHayMetodo() = assertNull(MethodAdvisor.best(UnifiedDevice("x", emptyList())))
    @Test fun siFallaElEspejoPeroHayOtroEspejoSeUsaEse() {
        val d = UnifiedDevice("TV", listOf(o(LinkKind.MIRACAST), o(LinkKind.OPENTV_LINK), o(LinkKind.DLNA)))
        assertTrue(MethodAdvisor.ranked(d, setOf(LinkKind.OPENTV_LINK)).first() == LinkKind.MIRACAST)
    }
    @Test fun duplicadosDelMismoMetodoSeUnen() {
        val m = MethodAdvisor.merge(listOf("TV" to o(LinkKind.DLNA, "a"), "tv" to o(LinkKind.DLNA, "a")))
        assertEquals(1, m.single().offers.size)
    }

    @Test fun elPuenteUneCastYDlnaDelMismoTv() {
        val ep = LanEndpoint("192.168.1.20", 8080, "/ctl")
        val u = UpnpDevice("uuid:1", "[LG] webOS TV", "LG", "OLED", "http://x/d.xml", "urn:schemas-upnp-org:service:AVTransport:1", ep)
        val t = TvTargets.build(listOf(CastDevice("c1", "LG webOS TV")), listOf(u))
        val d = MethodAdvisor.fromTargets(t)
        assertEquals(1, d.size); assertEquals(setOf(LinkKind.CAST, LinkKind.DLNA), d.single().kinds)
        assertEquals(LinkKind.CAST, MethodAdvisor.best(d.single()))
    }
}
