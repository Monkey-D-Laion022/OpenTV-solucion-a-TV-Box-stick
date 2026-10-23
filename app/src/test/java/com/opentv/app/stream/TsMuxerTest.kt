package com.opentv.app.stream

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TsMuxerTest {

    private val sps = byteArrayOf(0, 0, 0, 1, 0x67, 0x42, 0x00, 0x1F)
    private val aud = byteArrayOf(0, 0, 0, 1, 0x09, 0xF0.toByte())

    private fun packets(ts: ByteArray): List<ByteArray> {
        assertEquals("tamaño múltiplo de 188", 0, ts.size % 188)
        return ts.toList().chunked(188).map { it.toByteArray() }
    }

    private fun pid(p: ByteArray) = ((p[1].toInt() and 0x1F) shl 8) or (p[2].toInt() and 0xFF)
    private fun pusi(p: ByteArray) = (p[1].toInt() and 0x40) != 0
    private fun cc(p: ByteArray) = p[3].toInt() and 0xF

    private fun payload(p: ByteArray): ByteArray {
        val afc = (p[3].toInt() shr 4) and 0x3
        var start = 4
        if (afc and 0x2 != 0) start += 1 + (p[4].toInt() and 0xFF)
        return if (afc and 0x1 != 0) p.copyOfRange(start, 188) else ByteArray(0)
    }

    /** Reensambla el PES del video y devuelve (cabecera PES, datos). */
    private fun videoEs(ts: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        packets(ts).filter { pid(it) == TsMuxer.PID_VIDEO }.forEach { out.write(payload(it)) }
        val pes = out.toByteArray()
        assertEquals(listOf<Byte>(0, 0, 1, 0xE0.toByte()), pes.take(4))
        return pes.copyOfRange(9 + (pes[8].toInt() and 0xFF), pes.size)
    }

    @Test
    fun everyPacketHasSyncByte() {
        val muxer = TsMuxer().apply { setCodecConfig(sps) }
        val ts = muxer.mux(ByteArray(5000) { 7 }, 0, true) + muxer.mux(ByteArray(10) { 1 }, 33_000, false)
        packets(ts).forEach { assertEquals(0x47, it[0].toInt()) }
    }

    @Test
    fun payloadRoundTripsForManySizes() {
        for (size in listOf(1, 2, 100, 175, 176, 177, 183, 184, 185, 360, 400, 4096, 20_000)) {
            val frame = ByteArray(size) { (it * 31).toByte() }
            val muxer = TsMuxer().apply { setCodecConfig(sps) }
            val key = muxer.mux(frame, 0, true)
            assertArrayEquals("clave $size", aud + sps + frame, videoEs(key))
            val delta = muxer.mux(frame, 40_000, false)
            assertArrayEquals("delta $size", aud + frame, videoEs(delta))
        }
    }

    @Test
    fun keyFramesAreAnnouncedWithPatAndPmtThatPassCrc() {
        val muxer = TsMuxer().apply { setCodecConfig(sps) }
        val key = packets(muxer.mux(ByteArray(300), 0, true))
        assertEquals(TsMuxer.PID_PAT, pid(key[0]))
        assertEquals(TsMuxer.PID_PMT, pid(key[1]))
        assertEquals(TsMuxer.PID_VIDEO, pid(key[2]))
        assertTrue(pusi(key[0]) && pusi(key[1]) && pusi(key[2]))
        listOf(key[0], key[1]).forEach { p ->
            val sectionLength = ((p[6].toInt() and 0x0F) shl 8) or (p[7].toInt() and 0xFF)
            val section = p.copyOfRange(5, 5 + 3 + sectionLength)
            assertEquals("CRC de la tabla", 0, TsMuxer.crc32Mpeg(section))
        }
        val deltaFirst = packets(muxer.mux(ByteArray(300), 40_000, false))[0]
        assertEquals("un fotograma normal no repite PAT", TsMuxer.PID_VIDEO, pid(deltaFirst))
    }

    @Test
    fun continuityCountersCountPerPid() {
        val muxer = TsMuxer().apply { setCodecConfig(sps) }
        val all = muxer.mux(ByteArray(3000), 0, true) +
            muxer.mux(ByteArray(3000), 40_000, false) +
            muxer.mux(ByteArray(3000), 80_000, false)
        val video = packets(all).filter { pid(it) == TsMuxer.PID_VIDEO }
        video.forEachIndexed { i, p -> assertEquals(i % 16, cc(p)) }
        assertTrue(video.size > 16)
    }

    @Test
    fun firstPacketOfEachFrameCarriesPcrAndKeyFrameMarksRandomAccess() {
        val muxer = TsMuxer().apply { setCodecConfig(sps) }
        val key = packets(muxer.mux(ByteArray(500), 1_000_000, true)).first { pid(it) == TsMuxer.PID_VIDEO }
        assertTrue((key[3].toInt() and 0x20) != 0)
        assertEquals(0x50, key[5].toInt() and 0x50) // acceso aleatorio + PCR
        val delta = packets(muxer.mux(ByteArray(500), 1_040_000, false)).first()
        assertEquals(0x10, delta[5].toInt() and 0x50)
    }

    @Test
    fun ptsIsEncodedWithOffset() {
        val muxer = TsMuxer().apply { setCodecConfig(sps) }
        val ts = muxer.mux(ByteArray(10), 2_000_000, false) // 2 s -> 180000 + 63000 de desfase
        val p = packets(ts).first { pid(it) == TsMuxer.PID_VIDEO }
        val pes = payload(p)
        val pts = ((pes[9].toLong() and 0x0E) shl 29) or
            ((pes[10].toLong() and 0xFF) shl 22) or
            ((pes[11].toLong() and 0xFE) shl 14) or
            ((pes[12].toLong() and 0xFF) shl 7) or
            ((pes[13].toLong() and 0xFE) shr 1)
        assertEquals(243_000L, pts)
    }
}
