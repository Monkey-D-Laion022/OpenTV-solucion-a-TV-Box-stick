package com.opentv.app.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TsPaddingTest {
    @Test
    fun nullPacketsAreValidTs() {
        val ts = TsPadding.nullPackets(3)
        assertEquals(188 * 3, ts.size)
        for (i in 0 until 3) {
            val o = i * 188
            assertEquals(0x47, ts[o].toInt())
            assertEquals(0x1FFF, ((ts[o + 1].toInt() and 0x1F) shl 8) or (ts[o + 2].toInt() and 0xFF))
            assertEquals(0x10, ts[o + 3].toInt() and 0xFF)
        }
    }

    @Test
    fun fillsOnlyTheMissingRate() {
        // 2 Mbps = 250 000 B/s: tras 1 s sin emitir nada faltan 250000/188 = 1329, pero se limita por tanda.
        assertEquals(200, TsPadding.packetsNeeded(2_000_000, 1000, 0))
        assertEquals(1329, TsPadding.packetsNeeded(2_000_000, 1000, 0, maxPackets = 5000))
        assertEquals(0, TsPadding.packetsNeeded(2_000_000, 1000, 250_000))
        assertEquals(0, TsPadding.packetsNeeded(2_000_000, 1000, 300_000))
        assertTrue(TsPadding.packetsNeeded(2_000_000, 20, 0) in 1..30)
    }

    @Test
    fun hubCountsEveryPublishedByte() {
        val hub = StreamHub()
        hub.publish(ByteArray(188), true)
        hub.publish(TsPadding.nullPackets(2), false)
        assertEquals(188L * 3, hub.publishedBytes.get())
    }
}
