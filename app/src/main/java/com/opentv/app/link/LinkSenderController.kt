package com.opentv.app.link

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.opentv.app.iptv.CapturePipeline
import com.opentv.app.iptv.IptvPreset
import com.opentv.app.iptv.IptvService
import com.opentv.app.iptv.PipelineSink
import com.opentv.app.iptv.RateMeter
import com.opentv.app.proto.OtBitrateController
import com.opentv.app.proto.OtChannel
import com.opentv.app.proto.OtControl
import com.opentv.app.proto.OtDiscovery
import com.opentv.app.proto.OtError
import com.opentv.app.proto.OtFrame
import com.opentv.app.proto.OtHandshake
import com.opentv.app.proto.OtOffer
import com.opentv.app.proto.OtAccept
import com.opentv.app.proto.OtProtocolException
import com.opentv.app.proto.OtStats
import com.opentv.app.proto.OtWire
import com.opentv.app.stream.LanIp
import com.opentv.app.stream.MirrorRequest
import com.opentv.app.transmission.TransmissionInfo
import com.opentv.app.transmission.TransmissionRegistry
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface LinkSenderState {
    data object Idle : LinkSenderState
    data object Starting : LinkSenderState
    /** Esperando a un receptor. [address] sirve para escribirla a mano en el receptor. */
    data class Waiting(val address: String, val announce: String, val paired: List<String>) : LinkSenderState
    /** El emparejamiento pide confirmar que el código es el mismo en las dos pantallas. */
    data class ConfirmCode(val code: String, val peerName: String) : LinkSenderState
    data class Streaming(
        val peerName: String, val address: String, val preset: IptvPreset, val encodedFps: Double, val producedKbps: Int,
        val currentBitrateKbps: Int, val receiverBufferMs: Int, val receiverDropped: Long, val queuedKb: Int, val audio: String, val codec: String,
        val encrypted: Boolean = true, val note: String? = null,
    ) : LinkSenderState
    data class Failed(val message: String) : LinkSenderState
}

/**
 * Emisor nativo OpenTV Link: captura la pantalla con [CapturePipeline], escucha por TCP y envía el MPEG‑TS cifrado
 * (AES‑256‑GCM) al receptor emparejado, con control de bitrate según lo que informa el receptor. Solo atiende a un
 * receptor a la vez. NO verificado en hardware ni en red real (sí el protocolo contra el receptor de referencia en Python).
 */
class LinkSenderController private constructor(context: Context) {
    private val app = context.applicationContext
    private val store = LinkStore(app)
    private val nsd = LinkNsd(app)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow<LinkSenderState>(LinkSenderState.Idle)
    val state: StateFlow<LinkSenderState> = _state.asStateFlow()

    private val _preset = MutableStateFlow(IptvPreset.BALANCED)
    val preset: StateFlow<IptvPreset> = _preset.asStateFlow()
    fun cyclePreset() { if (pipeline == null) _preset.value = _preset.value.next() }

    val peers: List<LinkPeer> get() = store.peers()
    fun forget(idHex: String) = store.forget(idHex)

    @Volatile private var pipeline: CapturePipeline? = null
    @Volatile private var server: ServerSocket? = null
    @Volatile private var active: Active? = null
    @Volatile private var running = false
    private var timer: java.util.Timer? = null
    private var sasFuture: CompletableFuture<Boolean>? = null
    private var address = ""
    private var announce = ""

    private class Active(val socket: Socket, val queue: LinkOutQueue, val peerName: String, val bitrate: OtBitrateController) {
        @Volatile var bufferMs = 0
        @Volatile var dropped = 0L
        @Volatile var closed = false
    }

    /** El usuario compara el código en las dos pantallas y responde. */
    fun confirmCode(ok: Boolean) { sasFuture?.complete(ok) }

