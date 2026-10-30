package com.opentv.app.stream

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Bundle
import android.view.Surface
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

/**
 * Codifica en H.264 lo que se dibuje en su [Surface] y entrega el resultado empaquetado en MPEG-TS.
 * Perfil y nivel quedan fijos (Main, nivel 4.0 o 3.1) porque los televisores viejos no abren perfiles
 * desconocidos. Los tiempos se miden desde [originUs] para quedar alineados con el audio.
 */
class ScreenEncoder(
    private val profile: StreamProfile,
    private val muxer: TsMuxer,
    private val originUs: Long,
    private val fps: Int = FPS,
    private val bitrate: Int = BITRATE,
    private val iFrameSeconds: Int = 1,
    private val lowLatency: Boolean = false,
    /** Nivel H.264 explícito (constante `AVCLevel*`); `null` conserva el comportamiento histórico (4.0 en 1080p, 3.1 en lo demás). */
    private val avcLevel: Int? = null,
    private val onChunk: (chunk: ByteArray, keyStart: Boolean) -> Unit,
) {
    private var codec: MediaCodec? = null
    private var thread: Thread? = null
    @Volatile private var running = false

    val encodedFrames = AtomicLong(0)

    /** Prepara el codificador y devuelve la superficie donde hay que dibujar. */
    fun start(): Surface {
        val encoder = try {
            create(withProfile = true)
        } catch (e: RuntimeException) {
            create(withProfile = false) // algunos codificadores rechazan perfil/nivel explícitos
        }
        val surface = encoder.createInputSurface()
        encoder.start()
        codec = encoder
        running = true
        thread = Thread({ drain(encoder) }, "opentv-encoder").apply { isDaemon = true; start() }
        return surface
    }

    private fun create(withProfile: Boolean): MediaCodec {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, profile.width, profile.height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameSeconds)
            if (lowLatency) {
                // Claves de texto: los equipos que no las conocen las ignoran (KEY_LATENCY existe desde Android 11).
                setInteger("latency", 1)
                setInteger("priority", 0)
            }
            setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 200_000L) // red de seguridad si se frena el dibujo
            setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
            if (withProfile) {
                setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileMain)
                setInteger(
                    MediaFormat.KEY_LEVEL,
                    avcLevel ?: if (profile.height >= 1080) MediaCodecInfo.CodecProfileLevel.AVCLevel4 else MediaCodecInfo.CodecProfileLevel.AVCLevel31,
                )
            }
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        } catch (e: RuntimeException) {
            encoder.release()
            throw e
        }
        return encoder
    }

    /** Pide un fotograma clave ya (cuando se conecta el TV) para que no espere hasta 1 s. */
    fun requestKeyFrame() {
        try {
            codec?.setParameters(Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0) })
        } catch (e: RuntimeException) {
            // El codificador ya se detuvo.
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
        var lastPts = 0L
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
                        val now = System.nanoTime() / 1000 - originUs
                        val raw = info.presentationTimeUs - originUs
                        // Si el reloj del codificador no coincide con el del sistema, se usa el del sistema.
                        var pts = if (abs(raw - now) < 5_000_000L) raw else now
                        if (pts < lastPts) pts = lastPts
                        lastPts = pts
                        val key = info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
                        onChunk(muxer.mux(data, pts, key), key)
                        encodedFrames.incrementAndGet()
                    }
                }
                encoder.releaseOutputBuffer(index, false)
            }
        } catch (e: IllegalStateException) {
            // El codificador se detuvo mientras se leía: es parte del cierre normal.
        }
    }

    companion object {
        const val FPS = 15
        const val BITRATE = 4_000_000
    }
}
