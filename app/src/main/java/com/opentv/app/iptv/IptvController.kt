package com.opentv.app.iptv

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.opentv.app.stream.LanIp
import com.opentv.app.stream.MirrorRequest
import com.opentv.app.stream.StreamHub
import com.opentv.app.transmission.TransmissionInfo
import com.opentv.app.transmission.TransmissionRegistry
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.security.SecureRandom
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
        val audio: IptvAudio = IptvAudio.SILENT_TRACK,
        val note: String? = null,
        val externalRequests: Int = 0,
        val externalClients: Int = 0,
        val lastExternal: String = "",
        val encodedFps: Double = 0.0,
        val producedKbps: Int = 0,
        val deliveredKbps: Int = 0,
        val diag: List<String> = emptyList(),
        val fpsWarning: String? = null,
        val codec: String = "",
        val mp4Url: String = "",
        /** Página `http://IP:puerto/` para navegadores de TV (solo con dirección corta). */
        val pageUrl: String? = null,
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
 * Audio: si el usuario concedió RECORD_AUDIO se captura el audio que reproduce el teléfono (Android 10+); si no, o si
 * Android lo rechaza, se emite una pista AAC en silencio (muchos reproductores no abren video sin audio) y la
 * pantalla lo dice. Solo se informa «con sonido» si se midieron muestras no nulas. NO verificado en hardware.
 * Debe llamarse desde el hilo principal.
 */
