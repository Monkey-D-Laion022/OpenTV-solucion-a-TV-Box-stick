package com.opentv.app.link

import android.content.Context
import com.opentv.app.proto.OtAccept
import com.opentv.app.proto.OtChannel
import com.opentv.app.proto.OtControl
import com.opentv.app.proto.OtHandshake
import com.opentv.app.proto.OtOffer
import com.opentv.app.proto.OtProtocolException
import com.opentv.app.proto.OtSecureSession
import com.opentv.app.proto.OtStats
import com.opentv.app.proto.OtWire
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface LinkReceiverState {
    data object Idle : LinkReceiverState
    data class Connecting(val host: String, val attempt: Int) : LinkReceiverState
    data class ConfirmCode(val code: String) : LinkReceiverState
    data class Receiving(val senderName: String, val offer: OtOffer, val kbps: Int, val overflows: Long) : LinkReceiverState
    data class Failed(val message: String) : LinkReceiverState
}

/**
 * Receptor nativo OpenTV Link: se conecta al emisor, se empareja (código de 6 dígitos) o se reautentica con el secreto
 * guardado, acepta la oferta y entrega el MPEG‑TS descifrado a [source], que lee el reproductor. Si la conexión se corta
 * se reconecta sola (hasta [MAX_ATTEMPTS] intentos) con el secreto ya guardado. NO verificado en hardware.
 */
class LinkReceiverController private constructor(context: Context) {
    private val app = context.applicationContext
    private val store = LinkStore(app)

    private val _state = MutableStateFlow<LinkReceiverState>(LinkReceiverState.Idle)
    val state: StateFlow<LinkReceiverState> = _state.asStateFlow()

    /** Bytes TS para el reproductor. Se crea uno nuevo por cada conexión. */
    @Volatile var source: TsStreamSource = TsStreamSource()
        private set

    private val _sessions = MutableStateFlow(0)
    /** Sube en uno cada vez que hay una conexión nueva con video: la pantalla vuelve a preparar el reproductor. */
    val sessions: StateFlow<Int> = _sessions.asStateFlow()

    /** Lo completa la pantalla del reproductor: milisegundos de búfer y cuadros descartados del reproductor. */
    @Volatile var playerStats: () -> Pair<Int, Long> = { 0 to 0L }

    val peers: List<LinkPeer> get() = store.peers()
    val lastSender: String get() = store.lastSender
    fun forget(idHex: String) = store.forget(idHex)

    @Volatile private var running = false
    @Volatile private var socket: Socket? = null
    private var sasFuture: CompletableFuture<Boolean>? = null

    fun confirmCode(ok: Boolean) { sasFuture?.complete(ok) }

    /** [pair] = true para emparejar por primera vez; false para usar el secreto ya guardado. */
    fun connect(host: String, port: Int, pair: Boolean) {
        if (running) return
        running = true
        store.lastSender = "$host:$port"
        Thread({ runLoop(host, port, pair) }, "opentv-link-receiver").apply { isDaemon = true; start() }
    }

    fun disconnect() {
        running = false
        runCatching { socket?.close() }
        source.close()
        _state.value = LinkReceiverState.Idle
    }

    private fun runLoop(host: String, port: Int, firstPair: Boolean) {
        var attempt = 0
        var pair = firstPair
        while (running && attempt < MAX_ATTEMPTS) {
            attempt++
            _state.value = LinkReceiverState.Connecting(host, attempt)
            val streamed = try {
                session(host, port, pair)
            } catch (e: OtProtocolException) {
                _state.value = LinkReceiverState.Failed("El emisor rechazó o no pudo autenticar la conexión: ${e.message}")
                if (pair) break // un rechazo al emparejar no se reintenta solo
                false
            } catch (e: IOException) {
                if (attempt >= MAX_ATTEMPTS) _state.value = LinkReceiverState.Failed("No se pudo conectar con $host:$port (${e.message ?: "sin respuesta"}).")
                false
            }
            if (streamed) { attempt = 0; pair = false } // si llegó a transmitir, el emparejamiento ya existe
            if (!running) break
            Thread.sleep(RETRY_MS)
        }
        if (running) { running = false; if (_state.value !is LinkReceiverState.Failed) _state.value = LinkReceiverState.Idle }
    }

