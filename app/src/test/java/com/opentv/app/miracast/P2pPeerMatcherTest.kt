package com.opentv.app.miracast

import com.opentv.app.miracast.P2pPeerMatcher.Candidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class P2pPeerMatcherTest {
    private val tv = Candidate("AA:BB:CC:00:11:22", "[TV] Sala", 3)

    @Test fun resuelvePorDireccionSinDistinguirMayusculas() {
        val m = P2pPeerMatcher.resolve(listOf(tv), "aa:bb:cc:00:11:22", "otro")
        assertEquals(tv, m.candidate); assertFalse(m.addressChanged)
    }
    @Test fun siCambioLaDireccionResuelvePorNombreUnico() {
        val m = P2pPeerMatcher.resolve(listOf(tv), "11:11:11:11:11:11", "[tv] sala ")
        assertEquals(tv, m.candidate); assertTrue(m.addressChanged)
    }
    @Test fun nombreRepetidoNoSeAdivina() {
        val m = P2pPeerMatcher.resolve(listOf(tv, tv.copy(address = "DD:00:00:00:00:01")), "11:11:11:11:11:11", "[TV] Sala")
        assertNull(m.candidate)
    }
    @Test fun listaVaciaONombreVacioNoResuelven() {
        assertNull(P2pPeerMatcher.resolve(emptyList(), "x", "[TV] Sala").candidate)
        assertNull(P2pPeerMatcher.resolve(listOf(tv), "x", "  ").candidate)
    }
}
