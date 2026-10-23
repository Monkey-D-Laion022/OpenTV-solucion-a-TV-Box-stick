package com.opentv.app.stream

import java.io.ByteArrayOutputStream

/**
 * Empaqueta video H.264 (formato Annex-B, el que entrega MediaCodec) en un flujo MPEG-TS, que es el
 * formato que los televisores DLNA abren en vivo. Solo video, sin B-frames. Lógica pura: se prueba
 * en la JVM. Cada llamada a [mux] devuelve paquetes de 188 bytes completos.
 */
class TsMuxer {

    private var codecConfig: ByteArray = ByteArray(0)
    private var patCc = 0
    private var pmtCc = 0
    private var videoCc = 0

    /** SPS + PPS tal como los entrega el codificador; se repiten antes de cada fotograma clave. */
    fun setCodecConfig(annexB: ByteArray) {
        codecConfig = annexB.copyOf()
    }

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
        val pes = buildPes(es.toByteArray(), ptsUs)

        val pts90 = ptsUs * 9 / 100 + PTS_OFFSET
        val pcr = (pts90 - PCR_LEAD).coerceAtLeast(0)
        writePes(out, pes, pcr, keyFrame)
        return out.toByteArray()
    }

    // --- PES ---------------------------------------------------------------------------------

    private fun buildPes(es: ByteArray, ptsUs: Long): ByteArray {
        val pts = ptsUs * 9 / 100 + PTS_OFFSET
        val header = ByteArray(14)
        header[0] = 0; header[1] = 0; header[2] = 1
        header[3] = 0xE0.toByte()
        header[4] = 0; header[5] = 0 // longitud 0 = sin límite (permitido para video)
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

    private fun writePes(out: ByteArrayOutputStream, pes: ByteArray, pcr90: Long, keyFrame: Boolean) {
        var offset = 0
        var first = true
        while (offset < pes.size) {
            val remaining = pes.size - offset
            val packet = ByteArray(PACKET)
            packet[0] = SYNC
            packet[1] = ((if (first) 0x40 else 0) or ((PID_VIDEO shr 8) and 0x1F)).toByte()
            packet[2] = (PID_VIDEO and 0xFF).toByte()

            val capacityWithoutAf = PACKET - 4
            val capacity = if (first) capacityWithoutAf - 8 else capacityWithoutAf // 8 = AF con PCR
            val take = minOf(remaining, capacity)
            var pos = 4

            if (first) {
                // Adaptation field con PCR (y marca de acceso aleatorio en fotogramas clave).
                val afLength = if (take < capacity) capacityWithoutAf - take - 1 else 7
                packet[3] = (0x30 or videoCc).toByte()
                packet[pos++] = afLength.toByte()
                packet[pos++] = ((if (keyFrame) 0x40 else 0) or 0x10).toByte()
                val base = pcr90 and 0x1FFFFFFFFL
                packet[pos++] = ((base shr 25) and 0xFF).toByte()
                packet[pos++] = ((base shr 17) and 0xFF).toByte()
                packet[pos++] = ((base shr 9) and 0xFF).toByte()
                packet[pos++] = ((base shr 1) and 0xFF).toByte()
                packet[pos++] = (((base and 1L).toInt() shl 7) or 0x7E).toByte()
                packet[pos++] = 0
                while (pos < 4 + 1 + afLength) packet[pos++] = 0xFF.toByte()
            } else if (take < capacityWithoutAf) {
                // Último paquete: relleno con adaptation field para completar los 188 bytes.
                val afLength = capacityWithoutAf - take - 1
                packet[3] = (0x30 or videoCc).toByte()
                packet[pos++] = afLength.toByte()
                if (afLength > 0) {
                    packet[pos++] = 0
                    while (pos < 4 + 1 + afLength) packet[pos++] = 0xFF.toByte()
                }
            } else {
                packet[3] = (0x10 or videoCc).toByte()
            }
            System.arraycopy(pes, offset, packet, pos, take)
            out.write(packet)
            offset += take
            videoCc = (videoCc + 1) and 0xF
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
        val body = byteArrayOf(
            0x00, 0x01, // program_number
            0xC1.toByte(), 0x00, 0x00,
            (0xE0 or ((PID_VIDEO shr 8) and 0x1F)).toByte(), (PID_VIDEO and 0xFF).toByte(), // PCR PID
            0xF0.toByte(), 0x00, // program_info_length 0
            0x1B, // H.264
            (0xE0 or ((PID_VIDEO shr 8) and 0x1F)).toByte(), (PID_VIDEO and 0xFF).toByte(),
            0xF0.toByte(), 0x00, // ES_info_length 0
        )
        return section(0x02, body)
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

        /** Desfase para que el PCR (que debe ir antes del PTS) nunca sea negativo. */
        private const val PTS_OFFSET = 63_000L // 0,7 s a 90 kHz
        private const val PCR_LEAD = 45_000L // 0,5 s

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
