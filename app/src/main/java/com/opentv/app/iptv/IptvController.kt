package com.opentv.app.iptv

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import com.opentv.app.stream.LanIp
import com.opentv.app.stream.MirrorRequest
import com.opentv.app.stream.ScreenEncoder
import com.opentv.app.stream.SilentAudioEncoder
import com.opentv.app.stream.StreamHub
import com.opentv.app.stream.StreamProfile
import com.opentv.app.stream.TsMuxer
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface IptvState {
    data object Idle : IptvState
    data object Starting : IptvState
    data class Live(
        val hlsUrl: String,
        val m3uUrl: String,
        val compatM3uUrl: String,
        val tsUrl: String,
        val clients: Int,
        val segments: Long,
        val kbPerSecond: Int,
        val requests: List<String>,
        val preset: IptvPreset,
        val withAudio: Boolean,
    ) : IptvState
    data class Failed(val message: String) : IptvState
}

/**
 * Canal de televisión en vivo generado por el teléfono: captura la pantalla (MediaProjection), la codifica en
 * H.264 (hardware si el teléfono lo ofrece), la empaqueta en MPEG-TS y la publica por HTTP en la red local como
 * HLS (playlist .m3u8 + segmentos) y como TS continuo, junto con una lista M3U lista para cargar en una app IPTV.
 *
 * Es independiente de la proyección DLNA y del espejo nativo: no comparte sesión con ellos ni los modifica. Solo
 * una captura de pantalla puede estar activa a la vez; no lo uses junto con el espejo por navegador.
 * Audio: hoy solo una pista AAC en silencio (muchos reproductores no abren video sin audio). Capturar el audio del
 * teléfono exige el permiso RECORD_AUDIO, que no está autorizado en este proyecto. NO verificado en hardware.
 * Debe llamarse desde el hilo principal.
 */
