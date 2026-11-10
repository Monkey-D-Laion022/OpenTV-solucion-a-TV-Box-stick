package com.opentv.app.proto

import org.junit.Assert.assertEquals
import org.junit.Test

class OtAcceptCapsTest {
    @Test fun receiverAskingLessIsHonored() {
        val c = OtAcceptCaps.resolve(12_000_000, 60, OtAccept(true, 8_000_000, 30, 250))
        assertEquals(8_000_000L, c.bitrateBps); assertEquals(30, c.fps)
    }
    @Test fun receiverAskingSameOrMoreMeansNoCap() {
        val c = OtAcceptCaps.resolve(12_000_000, 60, OtAccept(true, 12_000_000, 60, 250))
        assertEquals(12_000_000L, c.bitrateBps); assertEquals(0, c.fps)
        val d = OtAcceptCaps.resolve(12_000_000, 30, OtAccept(true, 50_000_000, 60, 250))
        assertEquals(12_000_000L, d.bitrateBps); assertEquals(0, d.fps)
    }
    @Test fun absurdLowValuesAreIgnored() {
        val c = OtAcceptCaps.resolve(12_000_000, 60, OtAccept(true, 1_000, 0, 250))
        assertEquals(12_000_000L, c.bitrateBps); assertEquals(0, c.fps)
    }
}
