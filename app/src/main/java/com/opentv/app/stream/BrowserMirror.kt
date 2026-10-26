package com.opentv.app.stream

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetAddress
import java.security.SecureRandom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface BrowserMirrorState {
    data object Idle : BrowserMirrorState
    data object Starting : BrowserMirrorState
    data class Serving(
        val url: String,
        val slowUrl: String,
        val clients: Int,
        val framesPerSecond: Int,
        val kbPerSecond: Int,
        /** Últimos pedidos que llegaron al servidor: si está vacío, el TV nunca llegó hasta el teléfono. */
        val requests: List<String> = emptyList(),
    ) : BrowserMirrorState
    data class Failed(val message: String) : BrowserMirrorState
}

/**
 * Espejo de pantalla por navegador: captura la pantalla con MediaProjection, comprime cada cuadro a JPEG y lo sirve
 * por HTTP en la red local (MJPEG) para el navegador del TV. Es la alternativa universal a Miracast: funciona con
 * cualquier teléfono con Wi‑Fi y no depende del búfer de un reproductor DLNA. NO verificado en hardware.
 * Debe llamarse desde el hilo principal.
 */
class BrowserMirror(context: Context) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow<BrowserMirrorState>(BrowserMirrorState.Idle)
    val state: StateFlow<BrowserMirrorState> = _state.asStateFlow()

    private val _quality = MutableStateFlow(MjpegQuality.MEDIUM)
    val quality: StateFlow<MjpegQuality> = _quality.asStateFlow()

    fun cycleQuality() {
        if (session == null) _quality.value = _quality.value.next()
    }

    private val _shortUrl = MutableStateFlow(true)

    /** URL corta (`http://IP:8080`, sin código) o con código aleatorio (más privada). */
    val shortUrl: StateFlow<Boolean> = _shortUrl.asStateFlow()

    fun toggleShortUrl() {
        if (session == null) _shortUrl.value = !_shortUrl.value
    }

    private class Session(
        val hub: MjpegHub,
        val server: MjpegHttpServer,
        val projection: MediaProjection,
        val display: VirtualDisplay,
        val reader: ImageReader,
        val thread: HandlerThread,
        val statsTimer: java.util.Timer,
    )

    private var session: Session? = null

    /** Arma la captura y el servidor. Hay que esperar el servicio en primer plano antes (lo hace esta función). */
    suspend fun start(mirror: MirrorRequest) {
        if (session != null || _state.value is BrowserMirrorState.Starting) return
        _state.value = BrowserMirrorState.Starting
        val ip = LanIp.pick()
        if (ip == null) {
            _state.value = BrowserMirrorState.Failed("No hay una red Wi‑Fi local activa. Conectá el teléfono a la misma red que el TV.")
            return
        }
        ProjectionService.start(appContext, mirror = true)
        val hub = MjpegHub()
        val short = _shortUrl.value
        val server = MjpegHttpServer(hub, if (short) "" else randomToken(), acceptClient = ::isLanClient)
        var projection: MediaProjection? = null
        var display: VirtualDisplay? = null
        var reader: ImageReader? = null
        val thread = HandlerThread("opentv-mjpeg-capture").apply { start() }
        try {
            ProjectionService.awaitReady()
            val port = server.start(ip, PREFERRED_PORTS)
            val manager = appContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val p = manager.getMediaProjection(mirror.resultCode, mirror.data)
                ?: throw IllegalStateException("Android no entregó la captura de pantalla.")
            projection = p
            // Desde Android 14 hay que registrar el callback antes de crear la pantalla virtual.
            p.registerCallback(
                object : MediaProjection.Callback() {
                    override fun onStop() {
                        mainHandler.post { stop() }
                    }
                },
                mainHandler,
            )
            val q = _quality.value
            val metrics = appContext.resources.displayMetrics
            val (w, h) = q.captureSize(metrics.widthPixels, metrics.heightPixels)
            val imageReader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
            reader = imageReader
            val encoder = FrameEncoder(imageReader, hub, q)
            imageReader.setOnImageAvailableListener({ encoder.onImage() }, Handler(thread.looper))
            val vd = p.createVirtualDisplay(
                "OpenTV-Navegador", w, h, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader.surface, null, null,
            ) ?: throw IllegalStateException("Android no creó la pantalla de captura.")
            display = vd

            val root = "http://${ip.hostAddress}:$port"
            val base = if (server.token.isEmpty()) root else "$root/${server.token}"
            val home = if (server.token.isEmpty()) root else "$base/"
            val slow = if (server.token.isEmpty()) "$root/l" else "$base/lento"
            val timer = java.util.Timer("opentv-mjpeg-stats", true)
            var lastFrames = 0L
            var lastBytes = 0L
            timer.schedule(object : java.util.TimerTask() {
                override fun run() {
                    val frames = server.framesSent.get()
                    val bytes = server.bytesSent.get()
                    val s = BrowserMirrorState.Serving(
                        home, slow, server.activeClients,
                        (frames - lastFrames).toInt(), ((bytes - lastBytes) / 1024).toInt(), server.history,
                    )
                    lastFrames = frames; lastBytes = bytes
                    if (session != null) _state.value = s
                }
            }, 1_000, 1_000)
            session = Session(hub, server, p, vd, imageReader, thread, timer)
            _state.value = BrowserMirrorState.Serving(home, slow, 0, 0, 0)
        } catch (e: IOException) {
            teardown(server, display, reader, projection, thread, null)
            _state.value = BrowserMirrorState.Failed("No se pudo abrir el servidor local (${e.message ?: "error de red"}).")
        } catch (e: RuntimeException) {
            teardown(server, display, reader, projection, thread, null)
            _state.value = BrowserMirrorState.Failed("No se pudo iniciar el espejo (${e.javaClass.simpleName}: ${e.message ?: "sin detalle"}).")
        }
    }

    fun stop() {
        val s = session
        session = null
        if (s != null) teardown(s.server, s.display, s.reader, s.projection, s.thread, s.statsTimer)
        ProjectionService.stop(appContext)
        if (_state.value !is BrowserMirrorState.Failed) _state.value = BrowserMirrorState.Idle
    }

    fun shutdown() = stop()

    private fun teardown(
        server: MjpegHttpServer,
        display: VirtualDisplay?,
        reader: ImageReader?,
        projection: MediaProjection?,
        thread: HandlerThread,
        timer: java.util.Timer?,
    ) {
        timer?.cancel()
        runCatching { server.stop() }
        runCatching { display?.release() }
        runCatching { reader?.setOnImageAvailableListener(null, null); reader?.close() }
        runCatching { projection?.stop() }
        thread.quitSafely()
    }

    private companion object {
        /** Puertos fijos y fáciles de teclear, en orden; si todos están ocupados se usa uno al azar. */
        val PREFERRED_PORTS = listOf(8080, 8081, 8888)
    }

    private fun isLanClient(address: InetAddress): Boolean =
        address is java.net.Inet4Address && (address.isSiteLocalAddress || address.isLoopbackAddress)

    private fun randomToken(): String {
        val bytes = ByteArray(6)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** Convierte cada imagen capturada en JPEG, a lo sumo [MjpegQuality.fps] por segundo; se queda con la más nueva. */
    private class FrameEncoder(private val reader: ImageReader, private val hub: MjpegHub, private val quality: MjpegQuality) {
        private val minIntervalMs = 1000L / quality.fps
        private var lastMs = 0L
        private val out = ByteArrayOutputStream(256 * 1024)
        private var bitmap: Bitmap? = null

        fun onImage() {
            val image = try {
                reader.acquireLatestImage()
            } catch (e: RuntimeException) {
                null
            } ?: return
            try {
                val now = System.currentTimeMillis()
                if (now - lastMs < minIntervalMs) return // se descarta: no se acumula nada
                lastMs = now
                val plane = image.planes[0]
                val pixelStride = plane.pixelStride
                val rowPadding = plane.rowStride - pixelStride * image.width
                val fullWidth = image.width + rowPadding / pixelStride
                var bmp = bitmap
                if (bmp == null || bmp.width != fullWidth || bmp.height != image.height) {
                    bmp?.recycle()
                    bmp = Bitmap.createBitmap(fullWidth, image.height, Bitmap.Config.ARGB_8888)
                    bitmap = bmp
                }
                plane.buffer.rewind()
                bmp.copyPixelsFromBuffer(plane.buffer)
                out.reset()
                // El ancho extra por relleno de filas queda fuera: se comprime solo el área visible.
                val visible = if (fullWidth == image.width) bmp else Bitmap.createBitmap(bmp, 0, 0, image.width, image.height)
                visible.compress(Bitmap.CompressFormat.JPEG, quality.jpegQuality, out)
                if (visible !== bmp) visible.recycle()
                hub.publish(out.toByteArray())
            } catch (e: RuntimeException) {
                // Un cuadro que falla se salta; el siguiente sigue.
            } finally {
                image.close()
            }
        }
    }
}
