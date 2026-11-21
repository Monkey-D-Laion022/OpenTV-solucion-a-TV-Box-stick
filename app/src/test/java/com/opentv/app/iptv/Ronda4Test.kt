package com.opentv.app.iptv

import com.opentv.app.media.dlna.DlnaAction
import com.opentv.app.media.dlna.ProfileSelector
import com.opentv.app.media.dlna.SessionSupervisor
import com.opentv.app.media.dlna.key
import com.opentv.app.miracast.WfdFormatChooser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cambios de la ronda 5: datos reales del LG 42LB5800-SB (M3 de la prueba física) y comportamiento verificable sin hardware. */
class Ronda4Test {
    private val lgM3 = mapOf(
        "wfd_video_formats" to "40 00 01 08 00019CFF 3FFFFFFF 000005FF 00 0000 0000 10 none none, 02 08 00019CFF 3FFFFFFF 000005FF 00 0000 0000 10 none none",
        "wfd_audio_codecs" to "LPCM 00000003 00, AAC 00000007 00, AC3 00000007 00",
        "wfd_client_rtp_ports" to "RTP/AVP/UDP;unicast 19000 0 mode=play",
    )

    @Test fun conElM3RealDelLgPorDefectoSigueSiendo1080p30() {
        val (c, _) = WfdFormatChooser.choose(lgM3)
        assertEquals("1920x1080p30", c!!.mode.label)
    }
    @Test fun conPrioridadDeFluidezElLgNegocia720p60PorqueNoDeclara1080p60() {
        val (c, _) = WfdFormatChooser.choose(lgM3, preferFluidity = true)
        assertNotNull(c); assertEquals("1280x720p60", c!!.mode.label); assertEquals(IptvPreset.HD_60, c.preset)
    }
    @Test fun laFluidezNuncaElijeUnModoNoDeclarado() {
        val sin60 = lgM3 + ("wfd_video_formats" to "00 00 01 04 00000080 00000000 00000000 00 0000 0000 00 none none")   // solo 1080p30
        val (c, _) = WfdFormatChooser.choose(sin60, preferFluidity = true)
        assertEquals("1920x1080p30", c!!.mode.label)
    }

    @Test fun sinListaDelTvSeIntentanDosMimeDeHlsAntesDelTsGenerico() {
        val sel = ProfileSelector.select(null)
        val hls = sel.attempts.filter { it.hls }
        assertEquals(2, hls.size); assertTrue(hls.all { it.fps == 60 })
        assertTrue(sel.attempts.indexOfFirst { !it.hls } > 1)
    }
    @Test fun elFormatoRecordadoPasaAlPrincipioSinPerderLosDemas() {
        val sel = ProfileSelector.select(null)
        val ts = sel.attempts.first { !it.hls && it.height == 1080 }
        val re = ProfileSelector.preferRemembered(sel, ts.key)
        assertEquals(ts.key, re.attempts.first().key); assertEquals(sel.attempts.size, re.attempts.size)
        assertEquals(sel, ProfileSelector.preferRemembered(sel, "no-existe"))
        assertEquals(sel, ProfileSelector.preferRemembered(sel, null))
    }
    @Test fun unIntentoEspeculativoSinReintentosPasaAlSiguienteEnSeguida() {
        val sup = SessionSupervisor(3, retriesFor = { i -> if (i == 0) 0 else 2 })
        sup.begin(); sup.onCapabilitiesRead(); sup.onSent(0)
        val a = sup.onCommandFailed("sin respuesta", 1_000)
        assertTrue(a is DlnaAction.Send); assertEquals(1, (a as DlnaAction.Send).index)
    }
    @Test fun elComportamientoPorDefectoDelSupervisorNoCambia() {
        val sup = SessionSupervisor(2)
        sup.begin(); sup.onCapabilitiesRead(); sup.onSent(0)
        assertTrue(sup.onCommandFailed("x", 1_000) is DlnaAction.Retry)
    }

    @Test fun ticsAtrasadosSostenidosBajanLosFpsAunqueElPromedioParezcaBien() {
        val a = QualityAdapter(IptvPreset.FULL_HD_60, maxHeight = 1080)
        val s = QualityAdapter.Sample(60.0, 60.0, 8000, 8000, 1, false, lateTicks = 12)
        val acts = (1..40).map { a.onSecond(s) }
        assertTrue(acts.any { it.kind == QualityAdapter.Kind.FPS && it.fps < 60 })
    }
    @Test fun ticsAtrasadosAisladosNoCambianNada() {
        val a = QualityAdapter(IptvPreset.FULL_HD_60, maxHeight = 1080)
        val acts = (1..60).map { i -> a.onSecond(QualityAdapter.Sample(60.0, 60.0, 8000, 8000, 1, false, lateTicks = if (i % 10 == 0) 12 else 0)) }
        assertTrue(acts.none { it.changed && it.kind != QualityAdapter.Kind.NONE })
    }

    @Test fun avisoDeAhorroDeEnergiaSoloSiEstaActivo() {
        assertNull(PowerSaveJudge.warning(false, 60, null))
        assertTrue(PowerSaveJudge.warning(true, 60, "ninguno")!!.contains("60 fps"))
        assertTrue(!PowerSaveJudge.warning(true, 30, null)!!.contains("60 fps"))
    }

    @Test fun sinListaDelTvElPrimerIntentoEsTsConElPerfilDelUsuario() {
        val u = ProfileSelector.UserProfile(1280, 720, 60, "HD_60", "720p60")
        val sel = ProfileSelector.select(null, u)
        val first = sel.attempts.first()
        assertTrue(!first.hls); assertEquals(720, first.height); assertEquals(60, first.fps); assertEquals("HD_60", first.presetName)
        assertTrue(sel.attempts.any { it.hls })
    }
    @Test fun sinPerfilDeUsuarioElComportamientoAnteriorNoCambia() {
        assertEquals(ProfileSelector.select(null).attempts, ProfileSelector.select(null, null).attempts)
    }
    @Test fun losFormatosRechazadosSeOmitenPeroLaListaNuncaQuedaVacia() {
        val sel = ProfileSelector.select(null)
        val hlsKeys = sel.attempts.filter { it.hls }.map { it.key }.toSet()
        val dropped = ProfileSelector.dropRejected(sel, hlsKeys)
        assertTrue(dropped.attempts.none { it.hls }); assertTrue(dropped.attempts.isNotEmpty())
        val all = ProfileSelector.dropRejected(sel, sel.attempts.map { it.key }.toSet())
        assertEquals(sel.attempts, all.attempts)
    }
    @Test fun elAhorroSeDetectaPorAndroidOPorLosInterruptoresDeXiaomi() {
        assertTrue(PowerSaveJudge.active(true, false, false))
        assertTrue(PowerSaveJudge.active(false, true, false))
        assertTrue(PowerSaveJudge.active(false, false, true))
        assertTrue(!PowerSaveJudge.active(false, false, false))
    }
}
