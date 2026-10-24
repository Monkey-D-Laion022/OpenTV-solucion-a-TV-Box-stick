package com.opentv.app.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamLatencyTest {
    @Test
    fun lowModeShrinksTheTimingMargins() {
        val s = StreamLatency.STABLE
        val m = StreamLatency.SHORT_MARGIN
        assertTrue(m.ptsOffset90 < s.ptsOffset90)
        assertTrue(m.pcrLead90 < s.pcrLead90)
        StreamLatency.values().forEach { assertTrue(it.label, it.ptsOffset90 > it.pcrLead90) } // el PCR siempre va antes del PTS
        assertEquals(StreamLatency.STABLE, s.next().next().next())
    }

    @Test
    fun eachVariantChangesOnlyItsOwnThing() {
        val s = StreamLatency.STABLE
        val q = StreamLatency.SHORT_QUEUE
        assertEquals(s.fps, q.fps)
        assertEquals(s.ptsOffset90, q.ptsOffset90)
        assertEquals(0L, q.minRateBps)
        assertTrue(q.sendBufferBytes < s.sendBufferBytes && q.hubQueueBytes < s.hubQueueBytes)
        val m = StreamLatency.SHORT_MARGIN
        assertEquals(s.fps, m.fps)
        assertEquals(s.sendBufferBytes, m.sendBufferBytes)
        assertEquals(s.minRateBps, m.minRateBps)
    }

    @Test
    fun muxerHonoursTheConfiguredOffsets() {
        fun pts(ts: ByteArray): Long {
            // Primer paquete de video tras PAT y PMT: busca el PES con PTS.
            val p = ts.copyOfRange(188 * 2, 188 * 3)
            val af = (p[3].toInt() shr 4) and 3
            var o = 4
            if (af and 2 != 0) o += 1 + (p[4].toInt() and 0xFF)
            val h = p.copyOfRange(o, o + 14)
            return (((h[9].toLong() shr 1) and 7) shl 30) or ((h[10].toLong() and 0xFF) shl 22) or
                (((h[11].toLong() and 0xFF) shr 1) shl 15) or ((h[12].toLong() and 0xFF) shl 7) or ((h[13].toLong() and 0xFF) shr 1)
        }
        val a = TsMuxer(false, 63_000L, 45_000L).apply { setCodecConfig(byteArrayOf(0, 0, 0, 1, 0x67)) }
        val b = TsMuxer(false, 18_000L, 9_000L).apply { setCodecConfig(byteArrayOf(0, 0, 0, 1, 0x67)) }
        assertEquals(63_000L, pts(a.mux(ByteArray(10), 0, true)))
        assertEquals(18_000L, pts(b.mux(ByteArray(10), 0, true)))
    }
}
