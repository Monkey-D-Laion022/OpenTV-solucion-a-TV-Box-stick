package com.opentv.app.iptv

import java.io.ByteArrayOutputStream

/**
 * MP4 fragmentado (fMP4) para reproducción progresiva en vivo: un segmento de inicio (`ftyp`+`moov`) y luego un
 * fragmento (`moof`+`mdat`) por grupo de imágenes, cada uno empezando en un fotograma clave. Es el formato que muchos
 * navegadores y televisores aceptan en `<video src>` aunque no soporten HLS ni MPEG‑TS; NO se asume para ninguno:
 * cada equipo hay que probarlo. Video H.264 (Annex B de MediaCodec → AVCC) y audio AAC‑LC 48 kHz estéreo (ADTS → crudo).
 * Lógica pura: no toca Android. Los tiempos van en µs desde el inicio de la emisión. No es seguro entre hilos: usarlo
 * desde uno solo (o sincronizar por fuera).
 */
class Fmp4Muxer(
    private val width: Int,
    private val height: Int,
    private val withAudio: Boolean,
    private val onInit: (ByteArray) -> Unit,
    private val onFragment: (ByteArray) -> Unit,
) {
    private class Sample(val data: ByteArray, val timeUs: Long, val key: Boolean)

    private var sps: ByteArray? = null
    private var pps: ByteArray? = null
    private var initSent = false
    private val video = ArrayList<Sample>()
    private val audio = ArrayList<ByteArray>()
    private var audioBaseTicks = -1L
    private var audioNextTicks = 0L
    private var audioFragStartTicks = 0L
    private var sequence = 1
    private var lastVideoTimeUs = -1L
    private var videoBaseUs = -1L
    private var prevFragmentEndTicks = 0L
    private var avgFrameTicks = 3_000L

    /** Annex B con SPS y PPS (lo que entrega el codificador como «configuración»). */
    fun setConfig(annexB: ByteArray) {
        for (nal in AnnexB.split(annexB)) {
            when (nal[0].toInt() and 0x1F) {
                7 -> sps = nal
                8 -> pps = nal
            }
        }
    }

    /** Un fotograma en Annex B. Al llegar un fotograma clave se cierra y publica el fragmento anterior. */
    fun addVideo(annexB: ByteArray, timeUs: Long, key: Boolean) {
        if (sps == null || pps == null) return
        if (!initSent) {
            if (!key) return // no se puede empezar a decodificar antes del primer fotograma clave
            onInit(initSegment())
            initSent = true
            videoBaseUs = timeUs
        }
        if (key && video.isNotEmpty()) flush()
        video.add(Sample(AnnexB.toAvcc(annexB), timeUs, key))
        lastVideoTimeUs = timeUs
    }

    /** Un cuadro AAC con cabecera ADTS de 7 bytes. [timeUs] solo se usa para el primero (alinear con el video). */
    fun addAudio(adts: ByteArray, timeUs: Long) {
        if (!withAudio || !initSent || adts.size <= 7) return
        if (audioBaseTicks < 0) {
            audioBaseTicks = ((timeUs - videoBaseUs).coerceAtLeast(0) * AUDIO_RATE) / 1_000_000
            audioNextTicks = audioBaseTicks
            audioFragStartTicks = audioBaseTicks
        }
        audio.add(adts.copyOfRange(if ((adts[1].toInt() and 1) == 0) 9 else 7, adts.size)) // con CRC la cabecera mide 9
    }

    /** Publica lo acumulado aunque no haya llegado otro fotograma clave (p. ej. al cerrar la emisión). */
    fun flush() {
        if (video.isEmpty()) return
        val firstTicks = (video[0].timeUs - videoBaseUs) * VIDEO_RATE / 1_000_000
        val durations = LongArray(video.size)
        for (i in video.indices) {
            val next = if (i + 1 < video.size) video[i + 1].timeUs else video[i].timeUs + (avgFrameTicks * 1_000_000 / VIDEO_RATE)
            durations[i] = ((next - video[i].timeUs) * VIDEO_RATE / 1_000_000).coerceAtLeast(1)
        }
        if (video.size > 1) avgFrameTicks = (durations.sum() / durations.size).coerceAtLeast(1)
        val baseTicks = if (sequence == 1) 0L else maxOf(firstTicks, prevFragmentEndTicks)
        val videoTotal = durations.sum()
        prevFragmentEndTicks = baseTicks + videoTotal
        val out = ByteArrayOutputStream()
        out.write(fragment(baseTicks, durations))
        onFragment(out.toByteArray())
        video.clear()
        audio.clear()
        audioFragStartTicks = audioNextTicks
        sequence++
    }

    private fun fragment(baseTicks: Long, durations: LongArray): ByteArray {
        val vSizes = video.map { it.data.size }
        val aSizes = audio.map { it.size }
        val hasAudio = withAudio && audio.isNotEmpty()

        fun trun(count: Int, sizes: List<Int>, dur: (Int) -> Long, flags: (Int) -> Int, dataOffset: Int): ByteArray {
            val b = Box("trun", 0, 0x000701)
            b.u32(count.toLong()); b.u32(dataOffset.toLong())
            for (i in 0 until count) { b.u32(dur(i)); b.u32(sizes[i].toLong()); b.u32(flags(i).toLong()) }
            return b.build()
        }
        fun traf(trackId: Int, baseDecode: Long, trun: ByteArray): ByteArray {
            val tfhd = Box("tfhd", 0, 0x020000).also { it.u32(trackId.toLong()) }.build() // default-base-is-moof
            val tfdt = Box("tfdt", 1, 0).also { it.u64(baseDecode) }.build()
            return box("traf", tfhd + tfdt + trun)
        }
        val mfhd = Box("mfhd", 0, 0).also { it.u32(sequence.toLong()) }.build()
        val vFlags = { i: Int -> if (video[i].key) 0x02000000 else 0x01010000 } // sync / no-sync + depends-on-others
        val aFlags = { _: Int -> 0x02000000 }

        // Dos pasadas: primero se mide el moof con desplazamientos provisorios y después se fijan.
        fun build(vOff: Int, aOff: Int): ByteArray {
            val vTraf = traf(1, baseTicks, trun(video.size, vSizes, { durations[it] }, vFlags, vOff))
            val aTraf = if (hasAudio) traf(2, audioFragStartTicks, trun(audio.size, aSizes, { AAC_FRAME }, aFlags, aOff)) else ByteArray(0)
            return box("moof", mfhd + vTraf + aTraf)
        }
        val moofSize = build(0, 0).size
        val vBytes = vSizes.sum()
        val moof = build(moofSize + 8, moofSize + 8 + vBytes)
        val mdat = ByteArrayOutputStream()
        for (s in video) mdat.write(s.data)
        if (hasAudio) for (a in audio) mdat.write(a)
        val payload = mdat.toByteArray()
        audioNextTicks = audioFragStartTicks + audio.size * AAC_FRAME
        return moof + box("mdat", payload)
    }

    private fun initSegment(): ByteArray {
        val ftyp = box("ftyp", "isom".toByteArray() + u32(512) + "isomiso5avc1mp41".toByteArray())
        val mvhd = Box("mvhd", 0, 0).also {
            it.u32(0); it.u32(0); it.u32(1000); it.u32(0)
            it.u32(0x00010000); it.u16(0x0100); it.u16(0); it.u32(0); it.u32(0)
            it.matrix(); repeat(6) { _ -> it.u32(0) }; it.u32(if (withAudio) 3 else 2)
        }.build()
        val traks = trakVideo() + if (withAudio) trakAudio() else ByteArray(0)
        val trex = { id: Int -> Box("trex", 0, 0).also { it.u32(id.toLong()); it.u32(1); it.u32(0); it.u32(0); it.u32(0) }.build() }
        val mvex = box("mvex", trex(1) + if (withAudio) trex(2) else ByteArray(0))
        return ftyp + box("moov", mvhd + traks + mvex)
    }

    private fun trakVideo(): ByteArray {
        val tkhd = trackHeader(1, width, height, 0)
        val mdhd = mdhd(VIDEO_RATE)
        val hdlr = hdlr("vide", "OpenTV video")
        val spsB = sps!!
        val ppsB = pps!!
        val avcC = Box("avcC", -1, 0).also {
            it.u8(1); it.u8(spsB[1].toInt() and 0xFF); it.u8(spsB[2].toInt() and 0xFF); it.u8(spsB[3].toInt() and 0xFF)
            it.u8(0xFF); it.u8(0xE1); it.u16(spsB.size); it.bytes(spsB); it.u8(1); it.u16(ppsB.size); it.bytes(ppsB)
        }.build()
        val avc1 = Box("avc1", -1, 0).also {
            repeat(6) { _ -> it.u8(0) }; it.u16(1); it.u16(0); it.u16(0); repeat(3) { _ -> it.u32(0) }
            it.u16(width); it.u16(height); it.u32(0x00480000); it.u32(0x00480000); it.u32(0); it.u16(1)
            it.bytes(ByteArray(32)); it.u16(0x0018); it.u16(0xFFFF); it.bytes(avcC)
        }.build()
        val stsd = Box("stsd", 0, 0).also { it.u32(1); it.bytes(avc1) }.build()
        return box("trak", tkhd + box("mdia", mdhd + hdlr + box("minf", box("vmhd", byteArrayOf(0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0)) + dinf() + stbl(stsd))))
    }

    private fun trakAudio(): ByteArray {
        val tkhd = trackHeader(2, 0, 0, 0x0100)
        val mdhd = mdhd(AUDIO_RATE)
        val hdlr = hdlr("soun", "OpenTV audio")
        val esds = Box("esds", 0, 0).also {
            // ES_Descriptor → DecoderConfigDescriptor (AAC LC, 0x40) → DecoderSpecificInfo (2 bytes) → SLConfig
            it.bytes(byteArrayOf(0x03, 25, 0, 2, 0, 0x04, 17, 0x40, 0x15, 0, 0, 0))
            it.u32(128_000); it.u32(128_000)
            it.bytes(byteArrayOf(0x05, 2, 0x11, 0x90.toByte(), 0x06, 1, 2))
        }.build()
        val mp4a = Box("mp4a", -1, 0).also {
            repeat(6) { _ -> it.u8(0) }; it.u16(1); it.u32(0); it.u32(0)
            it.u16(2); it.u16(16); it.u16(0); it.u16(0); it.u32(AUDIO_RATE.toLong() shl 16); it.bytes(esds)
        }.build()
        val stsd = Box("stsd", 0, 0).also { it.u32(1); it.bytes(mp4a) }.build()
        return box("trak", tkhd + box("mdia", mdhd + hdlr + box("minf", box("smhd", ByteArray(8)) + dinf() + stbl(stsd))))
    }

    private fun trackHeader(id: Int, w: Int, h: Int, volume: Int): ByteArray = Box("tkhd", 0, 7).also {
        it.u32(0); it.u32(0); it.u32(id.toLong()); it.u32(0); it.u32(0)
        it.u32(0); it.u32(0); it.u16(0); it.u16(0); it.u16(volume); it.u16(0)
        it.matrix(); it.u32(w.toLong() shl 16); it.u32(h.toLong() shl 16)
    }.build()

    private fun mdhd(rate: Int) = Box("mdhd", 0, 0).also { it.u32(0); it.u32(0); it.u32(rate.toLong()); it.u32(0); it.u16(0x55C4); it.u16(0) }.build()

    private fun hdlr(type: String, name: String) = Box("hdlr", 0, 0).also {
        it.u32(0); it.bytes(type.toByteArray()); repeat(3) { _ -> it.u32(0) }; it.bytes(name.toByteArray()); it.u8(0)
    }.build()

    private fun dinf(): ByteArray {
        val url = Box("url ", 0, 1).build()
        val dref = Box("dref", 0, 0).also { it.u32(1); it.bytes(url) }.build()
        return box("dinf", dref)
    }

    private fun stbl(stsd: ByteArray): ByteArray {
        val empty = { t: String -> Box(t, 0, 0).also { it.u32(0) }.build() }
        val stsz = Box("stsz", 0, 0).also { it.u32(0); it.u32(0) }.build()
        return box("stbl", stsd + empty("stts") + empty("stsc") + stsz + empty("stco"))
    }

    // ---- Construcción de cajas ----

    private class Box(private val type: String, private val version: Int, private val flags: Int) {
        private val out = ByteArrayOutputStream()
        init { if (version >= 0) { out.write(version); u24(flags) } }
        private fun u24(v: Int) { out.write(v shr 16); out.write(v shr 8); out.write(v) }
        fun u8(v: Int) = out.write(v)
        fun u16(v: Int) { out.write(v shr 8); out.write(v) }
        fun u32(v: Long) { out.write((v shr 24).toInt()); out.write((v shr 16).toInt()); out.write((v shr 8).toInt()); out.write(v.toInt()) }
        fun u64(v: Long) { u32(v ushr 32); u32(v and 0xFFFFFFFFL) }
        fun bytes(b: ByteArray) = out.write(b)
        fun matrix() { for (v in longArrayOf(0x10000, 0, 0, 0, 0x10000, 0, 0, 0, 0x40000000)) u32(v) }
        fun build(): ByteArray = box(type, out.toByteArray())
    }

    companion object {
        const val VIDEO_RATE = 90_000
        const val AUDIO_RATE = 48_000
        const val AAC_FRAME = 1024L

        fun u32(v: Int): ByteArray = byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())

        fun box(type: String, payload: ByteArray): ByteArray {
            val size = payload.size + 8
            return u32(size) + type.toByteArray(Charsets.ISO_8859_1) + payload
        }
    }
}

