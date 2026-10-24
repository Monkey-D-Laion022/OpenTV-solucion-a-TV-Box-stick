package com.opentv.app.stream

import java.io.ByteArrayOutputStream

/** Cabecera ADTS que lleva cada fotograma de audio AAC dentro de un flujo TS. Lógica pura. */
object Adts {
    /** AAC-LC. [freqIndex] 3 = 48 kHz, 4 = 44,1 kHz. */
    fun header(aacSize: Int, freqIndex: Int = 3, channels: Int = 2): ByteArray {
        val length = aacSize + 7
        return byteArrayOf(
            0xFF.toByte(),
            0xF1.toByte(),
            ((1 shl 6) or (freqIndex shl 2) or (channels shr 2)).toByte(),
            (((channels and 3) shl 6) or (length shr 11)).toByte(),
            ((length shr 3) and 0xFF).toByte(),
            (((length and 7) shl 5) or 0x1F).toByte(),
            0xFC.toByte(),
        )
    }
}

/**
 * Empaqueta video H.264 (formato Annex-B, el que entrega MediaCodec) y, opcionalmente, audio AAC
 * (con cabecera ADTS) en un flujo MPEG-TS, que es el formato que los televisores DLNA abren en vivo.
 * Sin B-frames. Lógica pura: se prueba en la JVM. Cada llamada devuelve paquetes de 188 bytes
 * completos. El audio y el video pueden llamarse desde hilos distintos.
 */
