package com.opentv.app.stream

import android.app.Presentation
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.graphics.PixelFormat
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import com.opentv.app.connect.DidlLite
import com.opentv.app.core.BuildInfo
import com.opentv.app.connect.DlnaController
import com.opentv.app.connect.DlnaMessages
import com.opentv.app.connect.DlnaResult
import com.opentv.app.connect.DlnaSoap
import com.opentv.app.connect.LanAddress
import com.opentv.app.connect.UpnpDevice
import java.io.IOException
import java.net.Inet4Address
import java.security.SecureRandom
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Permiso de captura de pantalla que el usuario concedió (resultado de `createScreenCaptureIntent`). */
class MirrorRequest(val resultCode: Int, val data: Intent)

sealed interface ProjectionState {
    data object Idle : ProjectionState
    data object Starting : ProjectionState
    data class Streaming(val url: String, val deviceName: String) : ProjectionState
    data class Failed(val message: String) : ProjectionState
}

/**
 * Proyecta la interfaz de OpenTV en un TV DLNA, sin cables y sin la función «Emitir» de Android:
 * dibuja la interfaz en una pantalla virtual propia de 1920×1080 (16:9), la codifica en H.264, la sirve
 * por HTTP en la red local como MPEG-TS y le pide al TV que abra ese enlace.
 *
 * Lo que se ve en el TV llega con unos segundos de retraso (el TV guarda un búfer): sirve para
 * navegar, no para juegos. Debe llamarse desde el hilo principal.
 */