class IptvController private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val prefs = appContext.getSharedPreferences("opentv_iptv", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow<IptvState>(IptvState.Idle)
    val state: StateFlow<IptvState> = _state.asStateFlow()

    private val _preset = MutableStateFlow(IptvPreset.BALANCED)
    val preset: StateFlow<IptvPreset> = _preset.asStateFlow()

    fun cyclePreset() {
        if (session == null) _preset.value = _preset.value.next()
    }

    private val _access = MutableStateFlow(IptvAccess.SHORT)
    val access: StateFlow<IptvAccess> = _access.asStateFlow()

    fun cycleAccess() {
        if (session == null) _access.value = _access.value.next()
    }

    /** Automático: usa el mejor perfil que la medición del teléfono demostró sostener (1080p60 → 720p60 → menores). */
    private val _auto = MutableStateFlow(true)
    val auto: StateFlow<Boolean> = _auto.asStateFlow()

    fun toggleAuto() {
        if (session == null) _auto.value = !_auto.value
    }

    private val _calibration = MutableStateFlow(loadCalibration())
    val calibration: StateFlow<List<String>> = _calibration.asStateFlow()
    private val _measuring = MutableStateFlow(false)
    val measuring: StateFlow<Boolean> = _measuring.asStateFlow()

    private class Session(
        val pipeline: CapturePipeline,
        val server: IptvServer,
        val hub: StreamHub,
        val segmenter: HlsSegmenter,
        val mp4: Mp4Feed,
        val timer: java.util.Timer,
    )

    private var session: Session? = null

    /** Mide, con codificación real, qué perfiles sostiene el teléfono y guarda el resultado. Bloquea: llamar fuera del hilo principal. */
    fun measure(): List<PerfResult> {
        if (session != null || _measuring.value) return emptyList()
        _measuring.value = true
        try {
            val results = ArrayList<PerfResult>()
            for (p in PerfAdvisor.sequence) {
                val r = try { EncoderBenchmark.run(p) } catch (e: RuntimeException) { null } ?: continue
                results.add(r)
                if (r.sustained) break // el primero sostenido es el mejor: no hace falta seguir
            }
            saveCalibration(results)
            _calibration.value = loadCalibration()
            return results
        } finally {
            _measuring.value = false
        }
    }

    private fun saveCalibration(results: List<PerfResult>) {
        prefs.edit().putString("calibration", results.joinToString(";") { "${it.preset.name},${it.seconds},${it.encodedFps},${it.drawnFps}" }).apply()
    }

    private fun loadResults(): List<PerfResult> =
        (prefs.getString("calibration", "") ?: "").split(';').mapNotNull { row ->
            val f = row.split(',')
            val p = IptvPreset.values().firstOrNull { it.name == f.getOrNull(0) } ?: return@mapNotNull null
            val sec = f.getOrNull(1)?.toDoubleOrNull() ?: return@mapNotNull null
            val enc = f.getOrNull(2)?.toDoubleOrNull() ?: return@mapNotNull null
            val drawn = f.getOrNull(3)?.toDoubleOrNull() ?: return@mapNotNull null
            PerfResult(p, sec, enc, drawn)
        }

    private fun loadCalibration(): List<String> = loadResults().map { PerfAdvisor.describe(it) }

    suspend fun start(request: MirrorRequest, captureAudio: Boolean = false) {
        if (session != null || _state.value is IptvState.Starting) return
        _state.value = IptvState.Starting
        val ip = LanIp.pick()
        if (ip == null) {
            _state.value = IptvState.Failed("No hay una red Wi‑Fi local activa. Conectá el teléfono a la misma red que el televisor.")
            return
        }
        val results = loadResults()
        val requested = if (_auto.value) (PerfAdvisor.best(results) ?: _preset.value) else _preset.value
        val choice = IptvProfilePicker.pick(requested) { w, h, f -> EncoderProbe.supports(w, h, f) }
        val preset = choice.preset
        IptvService.start(appContext)
        val hub = StreamHub(maxQueuedBytes = 1024 * 1024)
        val segmenter = HlsSegmenter(preset.hlsConfig())
        val mp4 = Mp4Feed()
        val token = _access.value.fixedToken ?: randomToken()
        val startedAt = System.currentTimeMillis()
        var server: IptvServer? = null
        var pipeline: CapturePipeline? = null
        try {
            IptvService.awaitReady()
            val status = object : IptvStatusSource {
                override val channelName = "OpenTV · pantalla del teléfono"
                override val width = preset.width
                override val height = preset.height
                override val fps = preset.fps
                override val bitrateBps = preset.bitrateBps
                override val withAudio = true
                override val clients: Int get() = server?.activeClients ?: 0
                override val startedAtMs = startedAt
            }
            val router = IptvRouter(token, segmenter, status) { System.currentTimeMillis() }
            val srv = IptvServer(router, hub, acceptClient = ::isLanClient, ownAddress = ip, mp4 = mp4)
            server = srv
            val port = startServer(srv, ip)

            val pipe = CapturePipeline(
                appContext, request, preset, captureAudio,
                object : PipelineSink {
                    override fun onTs(chunk: ByteArray, keyStart: Boolean) {
                        segmenter.onChunk(chunk, keyStart, System.currentTimeMillis())
                        hub.publish(chunk, keyStart)
                    }
                    override fun onMp4Init(init: ByteArray) = mp4.onInit(init)
                    override fun onMp4Fragment(fragment: ByteArray) = mp4.onFragment(fragment)
                },
                onProjectionStopped = { stop() },
            )
            pipeline = pipe
            pipe.start()
            hub.onSubscribe = { pipe.requestKeyFrame() }
            mp4.hub.onSubscribe = { pipe.requestKeyFrame() }

            val note = listOfNotNull(choice.note, pipe.note).joinToString(" ").ifBlank { null }
            val base = "http://${ip.hostAddress}:$port/$token"
            val pageUrl = if (token == IptvAccess.SHORT.fixedToken) "http://${ip.hostAddress}:$port/" else null
            val timer = java.util.Timer("opentv-iptv-stats", true)
            val encRate = RateMeter(); val sentRate = RateMeter(); val servedRate = RateMeter()
            val diagLog = java.util.concurrent.ConcurrentLinkedDeque<String>()
            router.onDiag = { line -> diagLog.addLast(line); while (diagLog.size > 6) diagLog.pollFirst() }
            val aspect = appContext.resources.displayMetrics.let { it.widthPixels.toDouble() / it.heightPixels.coerceAtLeast(1) }
            timer.schedule(object : java.util.TimerTask() {
                override fun run() {
                    val now = System.currentTimeMillis()
                    val encFps = encRate.sample(pipe.encodedFrames, now)
                    val producedKbps = (sentRate.sample(pipe.producedBytes, now) * 8 / 1000).toInt()
                    val deliveredKbps = (servedRate.sample(srv.bytesSent.get(), now) * 8 / 1000).toInt()
                    val audio = pipe.audioState(now)
                    val live = IptvState.Live(
                        hlsUrl = "$base/index.m3u8", m3uUrl = "$base/channel.m3u", compatM3uUrl = "$base/compat.m3u",
                        tsUrl = "$base/live.ts", clients = srv.activeClients, segments = segmenter.publishedSegments,
                        kbPerSecond = producedKbps / 8, requests = srv.history, preset = preset, withAudio = pipe.hasAudioTrack,
                        audio = audio, note = note,
                        externalRequests = srv.externalRequests, externalClients = srv.externalClientIps.size, lastExternal = srv.lastExternal,
                        encodedFps = encFps, producedKbps = producedKbps, deliveredKbps = deliveredKbps, diag = diagLog.toList(),
                        fpsWarning = FpsVerdict.describe(preset.fps, encFps, (now - startedAt) / 1000),
                        codec = pipe.codecName, mp4Url = "$base/live.mp4", pageUrl = pageUrl,
                    )
                    if (session != null) _state.value = live
                    TransmissionRegistry.publish(
                        TransmissionInfo(ID, "IPTV", preset.width, preset.height, preset.fps, encFps, producedKbps, audio.label, aspect),
                    ) { mainHandler.post { stop() } }
                }
            }, 1_000, 1_000)
            session = Session(pipe, srv, hub, segmenter, mp4, timer)
            TransmissionRegistry.publish(
                TransmissionInfo(ID, "IPTV", preset.width, preset.height, preset.fps, null, null, "midiendo…", aspect),
            ) { mainHandler.post { stop() } }
            _state.value = IptvState.Live(
                "$base/index.m3u8", "$base/channel.m3u", "$base/compat.m3u", "$base/live.ts", 0, 0, 0, emptyList(), preset, pipe.hasAudioTrack,
                audio = pipe.audioState(System.currentTimeMillis()), note = note, codec = pipe.codecName, mp4Url = "$base/live.mp4",
                pageUrl = pageUrl,
            )
        } catch (e: IOException) {
            teardown(server, pipeline, hub, segmenter, mp4, null)
            _state.value = IptvState.Failed("No se pudo abrir el servidor local (${e.message ?: "error de red"}).")
        } catch (e: RuntimeException) {
            teardown(server, pipeline, hub, segmenter, mp4, null)
            _state.value = IptvState.Failed("No se pudo iniciar el canal (${e.javaClass.simpleName}: ${e.message ?: "sin detalle"}).")
        }
    }

    fun stop() {
        val s = session
        session = null
        if (s != null) teardown(s.server, s.pipeline, s.hub, s.segmenter, s.mp4, s.timer)
        TransmissionRegistry.remove(ID)
        IptvService.stopIfIdle(appContext)
        if (_state.value !is IptvState.Failed) _state.value = IptvState.Idle
    }

    private fun startServer(server: IptvServer, ip: InetAddress): Int {
        for (port in IptvPorts.PREFERRED) {
            try { return server.start(ip, port) } catch (e: IOException) { /* ocupado: siguiente */ }
        }
        return server.start(ip, 0)
    }

    private fun teardown(
        server: IptvServer?, pipeline: CapturePipeline?, hub: StreamHub, segmenter: HlsSegmenter, mp4: Mp4Feed, timer: java.util.Timer?,
    ) {
        timer?.cancel()
        runCatching { segmenter.end() }
        runCatching { pipeline?.stop() }
        runCatching { hub.closeAll() }
        runCatching { mp4.close() }
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
        private const val ID = "iptv"

        @Volatile private var instance: IptvController? = null

        fun get(context: Context): IptvController =
            instance ?: synchronized(this) { instance ?: IptvController(context).also { instance = it } }
    }
}
