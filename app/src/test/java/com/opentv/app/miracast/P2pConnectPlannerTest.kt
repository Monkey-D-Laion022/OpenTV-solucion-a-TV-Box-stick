package com.opentv.app.miracast

import com.opentv.app.miracast.P2pConnectPlanner.Kind
import com.opentv.app.miracast.P2pConnectPlanner.PeerAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class P2pConnectPlannerTest {
    @Test fun conectaCuandoElParApareceDisponible() =
        assertEquals(PeerAction.CONNECT, P2pConnectPlanner().onPeerCheck(true, P2pConnectPlanner.STATUS_AVAILABLE, 1_000).action)

    @Test fun esperaSiAunNoAparece() =
        assertEquals(PeerAction.WAIT, P2pConnectPlanner().onPeerCheck(false, null, 2_000).action)

    @Test fun trasLaEsperaConectaIgualYLoDice() {
        val c = P2pConnectPlanner().onPeerCheck(false, null, P2pConnectPlanner.PEER_WAIT_MS)
        assertEquals(PeerAction.CONNECT, c.action); assertTrue(c.note.contains("no apareció"))
    }
    @Test fun noDisponibleEsperaYLuegoIntenta() {
        val p = P2pConnectPlanner()
        assertEquals(PeerAction.WAIT, p.onPeerCheck(true, P2pConnectPlanner.STATUS_UNAVAILABLE, 1_000).action)
        assertEquals(PeerAction.CONNECT, p.onPeerCheck(true, P2pConnectPlanner.STATUS_UNAVAILABLE, P2pConnectPlanner.PEER_WAIT_MS).action)
    }
    @Test fun ocupadoReintentaTresVecesYLuegoReinicia() {
        val p = P2pConnectPlanner()
        repeat(3) { assertEquals(Kind.WAIT_AND_RETRY, p.onConnectFailure(2).kind) }
        assertEquals(Kind.RESET_AND_RETRY, p.onConnectFailure(2).kind)
    }
    @Test fun error0ReiniciaDosVecesYSeRinde() {
        val p = P2pConnectPlanner()
        assertEquals(Kind.RESET_AND_RETRY, p.onConnectFailure(0).kind)
        assertEquals(Kind.RESET_AND_RETRY, p.onConnectFailure(0).kind)
        assertEquals(Kind.GIVE_UP, p.onConnectFailure(0).kind)
        assertEquals(2, p.resets)
    }
    @Test fun otrosCodigosNoSeReintentan() {
        assertEquals(Kind.GIVE_UP, P2pConnectPlanner().onConnectFailure(1).kind)
        assertEquals(Kind.GIVE_UP, P2pConnectPlanner().onConnectFailure(3).kind)
    }
    @Test fun laExplicacionSeparaMedidoHipotesisYLimite() {
        val t = P2pConnectPlanner.explainFailure(0, 2, true, P2pConnectPlanner.STATUS_AVAILABLE)
        assertTrue(t.contains("SÍ estaba") && t.contains("Hipótesis") && t.contains("WifiP2pWfdInfo") && t.contains("código 0"))
        assertTrue(P2pConnectPlanner.explainFailure(0, 2, false, null).contains("NO apareció"))
    }
}
