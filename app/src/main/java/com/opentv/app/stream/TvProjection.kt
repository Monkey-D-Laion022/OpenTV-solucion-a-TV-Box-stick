package com.opentv.app.stream

import android.app.Presentation
import android.content.Context
import android.hardware.display.DisplayManager
import android.graphics.PixelFormat
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
        val feeder: ViewFrameFeeder,
        val reader: ImageReader,
        val padder: Thread,
        val virtualDisplay: VirtualDisplay,
        val presentation: Presentation,
        val target: UpnpDevice,
        val profile: StreamProfile,
        val testPattern: Boolean,
    )

    private var session: Session? = null

    /**
     * Arma todo y le pide al TV que reproduzca. [createPresentation] construye la ventana con la
     * interfaz que se verá en el TV, sobre la pantalla virtual que se le entrega.
     */
    suspend fun start(target: UpnpDevice, createPresentation: (Display) -> Presentation) {
        if (_state.value is ProjectionState.Starting || _state.value is ProjectionState.Streaming) return
        _state.value = ProjectionState.Starting
        release("reinicio")

        val ip = LanIp.pick()
        if (ip == null) {
            fail("No hay una red Wi‑Fi local activa. Conectá el teléfono a la misma red que el TV.")
            return
        }

        ProjectionService.start(appContext)
        val profile = _profile.value
        val test = _testPattern.value
        log.clear()
        originMs = System.currentTimeMillis()
        note("inicio · formato «${profile.label}»${if (test) " · imagen de prueba" else ""}")
        val hub = StreamHub()
        val token = randomToken()
        val server = HttpStreamServer(hub, token, acceptClient = ::isLanClient, profile = profile)
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
            val muxer = TsMuxer(withAudio)
            val enc = ScreenEncoder(profile, muxer, originUs) { chunk, keyStart -> hub.publish(chunk, keyStart) }
            encoder = enc
            val encoderSurface = enc.start()
            hub.onSubscribe = { enc.requestKeyFrame() }
            if (withAudio) {
                audio = SilentAudioEncoder(originUs) { adts, pts -> hub.publish(muxer.muxAudio(adts, pts), false) }
                    .also { it.start() }
            }

            // La interfaz se dibuja en una pantalla virtual cuyo resultado no se usa (se descarta); lo que va
            // al TV lo dibujamos nosotros directo sobre el codificador a ritmo fijo, así nunca queda sin cuadros.
            val sink = ImageReader.newInstance(profile.width, profile.height, PixelFormat.RGBA_8888, 2)
            reader = sink
            sink.setOnImageAvailableListener({ r -> runCatching { r.acquireLatestImage()?.close() } }, mainHandler)
            val vd = displayManager.createVirtualDisplay(
                "OpenTV-TV", profile.width, profile.height, DENSITY_DPI, sink.surface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION or
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
            ) ?: throw IllegalStateException("Android no creó la pantalla virtual.")
            virtualDisplay = vd
            val shown = createPresentation(vd.display)
            presentation = shown
            shown.show()

            note("pantalla virtual y ventana listas")
            val feed = ViewFrameFeeder(encoderSurface, profile.width, profile.height, ScreenEncoder.FPS, test) {
                shown.window?.decorView
            }
            feeder = feed
            feed.start()

            val url = "http://${ip.hostAddress}:$port${server.path}"
            val pad = startPadder(hub)
            val current = Session(hub, server, enc, audio, feed, sink, pad, vd, shown, target, profile, test)
            session = current
            startStats(current)

            val metadata = if (profile.sendMetadata) DidlLite.forUrl(url, "OpenTV", profile.protocolInfo()) else null
            note("enviando el enlace al TV (SetAVTransportURI + Play)")
            val result = dlna.play(target, url, metadata)
            note("el TV respondió: ${result::class.simpleName}")
            if (result == DlnaResult.Ok) {
                _state.value = ProjectionState.Streaming(url, target.friendlyName)
            } else {
                release("el TV rechazó el Play")
                fail(DlnaMessages.describe(target.friendlyName, DlnaSoap.ACTION_PLAY, result))
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
    private fun startPadder(hub: StreamHub): Thread = Thread({
        val start = System.nanoTime()
        try {
            while (!Thread.currentThread().isInterrupted) {
                val elapsedMs = (System.nanoTime() - start) / 1_000_000
                val n = TsPadding.packetsNeeded(MIN_RATE_BPS, elapsedMs, hub.publishedBytes.get())
                if (n > 0) hub.publish(TsPadding.nullPackets(n), false)
                Thread.sleep(20)
            }
        } catch (e: InterruptedException) {
            // Cierre normal.
        }
    }, "opentv-padding").apply { isDaemon = true; start() }

    private fun snapshot(current: Session, phase: String) = ProjectionStats(
        profileLabel = current.profile.label,
        audio = current.audio != null,
        drawnFrames = current.feeder.drawnFrames.get(),
        encodedFrames = current.encoder.encodedFrames.get(),
        bytesSent = current.server.bytesSent.get(),
        clients = current.server.activeClients,
        requests = current.server.requests,
        lastRequest = current.server.lastRequest,
        uiVisible = current.feeder.uiVisible,
        drawErrors = current.feeder.errors.get(),
        lastDrawError = current.feeder.lastError,
        lastClose = current.server.lastClose,
        history = (log.toList() + current.server.history).sortedBy { it.substringAfter('+').substringBefore(' ').toDoubleOrNull() ?: 0.0 }.takeLast(14),
        buildLabel = BuildInfo.LABEL,
        testPattern = current.testPattern,
        phase = phase,
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

        /** Caudal mínimo del flujo (video + audio + relleno): 2 Mbps. */
        const val MIN_RATE_BPS = 2_000_000L
    }
}