class TvProjection(
    context: Context,
    private val dlna: DlnaController,
) {
    private val appContext = context.applicationContext
    private val displayManager = appContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    private val _state = MutableStateFlow<ProjectionState>(ProjectionState.Idle)
    val state: StateFlow<ProjectionState> = _state.asStateFlow()

    private val _stats = MutableStateFlow(ProjectionStats())
    val stats: StateFlow<ProjectionStats> = _stats.asStateFlow()

    private val _profile = MutableStateFlow(StreamProfile.AVC_TS)
    val profile: StateFlow<StreamProfile> = _profile.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())

    private val _latency = MutableStateFlow(StreamLatency.STABLE)
    val latency: StateFlow<StreamLatency> = _latency.asStateFlow()

    fun cycleLatency() {
        if (session == null) _latency.value = _latency.value.next()
    }

    private val _testPattern = MutableStateFlow(false)
    val testPattern: StateFlow<Boolean> = _testPattern.asStateFlow()

    /** Alterna la imagen de prueba (sin interfaz): sirve para saber si el corte depende de dibujar la interfaz. */
    fun toggleTestPattern() {
        if (session == null) _testPattern.value = !_testPattern.value
    }

    private val log = java.util.concurrent.CopyOnWriteArrayList<String>()
    private var originMs = 0L

    private fun note(message: String) {
        log.add("+%.1f s APP: %s".format(java.util.Locale.US, (System.currentTimeMillis() - originMs) / 1000.0, message))
    }

    /** Cambia a la siguiente combinación de formato; solo con la proyección detenida. */
    fun cycleProfile() {
        if (session == null) _profile.value = _profile.value.next()
    }

    private class Session(
        val hub: StreamHub,
        val server: HttpStreamServer,
        val encoder: ScreenEncoder,
        val audio: SilentAudioEncoder?,
        val feeder: ViewFrameFeeder?,
        val reader: ImageReader?,
        val padder: Thread,
        val virtualDisplay: VirtualDisplay,
        val presentation: Presentation?,
        val target: UpnpDevice,
        val profile: StreamProfile,
        val testPattern: Boolean,
        val latency: StreamLatency,
        val mirror: Boolean,
    )

    private var mediaProjection: MediaProjection? = null

    private var session: Session? = null

    /**
     * Arma todo y le pide al TV que reproduzca. [createPresentation] construye la ventana con la
     * interfaz que se verá en el TV, sobre la pantalla virtual que se le entrega.
     */
    suspend fun start(
        target: UpnpDevice,
        mirror: MirrorRequest? = null,
        createPresentation: ((Display) -> Presentation)? = null,
    ) {
        if (_state.value is ProjectionState.Starting || _state.value is ProjectionState.Streaming) return
        _state.value = ProjectionState.Starting
        release("reinicio")

        val ip = LanIp.pick()
        if (ip == null) {
            fail("No hay una red Wi‑Fi local activa. Conectá el teléfono a la misma red que el TV.")
            return
        }

        ProjectionService.start(appContext, mirror = mirror != null)
        val profile = _profile.value
        val test = _testPattern.value
        val latency = _latency.value
        log.clear()
        originMs = System.currentTimeMillis()
        note("inicio${if (mirror != null) " (espejo de pantalla)" else ""} · formato «${profile.label}»${if (test) " · imagen de prueba" else ""}")
        val hub = StreamHub(maxQueuedBytes = latency.hubQueueBytes)
        val token = randomToken()
        val server = HttpStreamServer(hub, token, acceptClient = ::isLanClient, profile = profile, sendBufferBytes = latency.sendBufferBytes)
        server.originMs = originMs
        val originUs = System.nanoTime() / 1000
        var audio: SilentAudioEncoder? = null
        var encoder: ScreenEncoder? = null
        var feeder: ViewFrameFeeder? = null
        var reader: ImageReader? = null
        var virtualDisplay: VirtualDisplay? = null
        var presentation: Presentation? = null
        try {
            val port = server.start(ip)

            // Pista de audio en silencio: muchos TV no abren video sin audio. Si no hay codificador, sigue sin audio.
            val withAudio = SilentAudioEncoder.isAvailable()
            val muxer = TsMuxer(withAudio, latency.ptsOffset90, latency.pcrLead90)
            val enc = ScreenEncoder(
                profile, muxer, originUs,
                fps = latency.fps, iFrameSeconds = latency.iFrameSeconds, lowLatency = latency.lowLatencyEncoder,
            ) { chunk, keyStart -> hub.publish(chunk, keyStart) }
            encoder = enc
            val encoderSurface = enc.start()
            hub.onSubscribe = { enc.requestKeyFrame() }
            if (withAudio) {
                audio = SilentAudioEncoder(originUs) { adts, pts -> hub.publish(muxer.muxAudio(adts, pts), false) }
                    .also { it.start() }
            }

            var vd: VirtualDisplay
            var feed: ViewFrameFeeder? = null
            var sink: ImageReader? = null
            var shown: Presentation? = null
            if (mirror != null) {
                // Espejo: la captura de pantalla del sistema va directo al codificador. Si la pantalla no cambia no hay
                // cuadros nuevos; el codificador repite el último (KEY_REPEAT_PREVIOUS_FRAME_AFTER).
                note("espejo: esperando el servicio en primer plano")
                ProjectionService.awaitReady()
                val manager = appContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                val projection = manager.getMediaProjection(mirror.resultCode, mirror.data)
                    ?: throw IllegalStateException("Android no entregó la captura de pantalla.")
                mediaProjection = projection
                // Desde Android 14 hay que registrar el callback antes de crear la pantalla virtual.
                projection.registerCallback(
                    object : MediaProjection.Callback() {
                        override fun onStop() {
                            mainHandler.post { if (session != null) shutdown() }
                        }
                    },
                    mainHandler,
                )
                vd = projection.createVirtualDisplay(
                    "OpenTV-Espejo", profile.width, profile.height, appContext.resources.displayMetrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, encoderSurface, null, null,
                ) ?: throw IllegalStateException("Android no creó la pantalla de captura.")
                virtualDisplay = vd
                note("captura de pantalla lista (${profile.width}×${profile.height}; el sistema la encaja sin deformar)")
            } else {
                // La interfaz se dibuja en una pantalla virtual cuyo resultado no se usa (se descarta); lo que va
                // al TV lo dibujamos nosotros directo sobre el codificador a ritmo fijo, así nunca queda sin cuadros.
                val reader2 = ImageReader.newInstance(profile.width, profile.height, PixelFormat.RGBA_8888, 2)
                sink = reader2
                reader = reader2
                reader2.setOnImageAvailableListener({ r -> runCatching { r.acquireLatestImage()?.close() } }, mainHandler)
                vd = displayManager.createVirtualDisplay(
                    "OpenTV-TV", profile.width, profile.height, DENSITY_DPI, reader2.surface,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION or
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
                ) ?: throw IllegalStateException("Android no creó la pantalla virtual.")
                virtualDisplay = vd
                val window = (createPresentation ?: throw IllegalStateException("Falta la ventana de la interfaz."))(vd.display)
                shown = window
                presentation = window
                window.show()

                note("pantalla virtual y ventana listas")
                val feeder2 = ViewFrameFeeder(encoderSurface, profile.width, profile.height, latency.fps, test) {
                    window.window?.decorView
                }
                feed = feeder2
                feeder = feeder2
                feeder2.start()
            }

            val url = "http://${ip.hostAddress}:$port${server.path}"
            val pad = startPadder(hub, latency.minRateBps)
            val current = Session(hub, server, enc, audio, feed, sink, pad, vd, shown, target, profile, test, latency, mirror != null)
            session = current
            startStats(current)

            val metadata = if (profile.sendMetadata) DidlLite.forUrl(url, "OpenTV", profile.protocolInfo()) else null
            note("enviando el enlace al TV (SetAVTransportURI + Play)")
            val result = dlna.play(target, url, metadata)
            note("el TV respondió: ${result::class.simpleName}")
            var pulling = current.server.bytesSent.get() > 0
            if (result != DlnaResult.Ok && !pulling) {
                // Algunos TV contestan el Play tarde o mal; se les da unos segundos para empezar a pedir el video.
                val deadline = System.currentTimeMillis() + PULL_GRACE_MS
                while (!pulling && System.currentTimeMillis() < deadline) {
                    delay(100)
                    pulling = current.server.bytesSent.get() > 0
                }
            }
            when (PlayOutcome.decide(result == DlnaResult.Ok, pulling)) {
                PlayOutcome.CONFIRMED -> _state.value = ProjectionState.Streaming(url, target.friendlyName)
                PlayOutcome.TV_IS_PULLING -> {
                    note("el TV no confirmó el Play pero ya descarga el video: se sigue transmitiendo")
                    _state.value = ProjectionState.Streaming(url, target.friendlyName)
                }
                PlayOutcome.FAILED -> {
                    release("el TV rechazó el Play y no pidió el video")
                    fail(DlnaMessages.describe(target.friendlyName, DlnaSoap.ACTION_PLAY, result))
                }
            }
        } catch (e: IOException) {
            cleanup(server, encoder, audio, feeder, reader, null, virtualDisplay, presentation)
            session = null
            fail("No se pudo abrir el servidor de video local (${e.message ?: "error de red"}).")
        } catch (e: RuntimeException) {
            session?.padder?.interrupt()
            cleanup(server, encoder, audio, feeder, reader, null, virtualDisplay, presentation)
            session = null
            fail("No se pudo iniciar la proyección (${e.javaClass.simpleName}: ${e.message ?: "sin detalle"}).")
        }
    }

    /** Mantiene un caudal mínimo constante con paquetes nulos; se detiene al interrumpir el hilo. */
    private fun startPadder(hub: StreamHub, rateBps: Long): Thread = Thread({
        val start = System.nanoTime()
        try {
            while (!Thread.currentThread().isInterrupted) {
                val elapsedMs = (System.nanoTime() - start) / 1_000_000
                val n = TsPadding.packetsNeeded(rateBps, elapsedMs, hub.publishedBytes.get())
                if (n > 0) hub.publish(TsPadding.nullPackets(n), false)
                Thread.sleep(20)
            }
        } catch (e: InterruptedException) {
            // Cierre normal.
        }
    }, "opentv-padding").apply { isDaemon = true; start() }

    private fun snapshot(current: Session, phase: String) = ProjectionStats(
        profileLabel = current.profile.label + " · latencia " + current.latency.label,
        audio = current.audio != null,
        drawnFrames = current.feeder?.drawnFrames?.get() ?: current.encoder.encodedFrames.get(),
        encodedFrames = current.encoder.encodedFrames.get(),
        bytesSent = current.server.bytesSent.get(),
        clients = current.server.activeClients,
        requests = current.server.requests,
        lastRequest = current.server.lastRequest,
        uiVisible = current.feeder?.uiVisible ?: true,
        drawErrors = current.feeder?.errors?.get() ?: 0,
        lastDrawError = current.feeder?.lastError.orEmpty(),
        lastClose = current.server.lastClose,
        history = (log.toList() + current.server.history).sortedBy { it.substringAfter('+').substringBefore(' ').toDoubleOrNull() ?: 0.0 }.takeLast(14),
        buildLabel = BuildInfo.LABEL,
        testPattern = current.testPattern,
        phase = if (current.mirror) "$phase (espejo de pantalla)" else phase,
    )

    private fun startStats(current: Session) {
        val tick = object : Runnable {
            override fun run() {
                if (session !== current) return
                _stats.value = snapshot(current, "en curso")
                mainHandler.postDelayed(this, 1_000L)
            }
        }
        mainHandler.post(tick)
    }

    /** Pide al TV que pare y libera todo. */
    suspend fun stop() {
        val current = session
        if (current != null) {
            // Si el TV no responde igual se libera todo: no se deja nada vivo.
            dlna.stop(current.target)
        }
        release("el usuario detuvo la proyección")
    }

    /** Libera todo sin hablar con el TV; se usa al cerrar la app. */
    fun shutdown() = release("se cerró la app")

    private fun release(reason: String) {
        val current = session
        session = null
        if (current != null) {
            note("OpenTV detiene todo: $reason")
            _stats.value = snapshot(current, "detenida")
            cleanup(current.server, current.encoder, current.audio, current.feeder, current.reader, current.padder, current.virtualDisplay, current.presentation)
        }
        ProjectionService.stop(appContext)
        _state.value = ProjectionState.Idle
    }

    private fun cleanup(
        server: HttpStreamServer?,
        encoder: ScreenEncoder?,
        audio: SilentAudioEncoder?,
        feeder: ViewFrameFeeder?,
        reader: ImageReader?,
        padder: Thread?,
        virtualDisplay: VirtualDisplay?,
        presentation: Presentation?,
    ) {
        runCatching { padder?.interrupt() }
        runCatching { mediaProjection?.stop() }
        mediaProjection = null
        runCatching { feeder?.stop() }
        runCatching { presentation?.dismiss() }
        runCatching { virtualDisplay?.release() }
        runCatching { reader?.close() }
        runCatching { audio?.stop() }
        runCatching { encoder?.stop() }
        runCatching { server?.stop() }
    }

    private fun fail(message: String) {
        ProjectionService.stop(appContext)
        _state.value = ProjectionState.Failed(message)
    }

    private fun isLanClient(address: java.net.InetAddress): Boolean =
        address is Inet4Address && LanAddress.isPrivateIpv4(address.hostAddress.orEmpty())

    private fun randomToken(): String {
        val bytes = ByteArray(12)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val DENSITY_DPI = 240

        /** Espera máxima a que un TV que no confirmó el Play empiece a descargar el video. */
        const val PULL_GRACE_MS = 4_000L

    }
}
