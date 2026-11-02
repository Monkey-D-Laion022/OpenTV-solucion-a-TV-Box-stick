package com.opentv.app.iptv

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Fmp4AndPerfTest {
    private fun nal(type: Int, vararg body: Int) = byteArrayOf(0, 0, 0, 1, type.toByte()) + ByteArray(body.size) { body[it].toByte() }
    private val sps = nal(0x67, 0x4D, 0x40, 0x1F, 0xAA, 0xBB)
    private val pps = nal(0x68, 0xEE, 0x3C)
    private fun idr() = nal(0x65, 1, 2, 3, 4, 5, 6)
    private fun p() = nal(0x41, 9, 8, 7)
    private fun adts(n: Int) = byteArrayOf(0xFF.toByte(), 0xF1.toByte(), 0x4C, 0x80.toByte(), 0, 0, 0xFC.toByte()) + ByteArray(n) { 5 }

    /** Recorre las cajas de primer nivel: (tipo, tamaño). */
    private fun boxes(data: ByteArray): List<Pair<String, Int>> {
        val out = ArrayList<Pair<String, Int>>()
        var i = 0
        while (i < data.size) {
            val size = ((data[i].toInt() and 0xFF) shl 24) or ((data[i + 1].toInt() and 0xFF) shl 16) or ((data[i + 2].toInt() and 0xFF) shl 8) or (data[i + 3].toInt() and 0xFF)
            assertTrue("tamaño de caja válido", size >= 8 && i + size <= data.size)
            out.add(String(data, i + 4, 4, Charsets.ISO_8859_1) to size)
            i += size
        }
        assertEquals(data.size, i)
        return out
    }

    @Test fun annexBSeparaYConvierte() {
        val au = sps + pps + idr()
        val nals = AnnexB.split(au)
        assertEquals(listOf(0x67, 0x68, 0x65), nals.map { it[0].toInt() and 0xFF })
        val avcc = AnnexB.toAvcc(au) // SPS/PPS no se repiten en la muestra
        assertEquals(4 + 7, avcc.size)
        assertEquals(7, avcc[3].toInt())
        assertEquals(0x65, avcc[4].toInt())
    }
    @Test fun annexBConCodigosDeTresBytes() {
        val au = byteArrayOf(0, 0, 1, 0x41, 1, 2, 0, 0, 1, 0x41, 3)
        assertEquals(2, AnnexB.split(au).size)
    }
    @Test fun nadaSalesinConfigNiFotogramaClave() {
        val inits = ArrayList<ByteArray>(); val frags = ArrayList<ByteArray>()
        val m = Fmp4Muxer(640, 360, false, { inits.add(it) }, { frags.add(it) })
        m.addVideo(idr(), 0, true) // sin SPS/PPS todavía
        m.setConfig(sps + pps)
        m.addVideo(p(), 0, false) // no es clave: no se puede empezar
        m.flush()
        assertTrue(inits.isEmpty() && frags.isEmpty())
    }
    @Test fun estructuraDeCajas() {
        val inits = ArrayList<ByteArray>(); val frags = ArrayList<ByteArray>()
        val m = Fmp4Muxer(640, 360, true, { inits.add(it) }, { frags.add(it) })
        m.setConfig(sps + pps)
        var t = 0L
        for (g in 0 until 3) {
            m.addVideo(idr(), t, true); t += 33_333
            m.addAudio(adts(20), t)
            for (k in 0 until 4) { m.addVideo(p(), t, false); t += 33_333 }
            m.addAudio(adts(20), t)
        }
        m.flush()
        assertEquals(1, inits.size)
        assertEquals(listOf("ftyp", "moov"), boxes(inits[0]).map { it.first })
        assertEquals(3, frags.size)
        for (f in frags) assertEquals(listOf("moof", "mdat"), boxes(f).map { it.first })
        // la lista de muestras (trun) debe coincidir con el contenido de mdat: video 5 muestras
        val first = frags[0]
        val moofSize = boxes(first)[0].second
        val mdatPayload = boxes(first)[1].second - 8
        assertTrue(mdatPayload > 0 && moofSize > 100)
    }
    @Test fun elFragmentoSeCierraAlLlegarOtraClave() {
        val frags = ArrayList<ByteArray>()
        val m = Fmp4Muxer(320, 180, false, { }, { frags.add(it) })
        m.setConfig(sps + pps)
        m.addVideo(idr(), 0, true); m.addVideo(p(), 33_333, false)
        assertEquals(0, frags.size)
        m.addVideo(idr(), 66_666, true)
        assertEquals(1, frags.size)
    }
    @Test fun sinAudioNoHayPistaDeAudio() {
        val inits = ArrayList<ByteArray>()
        val m = Fmp4Muxer(320, 180, false, { inits.add(it) }, { })
        m.setConfig(sps + pps); m.addVideo(idr(), 0, true)
        val text = String(inits[0], Charsets.ISO_8859_1)
        assertTrue(text.contains("avc1") && text.contains("avcC")); assertFalse(text.contains("mp4a"))
    }
    @Test fun conAudioHayMp4aYEsds() {
        val inits = ArrayList<ByteArray>()
        val m = Fmp4Muxer(320, 180, true, { inits.add(it) }, { })
        m.setConfig(sps + pps); m.addVideo(idr(), 0, true)
        val text = String(inits[0], Charsets.ISO_8859_1)
        assertTrue(text.contains("mp4a") && text.contains("esds") && text.contains("trex"))
    }
    @Test fun mp4FeedGuardaInicioYReparteFragmentos() {
        val feed = Mp4Feed()
        assertNull(feed.init)
        feed.onInit(byteArrayOf(1, 2, 3))
        val sub = feed.hub.subscribe()
        feed.onFragment(byteArrayOf(9, 9))
        assertEquals(2, sub.poll(100)!!.size)
        assertEquals(3, feed.init!!.size)
    }

    private fun r(p: IptvPreset, enc: Double) = PerfResult(p, 5.0, enc, p.fps.toDouble())
    @Test fun asesorPrefiere1080p60LuegoLos720p60() {
        assertEquals(IptvPreset.FULL_HD_60, PerfAdvisor.best(listOf(r(IptvPreset.FULL_HD_60, 58.0), r(IptvPreset.HD_60, 60.0))))
        assertEquals(IptvPreset.HD_60, PerfAdvisor.best(listOf(r(IptvPreset.FULL_HD_60, 41.0), r(IptvPreset.HD_60, 59.0))))
        assertEquals(IptvPreset.FULL_HD, PerfAdvisor.best(listOf(r(IptvPreset.FULL_HD_60, 20.0), r(IptvPreset.HD_60, 33.0), r(IptvPreset.FULL_HD, 30.0))))
    }
    @Test fun asesorSinMedicionesNoInventa() = assertNull(PerfAdvisor.best(emptyList()))
    @Test fun sostenidoExige90PorCiento() {
        assertTrue(r(IptvPreset.HD_60, 54.0).sustained); assertFalse(r(IptvPreset.HD_60, 53.0).sustained)
        assertTrue(PerfAdvisor.describe(r(IptvPreset.HD_60, 30.0)).contains("NO lo sostiene"))
    }
}