class TsMuxer(
    private val withAudio: Boolean = false,
    private val ptsOffset: Long = DEFAULT_PTS_OFFSET,
    private val pcrLead: Long = DEFAULT_PCR_LEAD,
) {

    private class Stream(val pid: Int, val streamId: Int) {
        var cc = 0
    }

    private val video = Stream(PID_VIDEO, 0xE0)
    private val audio = Stream(PID_AUDIO, 0xC0)

    private var codecConfig: ByteArray = ByteArray(0)
    private var patCc = 0
    private var pmtCc = 0

    /** SPS + PPS tal como los entrega el codificador; se repiten antes de cada fotograma clave. */
    @Synchronized
    fun setCodecConfig(annexB: ByteArray) {
        codecConfig = annexB.copyOf()
    }

    @Synchronized
    fun mux(frame: ByteArray, ptsUs: Long, keyFrame: Boolean): ByteArray {
        val out = ByteArrayOutputStream(frame.size + frame.size / 40 + 512)
        if (keyFrame) {
            writePsi(out, PID_PAT, buildPat(), patCc).also { patCc = (patCc + 1) and 0xF }
            writePsi(out, PID_PMT, buildPmt(), pmtCc).also { pmtCc = (pmtCc + 1) and 0xF }
        }

        val es = ByteArrayOutputStream(frame.size + codecConfig.size + 16)
        es.write(AUD)
        if (keyFrame) es.write(codecConfig)
        es.write(frame)
        val pes = buildPes(video.streamId, es.toByteArray(), ptsUs, unbounded = true)

        val pts90 = ptsUs * 9 / 100 + ptsOffset
        val pcr = (pts90 - pcrLead).coerceAtLeast(0)
        writePes(out, video, pes, pcr, keyFrame)
        return out.toByteArray()
    }

    /** Un fotograma de audio AAC ya con cabecera ADTS (ver [Adts]). */
    @Synchronized
    fun muxAudio(adtsFrame: ByteArray, ptsUs: Long): ByteArray {
        check(withAudio) { "Este muxer se creó sin audio" }
        val out = ByteArrayOutputStream(adtsFrame.size + 256)
        writePes(out, audio, buildPes(audio.streamId, adtsFrame, ptsUs, unbounded = false), null, false)
        return out.toByteArray()
    }

    // --- PES ---------------------------------------------------------------------------------

    private fun buildPes(streamId: Int, es: ByteArray, ptsUs: Long, unbounded: Boolean): ByteArray {
        val pts = ptsUs * 9 / 100 + ptsOffset
        val header = ByteArray(14)
        header[0] = 0; header[1] = 0; header[2] = 1
        header[3] = streamId.toByte()
        val length = if (unbounded) 0 else 3 + 5 + es.size
        require(length <= 0xFFFF) { "Fotograma de audio demasiado grande" }
        header[4] = (length shr 8).toByte()
        header[5] = length.toByte()
        header[6] = 0x80.toByte()
        header[7] = 0x80.toByte() // solo PTS
        header[8] = 5
        header[9] = (0x21 or (((pts shr 30) and 0x7).toInt() shl 1)).toByte()
        header[10] = ((pts shr 22) and 0xFF).toByte()
        header[11] = (((pts shr 14) and 0xFE).toInt() or 1).toByte()
        header[12] = ((pts shr 7) and 0xFF).toByte()
        header[13] = (((pts shl 1) and 0xFE).toInt() or 1).toByte()
        return header + es
    }

    /** [pcr90] nulo = sin PCR (audio). */
    private fun writePes(out: ByteArrayOutputStream, stream: Stream, pes: ByteArray, pcr90: Long?, randomAccess: Boolean) {
        var offset = 0
        var first = true
        while (offset < pes.size) {
            val remaining = pes.size - offset
            val packet = ByteArray(PACKET)
            packet[0] = SYNC
            packet[1] = ((if (first) 0x40 else 0) or ((stream.pid shr 8) and 0x1F)).toByte()
            packet[2] = (stream.pid and 0xFF).toByte()

            val full = PACKET - 4
            val withPcr = first && pcr90 != null
            val capacity = if (withPcr) full - 8 else full // 8 = adaptation field con PCR
            val take = minOf(remaining, capacity)
            var pos = 4

            if (withPcr) {
                val afLength = if (take < capacity) full - take - 1 else 7
                packet[3] = (0x30 or stream.cc).toByte()
                packet[pos++] = afLength.toByte()
                packet[pos++] = ((if (randomAccess) 0x40 else 0) or 0x10).toByte()
                val base = pcr90!! and 0x1FFFFFFFFL
                packet[pos++] = ((base shr 25) and 0xFF).toByte()
                packet[pos++] = ((base shr 17) and 0xFF).toByte()
                packet[pos++] = ((base shr 9) and 0xFF).toByte()
                packet[pos++] = ((base shr 1) and 0xFF).toByte()
                packet[pos++] = (((base and 1L).toInt() shl 7) or 0x7E).toByte()
                packet[pos++] = 0
                while (pos < 4 + 1 + afLength) packet[pos++] = 0xFF.toByte()
            } else if (take < full) {
                // Último paquete (o PES corto): relleno con adaptation field hasta los 188 bytes.
                val afLength = full - take - 1
                packet[3] = (0x30 or stream.cc).toByte()
                packet[pos++] = afLength.toByte()
                if (afLength > 0) {
                    packet[pos++] = 0
                    while (pos < 4 + 1 + afLength) packet[pos++] = 0xFF.toByte()
                }
            } else {
                packet[3] = (0x10 or stream.cc).toByte()
            }
            System.arraycopy(pes, offset, packet, pos, take)
            out.write(packet)
            offset += take
            stream.cc = (stream.cc + 1) and 0xF
            first = false
        }
    }

    // --- PSI (PAT / PMT) --------------------------------------------------------------------

    private fun writePsi(out: ByteArrayOutputStream, pid: Int, section: ByteArray, cc: Int) {
        val packet = ByteArray(PACKET) { 0xFF.toByte() }
        packet[0] = SYNC
        packet[1] = (0x40 or ((pid shr 8) and 0x1F)).toByte()
        packet[2] = (pid and 0xFF).toByte()
        packet[3] = (0x10 or cc).toByte()
        packet[4] = 0 // pointer_field
        System.arraycopy(section, 0, packet, 5, section.size)
        out.write(packet)
    }

    private fun buildPat(): ByteArray {
        val body = byteArrayOf(
            0x00, 0x01, // transport_stream_id
            0xC1.toByte(), 0x00, 0x00, // versión 0, current, sección 0/0
            0x00, 0x01, // program_number 1
            (0xE0 or ((PID_PMT shr 8) and 0x1F)).toByte(), (PID_PMT and 0xFF).toByte(),
        )
        return section(0x00, body)
    }

    private fun buildPmt(): ByteArray {
        val head = byteArrayOf(
            0x00, 0x01, // program_number
            0xC1.toByte(), 0x00, 0x00,
            (0xE0 or ((PID_VIDEO shr 8) and 0x1F)).toByte(), (PID_VIDEO and 0xFF).toByte(), // PCR PID
            0xF0.toByte(), 0x00, // program_info_length 0
        )
        val videoEs = byteArrayOf(
            0x1B, // H.264
            (0xE0 or ((PID_VIDEO shr 8) and 0x1F)).toByte(), (PID_VIDEO and 0xFF).toByte(),
            0xF0.toByte(), 0x00, // ES_info_length 0
        )
        val audioEs = byteArrayOf(
            0x0F, // AAC con ADTS
            (0xE0 or ((PID_AUDIO shr 8) and 0x1F)).toByte(), (PID_AUDIO and 0xFF).toByte(),
            0xF0.toByte(), 0x00,
        )
        return section(0x02, head + videoEs + (if (withAudio) audioEs else ByteArray(0)))
    }

    private fun section(tableId: Int, body: ByteArray): ByteArray {
        val length = body.size + 4 // + CRC32
        val head = byteArrayOf(
            tableId.toByte(),
            (0xB0 or ((length shr 8) and 0x0F)).toByte(),
            (length and 0xFF).toByte(),
        )
        val withoutCrc = head + body
        val crc = crc32Mpeg(withoutCrc)
        return withoutCrc + byteArrayOf(
            (crc shr 24).toByte(), (crc shr 16).toByte(), (crc shr 8).toByte(), crc.toByte(),
        )
    }

    companion object {
        const val PACKET = 188
        const val SYNC: Byte = 0x47
        const val PID_PAT = 0x0000
        const val PID_PMT = 0x1000
        const val PID_VIDEO = 0x0100
        const val PID_AUDIO = 0x0101

        /** Desfase para que el PCR (que debe ir antes del PTS) nunca sea negativo. */
        const val DEFAULT_PTS_OFFSET = 63_000L // 0,7 s a 90 kHz
        const val DEFAULT_PCR_LEAD = 45_000L // 0,5 s

        private val AUD = byteArrayOf(0, 0, 0, 1, 0x09, 0xF0.toByte())

        /** CRC-32/MPEG-2 (polinomio 0x04C11DB7, sin reflejo, valor inicial 0xFFFFFFFF). */
        fun crc32Mpeg(data: ByteArray): Int {
            var crc = -1
            for (b in data) {
                crc = crc xor ((b.toInt() and 0xFF) shl 24)
                repeat(8) {
                    crc = if (crc < 0) (crc shl 1) xor 0x04C11DB7 else crc shl 1
                }
            }
            return crc
        }
    }
}
