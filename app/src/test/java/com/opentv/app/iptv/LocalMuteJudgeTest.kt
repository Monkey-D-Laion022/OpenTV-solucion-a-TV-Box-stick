package com.opentv.app.iptv

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalMuteJudgeTest {
    private fun armed(): LocalMuteJudge = LocalMuteJudge().also { it.arm() }

    @Test fun sinSonidoNoSilencia() {
        val j = armed()
        repeat(10) { assertEquals(LocalMuteJudge.Action.NONE, j.onSecond(-96.0)) }
        assertEquals(LocalMuteJudge.Phase.WAIT_SOUND, j.phase)
    }

    @Test fun silenciaTrasDosSegundosDeSonido() {
        val j = armed()
        assertEquals(LocalMuteJudge.Action.NONE, j.onSecond(-20.0))
        assertEquals(LocalMuteJudge.Action.MUTE, j.onSecond(-18.0))
        assertEquals(LocalMuteJudge.Phase.VERIFYING, j.phase)
    }

    @Test fun sonidoInterrumpidoReiniciaLaEspera() {
        val j = armed()
        j.onSecond(-20.0)
        j.onSecond(-96.0)
        assertEquals(LocalMuteJudge.Action.NONE, j.onSecond(-20.0))
        assertEquals(LocalMuteJudge.Phase.WAIT_SOUND, j.phase)
    }

    @Test fun capturaIntactaConfirma() {
        val j = armed()
        j.onSecond(-20.0); j.onSecond(-20.0)
        assertEquals(LocalMuteJudge.Action.NONE, j.onSecond(-22.0))
        assertEquals(LocalMuteJudge.Phase.CONFIRMED, j.phase)
    }

    @Test fun capturaApagadaRestauraTrasTresSegundos() {
        val j = armed()
        j.onSecond(-20.0); j.onSecond(-20.0)
        assertEquals(LocalMuteJudge.Action.NONE, j.onSecond(-96.0))
        assertEquals(LocalMuteJudge.Action.NONE, j.onSecond(-96.0))
        assertEquals(LocalMuteJudge.Action.RESTORE, j.onSecond(-96.0))
        assertEquals(LocalMuteJudge.Phase.ATTENUATED, j.phase)
    }

    @Test fun caidaGrandeSinSilencioTotalAgotaElTiempo() {
        val j = armed()
        j.onSecond(-10.0); j.onSecond(-10.0)
        var last = LocalMuteJudge.Action.NONE
        repeat(LocalMuteJudge.VERIFY_TIMEOUT_SECONDS) { last = j.onSecond(-50.0) }
        assertEquals(LocalMuteJudge.Action.RESTORE, last)
    }

    @Test fun apagarElModoRestauraSoloSiHabiaSilencio() {
        val j = armed()
        assertEquals(LocalMuteJudge.Action.NONE, j.disarm())
        val k = armed()
        k.onSecond(-20.0); k.onSecond(-20.0)
        assertEquals(LocalMuteJudge.Action.RESTORE, k.disarm())
        assertEquals(LocalMuteJudge.Phase.OFF, k.phase)
    }

    @Test fun confirmadoNoVuelveACambiar() {
        val j = armed()
        j.onSecond(-20.0); j.onSecond(-20.0); j.onSecond(-21.0)
        repeat(20) { assertEquals(LocalMuteJudge.Action.NONE, j.onSecond(-96.0)) }
        assertEquals(LocalMuteJudge.Phase.CONFIRMED, j.phase)
    }

    @Test fun etiquetasNoPrometenLoQueNoSeMide() {
        val confirmed = LocalMuteJudge.Phase.CONFIRMED.label
        assertEquals(true, confirmed.contains("no confirma lo que oye el televisor"))
    }
}