    /** Devuelve `true` si llegó a recibir video. */
    private fun session(host: String, port: Int, pair: Boolean): Boolean {
        val s = Socket()
        socket = s
        var got = false
        try {
            s.connect(InetSocketAddress(host, port), 5_000)
            s.tcpNoDelay = true
            s.soTimeout = 90_000
            val channel = OtChannel(s.getInputStream(), s.getOutputStream())
            val me = store.identity(store.receiverCaps())
            val secure: OtSecureSession = OtHandshake.asReceiver(channel, me, store, pair) { code -> askCode(code) }
            val offerFrame = secure.receive() ?: throw IOException("El emisor cerró antes de ofrecer video")
            if (offerFrame.type == OtWire.Type.ERROR) throw OtProtocolException("el emisor devolvió un error")
            if (offerFrame.type != OtWire.Type.OFFER) throw OtProtocolException("se esperaba OFFER")
            val offer = OtOffer.decode(offerFrame.payload)
            val supported = offer.videoCodec == 1 && offer.container == 1
            secure.send(OtWire.Type.ACCEPT, 0, OtAccept(supported, offer.bitrateBps, offer.fps, 1_500).encode())
            if (!supported) throw OtProtocolException("formato no soportado")
            val src = TsStreamSource()
            source = src
            _sessions.value = _sessions.value + 1
            s.soTimeout = 15_000
            startStats(secure, src)
            var lastBytes = 0L
            var lastMs = System.currentTimeMillis()
            while (running) {
                val f = secure.receive() ?: break
                when (f.type) {
                    OtWire.Type.TS_DATA -> {
                        got = true
                        if (!src.push(f.payload)) secure.send(OtWire.Type.CONTROL, 0, OtControl(OtWire.Control.REQUEST_KEYFRAME).encode())
                    }
                    OtWire.Type.PING -> secure.send(OtWire.Type.PONG, 0, f.payload)
                    OtWire.Type.BYE -> break
                    OtWire.Type.ERROR -> break
                }
                val now = System.currentTimeMillis()
                if (now - lastMs >= 1_000) {
                    val kbps = ((src.receivedBytes - lastBytes) * 8 * 1000 / (now - lastMs) / 1000).toInt()
                    lastBytes = src.receivedBytes; lastMs = now
                    _state.value = LinkReceiverState.Receiving(secure.peer.name, offer, kbps, src.overflows)
                }
            }
            runCatching { secure.send(OtWire.Type.BYE) }
        } finally {
            source.close()
            runCatching { s.close() }
            socket = null
        }
        return got
    }

    private fun startStats(secure: OtSecureSession, src: TsStreamSource) {
        Thread({
            try {
                while (running && !src.isClosed) {
                    Thread.sleep(1_000)
                    val (bufferMs, dropped) = playerStats()
                    secure.send(OtWire.Type.STATS, 0, OtStats(bufferMs, dropped, src.overflows).encode())
                }
            } catch (e: IOException) {
                // la conexión terminó
            } catch (e: InterruptedException) {
                // cierre
            }
        }, "opentv-link-stats").apply { isDaemon = true; start() }
    }

    private fun askCode(code: String): Boolean {
        val f = CompletableFuture<Boolean>()
        sasFuture = f
        _state.value = LinkReceiverState.ConfirmCode(code)
        return try { f.get(90, TimeUnit.SECONDS) } catch (e: Exception) { false } finally { sasFuture = null }
    }

    companion object {
        const val MAX_ATTEMPTS = 20
        const val RETRY_MS = 3_000L
        @Volatile private var instance: LinkReceiverController? = null
        fun get(context: Context): LinkReceiverController =
            instance ?: synchronized(this) { instance ?: LinkReceiverController(context).also { instance = it } }
    }
}