    suspend fun start(request: MirrorRequest, captureAudio: Boolean) {
        if (running || _state.value is LinkSenderState.Starting) return
        _state.value = LinkSenderState.Starting
        val ip = LanIp.pick()
        if (ip == null) {
            _state.value = LinkSenderState.Failed("No hay una red Wi‑Fi local activa. Conectá el teléfono a la misma red que el receptor.")
            return
        }
        val preset = _preset.value
        IptvService.start(app)
        var ss: ServerSocket? = null
        var pipe: CapturePipeline? = null
        try {
            IptvService.awaitReady()
            ss = bind(ip)
            server = ss
            val sink = object : PipelineSink {
                override fun onTs(chunk: ByteArray, keyStart: Boolean) {
                    val a = active ?: return
                    if (!a.queue.offer(chunk, keyStart)) pipeline?.requestKeyFrame()
                }
            }
            pipe = CapturePipeline(app, request, preset, captureAudio, sink, onProjectionStopped = { stop() })
            pipe.start()
            pipeline = pipe
            running = true
            address = "${ip.hostAddress}:${ss.localPort}"
            val id = store.identity(store.senderCaps())
            nsd.register(OtDiscovery.Advert(LinkStore.hex(id.deviceId), id.name, OtWire.ROLE_SENDER, id.caps, ss.localPort)) { announce = it; refreshWaiting() }
            Thread({ acceptLoop(ss, pipe) }, "opentv-link-accept").apply { isDaemon = true; start() }
            startStats(pipe, preset)
            _state.value = waiting()
        } catch (e: IOException) {
            cleanup(ss, pipe)
            _state.value = LinkSenderState.Failed("No se pudo abrir el puerto de OpenTV Link (${e.message ?: "error de red"}).")
        } catch (e: RuntimeException) {
            cleanup(ss, pipe)
            _state.value = LinkSenderState.Failed("No se pudo iniciar el emisor (${e.javaClass.simpleName}: ${e.message ?: "sin detalle"}).")
        }
    }

    private fun waiting() = LinkSenderState.Waiting(
        address, announce + (lastIssue?.let { " Último problema: $it" } ?: ""), store.peers().map { it.name },
    )

    private fun refreshWaiting() {
        if (running && active == null && _state.value is LinkSenderState.Waiting) _state.value = waiting()
    }

    private fun bind(ip: InetAddress): ServerSocket {
        val s = ServerSocket()
        s.reuseAddress = true
        try {
            s.bind(InetSocketAddress(ip, OtDiscovery.DEFAULT_PORT), 4)
        } catch (e: IOException) {
            s.close()
            return ServerSocket().also { it.reuseAddress = true; it.bind(InetSocketAddress(ip, 0), 4) }
        }
        return s
    }

    fun stop() {
        val a = active
        if (a != null) runCatching { a.socket.close() }
        cleanup(server, pipeline)
        TransmissionRegistry.remove(ID)
        IptvService.stopIfIdle(app)
        if (_state.value !is LinkSenderState.Failed) _state.value = LinkSenderState.Idle
    }

    private fun cleanup(ss: ServerSocket?, pipe: CapturePipeline?) {
        running = false
        timer?.cancel(); timer = null
        nsd.unregister()
        runCatching { ss?.close() }
        runCatching { pipe?.stop() }
        server = null; pipeline = null; active = null
        sasFuture?.complete(false)
    }

    private fun acceptLoop(ss: ServerSocket, pipe: CapturePipeline) {
        while (running && !ss.isClosed) {
            val socket = try { ss.accept() } catch (e: IOException) { return }
            if (active != null || _state.value is LinkSenderState.ConfirmCode) {
                refuse(socket)
                continue
            }
            Thread({ serve(socket, pipe) }, "opentv-link-session").apply { isDaemon = true; start() }
        }
    }

    private fun refuse(socket: Socket) {
        runCatching {
            val ch = OtChannel(socket.getInputStream(), socket.getOutputStream())
            ch.write(OtFrame(OtWire.Type.ERROR, 0, OtError(OtWire.ErrorCode.BUSY, "El emisor ya atiende a otro receptor").encode()))
        }
        runCatching { socket.close() }
    }

    private fun serve(socket: Socket, pipe: CapturePipeline) {
        var session: com.opentv.app.proto.OtSecureSession? = null
        try {
            socket.tcpNoDelay = true
            socket.soTimeout = 90_000 // el saludo y la confirmación del código pueden tardar
            val channel = OtChannel(socket.getInputStream(), socket.getOutputStream())
            val me = store.identity(store.senderCaps())
            val s = OtHandshake.asSender(channel, me, store) { code -> askCode(code) }
            session = s
            val preset = pipe.preset
            s.send(OtWire.Type.OFFER, 0, OtOffer(1, preset.width, preset.height, preset.fps, preset.bitrateBps.toLong(), if (pipe.hasAudioTrack) 1 else 0, 1).encode())
            val answer = s.receive() ?: throw IOException("El receptor cerró antes de aceptar")
            if (answer.type != OtWire.Type.ACCEPT) throw OtProtocolException("se esperaba ACCEPT")
            val accept = OtAccept.decode(answer.payload)
            if (!accept.accepted) throw IOException("El receptor no aceptó el formato")
            socket.soTimeout = 15_000 // el receptor informa cada segundo: si calla 15 s, se da por perdido
            val a = Active(socket, LinkOutQueue(), s.peer.name, OtBitrateController(preset.bitrateBps.toLong()))
            active = a
            pipe.requestKeyFrame()
            Thread({ readLoop(s, a, pipe) }, "opentv-link-read").apply { isDaemon = true; start() }
            writeLoop(s, a)
        } catch (e: OtProtocolException) {
            session?.sendError(OtWire.ErrorCode.AUTH_FAILED, e.message ?: "error")
            noteFailure("Conexión rechazada: ${e.message}")
        } catch (e: SocketTimeoutException) {
            noteFailure("El receptor dejó de responder.")
        } catch (e: IOException) {
            noteFailure(e.message ?: "conexión cortada")
        } finally {
            active?.closed = true
            active = null
            runCatching { socket.close() }
            if (running) mainHandler.post { if (running) _state.value = waiting() }
        }
    }