/** Utilidades de Annex B (códigos de inicio 00 00 01 / 00 00 00 01) ↔ AVCC (prefijo de largo de 4 bytes). */
object AnnexB {
    /** Separa unidades NAL sin el código de inicio. */
    fun split(data: ByteArray): List<ByteArray> {
        val starts = ArrayList<Pair<Int, Int>>() // (posición del código, largo del código)
        var i = 0
        while (i + 2 < data.size) {
            if (data[i].toInt() == 0 && data[i + 1].toInt() == 0) {
                if (data[i + 2].toInt() == 1) { starts.add(i to 3); i += 3; continue }
                if (i + 3 < data.size && data[i + 2].toInt() == 0 && data[i + 3].toInt() == 1) { starts.add(i to 4); i += 4; continue }
            }
            i++
        }
        val result = ArrayList<ByteArray>()
        for ((index, st) in starts.withIndex()) {
            val begin = st.first + st.second
            var end = if (index + 1 < starts.size) starts[index + 1].first else data.size
            while (end > begin && data[end - 1].toInt() == 0 && index + 1 < starts.size) end-- // ceros de relleno antes del próximo código
            if (end > begin) result.add(data.copyOfRange(begin, end))
        }
        return result
    }

    /** Convierte a AVCC; descarta SPS/PPS/AUD sueltos (van en `avcC`) para no duplicarlos en cada muestra. */
    fun toAvcc(annexB: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        for (nal in split(annexB)) {
            val type = nal[0].toInt() and 0x1F
            if (type == 7 || type == 8 || type == 9) continue
            out.write(Fmp4Muxer.u32(nal.size))
            out.write(nal)
        }
        return out.toByteArray()
    }
}
