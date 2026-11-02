package com.opentv.app.iptv

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.opentv.app.stream.MirrorRequest
import com.opentv.app.stream.ScreenEncoder
import com.opentv.app.stream.SilentAudioEncoder
import com.opentv.app.stream.StreamProfile
import com.opentv.app.stream.TsMuxer
import java.util.concurrent.atomic.AtomicLong

/** A dónde van los datos codificados. Se llama desde los hilos del codificador: no bloquear. */
interface PipelineSink {
    /** Bloque MPEG‑TS; [keyStart] = empieza en un fotograma clave (lleva PAT/PMT). */
    fun onTs(chunk: ByteArray, keyStart: Boolean)
    /** Segmento de inicio del MP4 fragmentado (se repite para cada cliente nuevo). */
    fun onMp4Init(init: ByteArray) {}
    /** Fragmento MP4 que empieza en un fotograma clave. */
    fun onMp4Fragment(fragment: ByteArray) {}
}

/**
 * Captura de pantalla (MediaProjection) + codificación H.264 por hardware (si el teléfono lo ofrece) + audio del
 * sistema (si hay permiso y Android lo permite; si no, pista en silencio) → MPEG‑TS y MP4 fragmentado. Lo comparten el
 * canal IPTV y el emisor OpenTV Link: así las dos tecnologías miden y se comportan igual. NO verificado en hardware.
 * Debe crearse y detenerse desde el hilo principal.
 */
class CapturePipeline(
    context: Context,
    private val request: MirrorRequest,
    val preset: IptvPreset,
    private val captureAudio: Boolean,
    private val sink: PipelineSink,
    private val onProjectionStopped: () -> Unit,
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var encoder: ScreenEncoder? = null
    private var silent: SilentAudioEncoder? = null
    private var playback: PlaybackAudioEncoder? = null
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private val produced = AtomicLong(0)
    private val locks = TransmissionLocks(context)

    /** Nombre del codificador realmente usado y si es de hardware (vacío antes de [start]). */
    @Volatile var codecName: String = ""
        private set

    /** Avisos para el usuario (sin permiso de audio, perfil rebajado, etc.). */
    @Volatile var note: String? = null
        private set

    private var fallbackAudio = IptvAudio.SILENT_TRACK
    val hasAudioTrack: Boolean = SilentAudioEncoder.isAvailable()

    val encodedFrames: Long get() = encoder?.encodedFrames?.get() ?: 0L
    val producedBytes: Long get() = produced.get()

    fun audioState(nowMs: Long): IptvAudio {
        val pb = playback
        return if (pb != null) AudioActivityMeter.captureState(pb.everNonZero, pb.lastSoundAtMs, nowMs) else fallbackAudio
    }

    /** `true` si se captura el audio real del teléfono (no la pista en silencio). */
    val capturesSystemAudio: Boolean get() = playback != null

    fun takeAudioPeakDb(): Double = playback?.takePeakDb() ?: -96.0

    fun requestKeyFrame() { encoder?.requestKeyFrame() }

    fun setBitrate(bps: Int): Boolean = encoder?.setBitrate(bps) ?: false

    /** Lanza [RuntimeException] si algo falla; en ese caso ya liberó lo que había abierto. */
    fun start() {
        locks.acquire()
        try {
            doStart()
        } catch (e: RuntimeException) {
            stop()
            throw e
        }
    }

    private fun doStart() {
        val level = AvcLevels.forStream(preset.width, preset.height, preset.fps)
        val originUs = System.nanoTime() / 1000
        val muxer = TsMuxer(hasAudioTrack)
        val mp4 = Fmp4Muxer(preset.width, preset.height, hasAudioTrack, { sink.onMp4Init(it) }, { sink.onMp4Fragment(it) })
        val profile = if (preset.height >= 1080) StreamProfile.AVC_TS else StreamProfile.HD720
        val enc = ScreenEncoder(
            profile, muxer, originUs, fps = preset.fps, bitrate = preset.bitrateBps, iFrameSeconds = 1, avcLevel = level,
            width = preset.width, height = preset.height,
            codecName = EncoderProbe.pick(preset.width, preset.height, preset.fps),
            onConfig = { cfg -> synchronized(mp4) { mp4.setConfig(cfg) } },
            onFrame = { frame, ptsUs, key -> synchronized(mp4) { mp4.addVideo(frame, ptsUs, key) } },
        ) { chunk, key ->
            produced.addAndGet(chunk.size.toLong())
            sink.onTs(chunk, key)
        }
        encoder = enc
        val surface = enc.start()
        codecName = enc.usedCodec

        val onAudio: (ByteArray, Long) -> Unit = { adts, pts ->
            val ts = muxer.muxAudio(adts, pts)
            produced.addAndGet(ts.size.toLong())
            sink.onTs(ts, false)
            synchronized(mp4) { mp4.addAudio(adts, pts) }
        }

        val manager = appContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val p = manager.getMediaProjection(request.resultCode, request.data)
            ?: throw IllegalStateException("Android no entregó la captura de pantalla.")
        projection = p
        // Desde Android 14 hay que registrar el callback antes de crear la pantalla virtual.
        p.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { mainHandler.post { onProjectionStopped() } }
        }, mainHandler)
        display = p.createVirtualDisplay(
            "OpenTV-captura", preset.width, preset.height, appContext.resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, surface, null, null,
        ) ?: throw IllegalStateException("Android no creó la pantalla de captura.")

        var audioNote: String? = null
        if (hasAudioTrack) {
            if (captureAudio && Build.VERSION.SDK_INT >= 29) {
                try {
                    playback = PlaybackAudioEncoder(p, originUs, onAudio).also { it.start() }
                } catch (e: RuntimeException) {
                    playback = null
                    audioNote = "No se pudo capturar el audio del teléfono (${e.javaClass.simpleName}); se emite una pista en silencio."
                    fallbackAudio = IptvAudio.UNAVAILABLE
                }
            } else if (captureAudio) {
                audioNote = "Capturar el audio del sistema exige Android 10 o superior; se emite una pista en silencio."
                fallbackAudio = IptvAudio.UNAVAILABLE
            } else {
                audioNote = "Sin permiso de audio: se emite una pista en silencio."
                fallbackAudio = IptvAudio.NO_PERMISSION
            }
            if (playback == null) silent = SilentAudioEncoder(originUs, onAudio).also { it.start() }
        }
        note = audioNote
    }

    fun stop() {
        locks.release()
        runCatching { display?.release() }
        runCatching { playback?.stop() }
        runCatching { silent?.stop() }
        runCatching { encoder?.stop() }
        runCatching { projection?.stop() }
        display = null; playback = null; silent = null; encoder = null; projection = null
    }
}
