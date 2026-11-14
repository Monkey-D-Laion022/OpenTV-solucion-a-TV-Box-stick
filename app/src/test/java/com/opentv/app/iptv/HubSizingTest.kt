package com.opentv.app.iptv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HubSizingTest {
    @Test fun losPerfilesLivianosConservanElMegabyteDeSiempre() {
        for (p in listOf(IptvPreset.SAFE, IptvPreset.BALANCED, IptvPreset.COMPAT_480, IptvPreset.COMPAT_360))
            assertEquals(1024 * 1024, HubSizing.queueBytes(p.bitrateBps))
    }
    @Test fun elPerfilExigenteCubreAlMenos15Segundos() {
        val b = HubSizing.queueBytes(IptvPreset.FULL_HD_60.bitrateBps)
        assertTrue(b * 8.0 / IptvPreset.FULL_HD_60.bitrateBps >= 1.5)
    }
    @Test fun nuncaSuperaElTecho() = assertEquals(HubSizing.MAX_BYTES, HubSizing.queueBytes(100_000_000))
    @Test fun esMonotonoConElBitrate() {
        var prev = 0; for (b in 500_000..30_000_000 step 500_000) { val q = HubSizing.queueBytes(b); assertTrue(q >= prev); prev = q }
    }
}