    private fun noteFailure(text: String) { lastIssue = text }
    @Volatile private var lastIssue: String? = null

    private fun askCode(code: String): Boolean {
        val future = CompletableFuture<Boolean>()
        sasFuture = future
        _state.value = LinkSenderState.ConfirmCode(code, "receptor")
        return try {
            future.get(90, TimeUnit.SECONDS)
        } catch (e: Exception) {
            false
        } finally {
            sasFuture = null
        }
    }

    private fun writeLoop(s: com.opentv.app.proto.OtSecureSession, a: Active) {
        while (running && !a.closed) {
            val item = a.queue.poll(500) ?: continue
            s.send(OtWire.Type.TS_DATA, if (item.keyStart) OtWire.FLAG_KEY_START else 0, item.data)
        }
    }

    private fun readLoop(s: com.opentv.app.proto.OtSecureSession, a: Active, pipe: CapturePipeline) {
        try {
            while (running && !a.closed) {
                val f = s.receive() ?: break
                when (f.type) {
                    OtWire.Type.STATS -> {
                        val st = OtStats.decode(f.payload)
                        a.bufferMs = st.bufferMs
                        a.dropped = st.dropped
                        a.bitrate.onStats(st)?.let { bps -> pipe.setBitrate(bps.toInt()) }
                    }
                    OtWire.Type.PING -> s.send(OtWire.Type.PONG, 0, f.payload)
                    OtWire.Type.CONTROL -> {
                        val c = OtControl.decode(f.payload)
                        when (c.command) {
                            OtWire.Control.REQUEST_KEYFRAME -> pipe.requestKeyFrame()
                            OtWire.Control.SET_BITRATE -> pipe.setBitrate(c.argument.toInt().coerceIn(200_000, pipe.preset.bitrateBps))
                            OtWire.Control.STOP -> a.closed = true
                        }
                    }
                    OtWire.Type.BYE -> a.closed = true
                }
            }
        } catch (e: IOException) {
            // cierre del receptor
        } catch (e: OtProtocolException) {
            // trama inválida: se corta
        } finally {
            a.closed = true
            runCatching { a.socket.close() }
        }
    }

    private fun startStats(pipe: CapturePipeline, preset: IptvPreset) {
        val encRate = RateMeter(); val sentRate = RateMeter()
        val aspect = app.resources.displayMetrics.let { it.widthPixels.toDouble() / it.heightPixels.coerceAtLeast(1) }
        val t = java.util.Timer("opentv-link-stats", true)
        t.schedule(object : java.util.TimerTask() {
            override fun run() {
                val now = System.currentTimeMillis()
                val enc = encRate.sample(pipe.encodedFrames, now)
                val kbps = (sentRate.sample(pipe.producedBytes, now) * 8 / 1000).toInt()
                val audio = pipe.audioState(now).label
                val a = active
                TransmissionRegistry.publish(
                    TransmissionInfo(ID, "OpenTV Link", preset.width, preset.height, preset.fps, enc, kbps, audio, aspect,
                        detail = if (a == null) "esperando receptor" else "enviando a ${a.peerName}"),
                ) { mainHandler.post { stop() } }
                if (a != null && !a.closed && running) {
                    _state.value = LinkSenderState.Streaming(
                        a.peerName, address, preset, enc, kbps, (a.bitrate.currentBps / 1000).toInt(), a.bufferMs, a.dropped,
                        a.queue.queuedBytes / 1024, audio, pipe.codecName, note = pipe.note ?: lastIssue,
                    )
                }
            }
        }, 1_000, 1_000)
        timer = t
    }

    companion object {
        private const val ID = "link"
        @Volatile private var instance: LinkSenderController? = null
        fun get(context: Context): LinkSenderController =
            instance ?: synchronized(this) { instance ?: LinkSenderController(context).also { instance = it } }
    }
}
