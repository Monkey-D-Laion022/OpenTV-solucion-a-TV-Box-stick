package com.opentv.app.stream

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat

/**
 * Genera audio AAC en silencio. Muchos televisores se niegan a abrir un video sin pista de audio;
 * esta pista vacía los deja conformes. Cuesta casi nada: 64 kbps de ceros.
 */
class SilentAudioEncoder(
    private val originUs: Long,
    private val onFrame: (adts: ByteArray, ptsUs: Long) -> Unit,
) {
    private var codec: MediaCodec? = null
    private var thread: Thread? = null
    @Volatile private var running = false

    fun start() {
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        try {
            encoder.configure(format(), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
        } catch (e: RuntimeException) {
            encoder.release()
            throw e
        }
        codec = encoder
        running = true
        thread = Thread({ loop(encoder) }, "opentv-audio").apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        thread?.join(1_000)
        thread = null
        codec?.let {
            try {
                it.stop()
            } catch (e: IllegalStateException) {
                // Ya estaba detenido.
            }
            it.release()
        }
        codec = null
    }

    private fun loop(encoder: MediaCodec) {
        val info = MediaCodec.BufferInfo()
        val startUs = System.nanoTime() / 1000 - originUs
        var samples = 0L
        val startWall = System.nanoTime() / 1000
        try {
            while (running) {
                // Mantiene el audio a ritmo de reloj, con 200 ms de adelanto como máximo.
                val wallElapsedUs = System.nanoTime() / 1000 - startWall
                val sentUs = samples * 1_000_000L / SAMPLE_RATE
                if (sentUs <= wallElapsedUs + 200_000L) {
                    val index = encoder.dequeueInputBuffer(5_000L)
                    if (index >= 0) {
                        val input = encoder.getInputBuffer(index)
                        input?.clear()
                        val bytes = SAMPLES_PER_FRAME * CHANNELS * 2
                        // El búfer ya viene en cero la primera vez; se limpia por si se reutiliza.
                        if (input != null) {
                            while (input.position() < bytes) input.put(0)
                        }
                        encoder.queueInputBuffer(index, 0, bytes, startUs + sentUs, 0)
                        samples += SAMPLES_PER_FRAME
                    }
                } else {
                    Thread.sleep(5)
                }
                while (true) {
                    val out = encoder.dequeueOutputBuffer(info, 0)
                    if (out < 0) break
                    val buffer = encoder.getOutputBuffer(out)
                    if (buffer != null && info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        val aac = ByteArray(info.size)
                        buffer.position(info.offset)
                        buffer.get(aac)
                        onFrame(Adts.header(aac.size) + aac, info.presentationTimeUs)
                    }
                    encoder.releaseOutputBuffer(out, false)
                }
            }
        } catch (e: IllegalStateException) {
            // Cierre normal.
        } catch (e: InterruptedException) {
            // Cierre normal.
        }
    }

    companion object {
        const val SAMPLE_RATE = 48_000
        const val CHANNELS = 2
        const val SAMPLES_PER_FRAME = 1024

        private fun format() = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, CHANNELS).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 64_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 8192)
        }

        /** Hay un codificador AAC en este teléfono. */
        fun isAvailable(): Boolean = try {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).findEncoderForFormat(format()) != null
        } catch (e: RuntimeException) {
            false
        }
    }
}
