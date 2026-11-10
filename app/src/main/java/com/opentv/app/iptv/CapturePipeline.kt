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
    /** Igual que [onTs] pero indica si el bloque es un cuadro de video (para contar cuadros por segmento). */
    fun onTsKind(chunk: ByteArray, keyStart: Boolean, video: Boolean) = onTs(chunk, keyStart)
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
    initialPreset: IptvPreset,
    private val captureAudio: Boolean,
    private val sink: PipelineSink,
    private val onProjectionStopped: () -> Unit,
    /** Bomba de cuadros a ritmo fijo (recomendado); si no se puede iniciar se usa la captura directa. */
    private val usePump: Boolean = true,
    /** Fin inesperado de una parte de la tubería (codificador, etc.), con el motivo. */
    private val onEnded: (StopReason, String) -> Unit = { _, _ -> },
    /** H.264 Baseline aunque el perfil no lo pida (Miracast lo exige: CBP). */
    private val forceBaseline: Boolean = false,
) {
    private val appContext = context.applicationContext
    /** Perfil en uso; cambia con [switchPreset]. */
    @Volatile var preset: IptvPreset = initialPreset
        private set
    private var muxer: TsMuxer? = null
    private var mp4: Fmp4Muxer? = null
    private var originUs = 0L
    private val encodeLatency = IntervalWindow(240)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var encoder: ScreenEncoder? = null
    private var silent: SilentAudioEncoder? = null
    private var playback: PlaybackAudioEncoder? = null
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var pump: FramePump? = null
    private var encoderSurface: android.view.Surface? = null
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

    /** `true` si los cuadros pasan por la bomba de ritmo fijo; `false` = captura directa al codificador. */
    val pumpActive: Boolean get() = pump != null

    /** Cuadros nuevos recibidos de la captura de pantalla; `null` si no se pueden contar (captura directa). */
    val capturedFrames: Long? get() = pump?.capturedFrames?.get()

    /** Cuadros entregados al codificador por la bomba; `null` en captura directa. */
    val renderedFrames: Long? get() = pump?.renderedFrames?.get()

    /** Error en tiempo de ejecución de la bomba (si dejó de dibujar). */
    val pumpFailure: String? get() = pump?.failure
    /** Intervalos entre cuadros dibujados por la bomba (vacío si no hay bomba). */
    fun frameIntervals(): IntervalStats = pump?.intervals?.snapshot() ?: IntervalStats(0, 0.0, 0.0, 0.0, 0.0)
    val lateTicks: Long get() = pump?.lateTicks?.get() ?: 0L
    fun setMarks(on: Boolean) { pump?.marks = on }

    /** `true` si el codificador aplicó el perfil y nivel pedidos. */
    val profileApplied: Boolean get() = encoder?.profileApplied ?: false
    val producedBytes: Long get() = produced.get()

    fun audioState(nowMs: Long): IptvAudio {
        val pb = playback
        return if (pb != null) AudioActivityMeter.captureState(pb.everNonZero, pb.lastSoundAtMs, nowMs) else fallbackAudio
    }

    /** `true` si se captura el audio real del teléfono (no la pista en silencio). */
    val capturesSystemAudio: Boolean get() = playback != null

    fun takeAudioPeakDb(): Double = playback?.takePeakDb() ?: -96.0

    /**
     * Si la bomba falló en marcha: la libera y conecta la pantalla virtual directo al codificador (como antes de la bomba).
     * Debe llamarse desde el hilo principal.
     */
    fun fallbackToDirect() {
        val direct = encoderSurface ?: return
        val fp = pump ?: return
        runCatching { fp.stop() }
        pump = null
        runCatching { display?.surface = direct }
        note = listOfNotNull(note, "El ritmo fijo de cuadros falló y se pasó a la captura directa.").joinToString(" ")
    }

    fun requestKeyFrame() { encoder?.requestKeyFrame() }

    /** Ritmo en vivo del redibujado (solo con el ritmo fijo activo). `false` si no hay bomba. */
    fun setPumpFps(fps: Int): Boolean { val p = pump ?: return false; p.setFps(fps); return true }

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

    /** Crea codificador y (si se puede) bomba de cuadros para [p]; devuelve la superficie donde debe dibujar la captura. */
    private fun buildVideo(p: IptvPreset): Pair<android.view.Surface, String?> {
        val level = AvcLevels.forStream(p.width, p.height, p.fps)
        val ts = muxer!!
        val mp4m = mp4!!
        val profile = if (p.height >= 1080) StreamProfile.AVC_TS else StreamProfile.HD720
        val enc = ScreenEncoder(
            profile, ts, originUs, fps = p.fps, bitrate = p.bitrateBps, iFrameSeconds = 1, avcLevel = level,
            width = p.width, height = p.height,
            codecName = EncoderProbe.pick(p.width, p.height, p.fps),
            onConfig = { cfg -> synchronized(mp4m) { mp4m.setConfig(cfg) } },
            onFrame = { frame, ptsUs, key -> synchronized(mp4m) { mp4m.addVideo(frame, ptsUs, key) } },
            baseline = p.baselineProfile || forceBaseline,
            onEnded = { why -> mainHandler.post { onEnded(StopReason.ENCODER_STOPPED, why) } },
            onEncodeLatency = { encodeLatency.add(it) },
        ) { chunk, key ->
            produced.addAndGet(chunk.size.toLong())
            sink.onTsKind(chunk, key, true)
        }
        encoder = enc
        val surface = enc.start()
        this.encoderSurface = surface
        codecName = enc.usedCodec
        var captureSurface = surface
        var pumpNote: String? = null
        if (usePump) {
            try {
                val fp = FramePump(surface, p.width, p.height, p.fps)
                captureSurface = fp.start()
                pump = fp
            } catch (e: RuntimeException) {
                pump = null
                pumpNote = "No se pudo iniciar el ritmo fijo de cuadros (${e.message ?: e.javaClass.simpleName}); se usa la captura directa."
            }
        }
        return captureSurface to pumpNote
    }

    /** Estadística del tiempo entre dibujar un cuadro y recibirlo codificado (ms). */
    fun encodeLatencyStats(): IntervalStats = encodeLatency.snapshot()

    /** Cuadros dibujados que el codificador todavía no devolvió (cola del codificador); 0 si no hay bomba que los cuente. */
    val backlog: Long get() { val r = renderedFrames ?: return 0L; return (r - encodedFrames).coerceAtLeast(0L) }

    /**
     * Cambia resolución/FPS/bitrate en marcha SIN pedir otra captura: se reemplazan codificador y bomba y se redimensiona la
     * pantalla virtual existente (Android 14 no deja crear otra con la misma proyección). El audio y los contadores del
     * empaquetador siguen. NO verificado en hardware. Devuelve `false` y conserva todo si no se pudo. Hilo principal.
     */
    fun switchPreset(next: IptvPreset): Boolean {
        val d = display ?: return false
        if (next == preset) return true
        val old = preset
        val oldEnc = encoder; val oldPump = pump
        return try {
            runCatching { oldPump?.stop() }
            runCatching { oldEnc?.stop() }
            pump = null; encoder = null
            preset = next
            synchronized(this) { mp4 = Fmp4Muxer(next.width, next.height, hasAudioTrack, { sink.onMp4Init(it) }, { sink.onMp4Fragment(it) }) }
            encodeLatency.clear()
            val (surface, pumpNote) = buildVideo(next)
            d.resize(next.width, next.height, appContext.resources.displayMetrics.densityDpi)
            d.surface = surface
            note = listOfNotNull(note, pumpNote).joinToString(" ").ifBlank { null }
            true
        } catch (e: RuntimeException) {
            // No se pudo armar el nuevo perfil: se intenta volver al anterior para no dejar la emisión muerta.
            runCatching { pump?.stop() }; runCatching { encoder?.stop() }
            preset = old
            val back = runCatching {
                mp4 = Fmp4Muxer(old.width, old.height, hasAudioTrack, { sink.onMp4Init(it) }, { sink.onMp4Fragment(it) })
                val (surface, _) = buildVideo(old)
                d.resize(old.width, old.height, appContext.resources.displayMetrics.densityDpi); d.surface = surface
            }.isSuccess
            note = listOfNotNull(note, "No se pudo cambiar a ${next.shortLabel} (${e.javaClass.simpleName}); ${if (back) "se mantiene ${old.shortLabel}" else "la emisión quedó detenida"}.").joinToString(" ")
            if (!back) mainHandler.post { onEnded(StopReason.ENCODER_STOPPED, "Falló el cambio de perfil y no se pudo volver al anterior.") }
            false
        }
    }

    private fun doStart() {
        originUs = System.nanoTime() / 1000
        muxer = TsMuxer(hasAudioTrack)
        mp4 = Fmp4Muxer(preset.width, preset.height, hasAudioTrack, { sink.onMp4Init(it) }, { sink.onMp4Fragment(it) })
        val (initialSurface, pumpNote) = buildVideo(preset)
        val muxer = this.muxer!!
        val captureSurface = initialSurface

        val onAudio: (ByteArray, Long) -> Unit = { adts, pts ->
            val ts = muxer.muxAudio(adts, pts)
            produced.addAndGet(ts.size.toLong())
            sink.onTsKind(ts, false, false)
            this.mp4?.let { m -> synchronized(m) { m.addAudio(adts, pts) } }
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
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, captureSurface, null, null,
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
        note = listOfNotNull(audioNote, pumpNote).joinToString(" ").ifBlank { null }
    }

    fun stop() {
        locks.release()
        runCatching { display?.release() }
        runCatching { pump?.stop() }
        runCatching { playback?.stop() }
        runCatching { silent?.stop() }
        runCatching { encoder?.stop() }
        runCatching { projection?.stop() }
        display = null; pump = null; playback = null; silent = null; encoder = null; projection = null
    }
}