class IptvController private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow<IptvState>(IptvState.Idle)
    val state: StateFlow<IptvState> = _state.asStateFlow()

    private val _preset = MutableStateFlow(IptvPreset.BALANCED)
    val preset: StateFlow<IptvPreset> = _preset.asStateFlow()

    fun cyclePreset() {
        if (session == null) _preset.value = _preset.value.next()
    }

    private class Session(
        val hub: StreamHub,
        val segmenter: HlsSegmenter,
        val server: IptvServer,
        val encoder: ScreenEncoder,
        val audio: SilentAudioEncoder?,
        val projection: MediaProjection,
        val display: VirtualDisplay,
        val timer: java.util.Timer,
        val preset: IptvPreset,
    )

    private var session: Session? = null

    suspend fun start(request: MirrorRequest) {
        if (session != null || _state.value is IptvState.Starting) return
        _state.value = IptvState.Starting
        val ip = LanIp.pick()
        if (ip == null) {
            _state.value = IptvState.Failed("No hay una red Wi‑Fi local activa. Conectá el teléfono a la misma red que el televisor.")
            return
        }
        val preset = _preset.value
        IptvService.start(appContext)
        val hub = StreamHub(maxQueuedBytes = 1024 * 1024)
        val segmenter = HlsSegmenter(preset.hlsConfig())
        val token = randomToken()
        val startedAt = System.currentTimeMillis()
        var server: IptvServer? = null
        var encoder: ScreenEncoder? = null
        var audio: SilentAudioEncoder? = null
        var projection: MediaProjection? = null
        var display: VirtualDisplay? = null
        try {
            IptvService.awaitReady()
            val hasAudio = SilentAudioEncoder.isAvailable()
            val status = object : IptvStatusSource {
                override val channelName = "OpenTV · pantalla del teléfono"
                override val width = preset.width
                override val height = preset.height
                override val fps = preset.fps
                override val bitrateBps = preset.bitrateBps
                override val withAudio = hasAudio
                override val clients: Int get() = server?.activeClients ?: 0
                override val startedAtMs = startedAt
            }
            val srv = IptvServer(IptvRouter(token, segmenter, status) { System.currentTimeMillis() }, hub, acceptClient = ::isLanClient)
            server = srv
            val port = startServer(srv, ip)

            val originUs = System.nanoTime() / 1000
            val muxer = TsMuxer(hasAudio)
            val profile = if (preset.height >= 1080) StreamProfile.AVC_TS else StreamProfile.HD720
            val sent = AtomicLong(0)
            val enc = ScreenEncoder(profile, muxer, originUs, fps = preset.fps, bitrate = preset.bitrateBps, iFrameSeconds = 1) { chunk, key ->
                sent.addAndGet(chunk.size.toLong())
                segmenter.onChunk(chunk, key, System.currentTimeMillis())
                hub.publish(chunk, key)
            }
            encoder = enc
            val surface = enc.start()
            hub.onSubscribe = { enc.requestKeyFrame() }
            if (hasAudio) {
                audio = SilentAudioEncoder(originUs) { adts, pts ->
                    val ts = muxer.muxAudio(adts, pts)
                    segmenter.onChunk(ts, false, System.currentTimeMillis())
                    hub.publish(ts, false)
                }.also { it.start() }
            }

            val manager = appContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val p = manager.getMediaProjection(request.resultCode, request.data)
                ?: throw IllegalStateException("Android no entregó la captura de pantalla.")
            projection = p
            // Desde Android 14 hay que registrar el callback antes de crear la pantalla virtual.
            p.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { mainHandler.post { stop() } }
            }, mainHandler)
            val vd = p.createVirtualDisplay(
                "OpenTV-IPTV", profile.width, profile.height, appContext.resources.displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, surface, null, null,
            ) ?: throw IllegalStateException("Android no creó la pantalla de captura.")
            display = vd

            val base = "http://${ip.hostAddress}:$port/$token"
            val timer = java.util.Timer("opentv-iptv-stats", true)
            var lastBytes = 0L
            timer.schedule(object : java.util.TimerTask() {
                override fun run() {
                    val bytes = sent.get()
                    val live = IptvState.Live(
                        hlsUrl = "$base/index.m3u8", m3uUrl = "$base/channel.m3u", compatM3uUrl = "$base/compat.m3u",
                        tsUrl = "$base/live.ts", clients = srv.activeClients, segments = segmenter.publishedSegments,
                        kbPerSecond = ((bytes - lastBytes) / 1024).toInt(), requests = srv.history, preset = preset, withAudio = hasAudio,
                    )
                    lastBytes = bytes
                    if (session != null) _state.value = live
                }
            }, 1_000, 1_000)
            session = Session(hub, segmenter, srv, enc, audio, p, vd, timer, preset)
            _state.value = IptvState.Live(
                "$base/index.m3u8", "$base/channel.m3u", "$base/compat.m3u", "$base/live.ts", 0, 0, 0, emptyList(), preset, hasAudio,
            )
        } catch (e: IOException) {
            teardown(server, encoder, audio, display, projection, hub, segmenter, null)
            _state.value = IptvState.Failed("No se pudo abrir el servidor local (${e.message ?: "error de red"}).")
        } catch (e: RuntimeException) {
            teardown(server, encoder, audio, display, projection, hub, segmenter, null)
            _state.value = IptvState.Failed("No se pudo iniciar el canal (${e.javaClass.simpleName}: ${e.message ?: "sin detalle"}).")
        }
    }

    fun stop() {
        val s = session
        session = null
        if (s != null) teardown(s.server, s.encoder, s.audio, s.display, s.projection, s.hub, s.segmenter, s.timer)
        IptvService.stop(appContext)
        if (_state.value !is IptvState.Failed) _state.value = IptvState.Idle
    }

    private fun startServer(server: IptvServer, ip: InetAddress): Int {
        for (port in IptvPorts.PREFERRED) {
            try { return server.start(ip, port) } catch (e: IOException) { /* ocupado: siguiente */ }
        }
        return server.start(ip, 0)
    }

    private fun teardown(
        server: IptvServer?, encoder: ScreenEncoder?, audio: SilentAudioEncoder?, display: VirtualDisplay?,
        projection: MediaProjection?, hub: StreamHub, segmenter: HlsSegmenter, timer: java.util.Timer?,
    ) {
        timer?.cancel()
        runCatching { segmenter.end() }
        runCatching { display?.release() }
        runCatching { audio?.stop() }
        runCatching { encoder?.stop() }
        runCatching { projection?.stop() }
        runCatching { hub.closeAll() }
        runCatching { server?.stop() }
    }

    private fun isLanClient(address: InetAddress): Boolean =
        address is Inet4Address && (address.isSiteLocalAddress || address.isLoopbackAddress)

    private fun randomToken(): String {
        val bytes = ByteArray(8)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        @Volatile private var instance: IptvController? = null

        fun get(context: Context): IptvController =
            instance ?: synchronized(this) { instance ?: IptvController(context).also { instance = it } }
    }
}
