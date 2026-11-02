package com.opentv.app.link

import android.content.Context
import com.opentv.app.proto.OtAccept
import com.opentv.app.proto.OtChannel
import com.opentv.app.proto.OtControl
import com.opentv.app.proto.OtDiscovery
import com.opentv.app.proto.OtHandshake
import com.opentv.app.proto.OtOffer
import com.opentv.app.proto.OtProtocolException
import com.opentv.app.proto.OtSecureSession
import com.opentv.app.proto.OtStats
import com.opentv.app.proto.OtWire
import com.opentv.app.stream.LanIp
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface LinkReceiverState {
    data object Idle : LinkReceiverState
    data class Connecting(val host: String, val attempt: Int) : LinkReceiverState
    /** Modo televisor / receptor HDMI: espera a que un teléfono se conecte. [pairingOpen] = acepta emparejar un teléfono nuevo. */
    data class Listening(val address: String, val announce: String, val pairingOpen: Boolean, val paired: List<String>, val lastIssue: String?) : LinkReceiverState
    data class ConfirmCode(val code: String) : LinkReceiverState
    data class Receiving(val senderName: String, val offer: OtOffer, val kbps: Int, val overflows: Long) : LinkReceiverState
    data class Failed(val message: String) : LinkReceiverState
}

/**
 * Receptor nativo OpenTV Link. Es el lado del TELEVISOR (o de un receptor HDMI: TV box/stick Android conectado al televisor).
 * Modo principal [listen]: espera a que el teléfono emisor se conecte (así el teléfono elige el televisor). Modo avanzado [connect]:
 * se conecta al emisor, se empareja (código de 6 dígitos) o se reautentica con el secreto
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
    @Volatile private var server: ServerSocket? = null
    private val nsd = LinkNsd(app)
    @Volatile private var pairingUntilMs = 0L
    @Volatile private var announce = ""
    @Volatile private var listenAddress = ""
    @Volatile private var lastIssue: String? = null
    private var listening = false
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
        listening = false
        nsd.unregister()
        runCatching { server?.close() }
        server = null
        runCatching { socket?.close() }
        source.close()
        _state.value = LinkReceiverState.Idle
    }

    /**
     * Modo televisor: abre un puerto TCP, se anuncia en la red como receptor y espera al teléfono. Para emparejar un
     * teléfono nuevo hay que abrir la ventana de emparejamiento con [openPairing] (así nadie se empareja sin que el
     * dueño del televisor lo pida); los ya emparejados se reconectan solos con su secreto.
     */
    fun listen() {
        if (running) return
        val ip = LanIp.pick()
        if (ip == null) {
            _state.value = LinkReceiverState.Failed("No hay una red Wi‑Fi o Ethernet local activa. Conectá este equipo a la misma red que el teléfono.")
            return
        }
        val ss = try {
            ServerSocket().also { it.reuseAddress = true; it.bind(InetSocketAddress(ip, OtDiscovery.RECEIVER_PORT), 2) }
        } catch (e: IOException) {
            try { ServerSocket().also { it.reuseAddress = true; it.bind(InetSocketAddress(ip, 0), 2) } } catch (e2: IOException) {
                _state.value = LinkReceiverState.Failed("No se pudo abrir el puerto de recepción (${e2.message ?: "error de red"}).")
                return
            }
        }
        server = ss
        running = true
        listening = true
        lastIssue = null
        listenAddress = "${ip.hostAddress}:${ss.localPort}"
        val id = store.identity(store.receiverCaps())
        nsd.register(OtDiscovery.Advert(LinkStore.hex(id.deviceId), id.name, OtWire.ROLE_RECEIVER, id.caps, ss.localPort)) {
            announce = it
            refreshListening()
        }
        _state.value = listeningState()
        Thread({ acceptLoop(ss) }, "opentv-link-listen").apply { isDaemon = true; start() }
    }

    /** Abre 2 minutos la posibilidad de emparejar un teléfono nuevo. */
    fun openPairing() {
        pairingUntilMs = System.currentTimeMillis() + PAIRING_WINDOW_MS
        refreshListening()
    }

    private fun pairingOpen() = System.currentTimeMillis() < pairingUntilMs

    private fun listeningState() = LinkReceiverState.Listening(listenAddress, announce, pairingOpen(), store.peers().map { it.name }, lastIssue)

    private fun refreshListening() {
        if (listening && _state.value is LinkReceiverState.Listening) _state.value = listeningState()
    }

    private fun acceptLoop(ss: ServerSocket) {
        while (running && !ss.isClosed) {
            val s = try { ss.accept() } catch (e: IOException) { return }
            val pair = pairingOpen()
            try {
                val streamed = try {
                    serve(s, pair)
                } catch (e: OtProtocolException) {
                    lastIssue = "Un teléfono intentó conectarse y fue rechazado: ${e.message}. Si es nuevo, abrí «Emparejar un teléfono» en este equipo antes."
                    false
                } catch (e: IOException) {
                    lastIssue = "Conexión cortada: ${e.message ?: "sin detalle"}"
                    false
                }
                if (pair) pairingUntilMs = 0L // la ventana sirve para un solo intento
                if (streamed) lastIssue = null
            } finally {
                runCatching { s.close() }
                socket = null
                if (running) _state.value = listeningState()
            }
        }
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
        try {
            s.connect(InetSocketAddress(host, port), 5_000)
            return serve(s, pair)
        } finally {
            runCatching { s.close() }
            socket = null
        }
    }

    /** Atiende una conexión ya abierta (en cualquiera de los dos sentidos). Devuelve `true` si llegó a recibir video. */
    private fun serve(s: Socket, pair: Boolean): Boolean {
        socket = s
        var got = false
        try {
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
        const val PAIRING_WINDOW_MS = 120_000L
        @Volatile private var instance: LinkReceiverController? = null
        fun get(context: Context): LinkReceiverController =
            instance ?: synchronized(this) { instance ?: LinkReceiverController(context).also { instance = it } }
    }
}
