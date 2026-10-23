package com.opentv.app.stream

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.view.Surface

/**
 * Codifica en H.264 lo que se dibuje en su [Surface] (una pantalla virtual de 1920×1080) y entrega
 * el resultado ya empaquetado en MPEG-TS. Repite el último cuadro cuando la imagen no cambia, así el
 * TV recibe video continuo aunque la interfaz esté quieta.
 */
class ScreenEncoder(
    private val width: Int = 1920,
    private val height: Int = 1080,
    private val fps: Int = 30,
    private val bitrate: Int = 6_000_000,
    private val onChunk: (chunk: ByteArray, keyStart: Boolean) -> Unit,
) {
    private var codec: MediaCodec? = null
    private var thread: Thread? = null
    @Volatile private var running = false
    private val muxer = TsMuxer()

    /** Prepara el codificador y devuelve la superficie donde hay que dibujar. */
    fun start(): Surface {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 100_000L)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = encoder.createInputSurface()
            encoder.start()
            codec = encoder
            running = true
            thread = Thread({ drain(encoder) }, "opentv-encoder").apply { isDaemon = true; start() }
            return surface
        } catch (e: RuntimeException) {
            encoder.release()
            throw e
        }
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

    private fun drain(encoder: MediaCodec) {
        val info = MediaCodec.BufferInfo()
        var firstPtsUs = -1L
        try {
            while (running) {
                val index = encoder.dequeueOutputBuffer(info, 100_000L)
                if (index < 0) continue
                val buffer = encoder.getOutputBuffer(index)
                if (buffer != null && info.size > 0) {
                    val data = ByteArray(info.size)
                    buffer.position(info.offset)
                    buffer.get(data)
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        muxer.setCodecConfig(data)
                    } else {
                        if (firstPtsUs < 0) firstPtsUs = info.presentationTimeUs
                        val key = info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
                        val ts = muxer.mux(data, info.presentationTimeUs - firstPtsUs, key)
                        onChunk(ts, key)
                    }
                }
                encoder.releaseOutputBuffer(index, false)
            }
        } catch (e: IllegalStateException) {
            // El codificador se detuvo mientras se leía: es parte del cierre normal.
        }
    }
}
