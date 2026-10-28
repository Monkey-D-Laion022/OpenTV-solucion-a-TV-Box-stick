package com.opentv.app.miracast

import android.annotation.SuppressLint
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado visible de una prueba de la etapa B. */
data class WfdUi(
    val running: Boolean = false,
    val started: Boolean = false,
    val observation: WfdObservation = WfdObservation(),
    val steps: List<WfdStep> = emptyList(),
    val log: List<String> = emptyList(),
)

/**
 * Etapa B: diagnóstico de la negociación Miracast/WFD con UN dispositivo elegido y confirmado por el usuario.
 *
 * Qué hace, en orden: pide la conexión Wi-Fi Direct (con la API pública), espera el grupo, abre el puerto RTSP en la
 * dirección del grupo y espera a que el televisor se conecte; si lo hace, intercambia M1/M2/M3 y lee sus capacidades.
 * Qué NO hace: no anuncia a OpenTV como fuente Wi-Fi Display (eso es API de sistema), no usa APIs ocultas ni reflexión,
 * no envía M4/M5, no hace SETUP/PLAY y no transmite video. Siempre se puede cancelar y todo se libera al terminar.
 */
@SuppressLint("MissingPermission")
class WfdDiagnosticSession(private val app: Application, private val scope: CoroutineScope) {
    private val _ui = MutableStateFlow(WfdUi())
    val ui: StateFlow<WfdUi> = _ui.asStateFlow()

    private var manager: WifiP2pManager? = null
    private var channel: WifiP2pManager.Channel? = null
    private var receiver: BroadcastReceiver? = null
    private var groupTimeout: Job? = null
    private var ioJob: Job? = null
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null

    @Volatile private var cancelRequested = false
    @Volatile private var groupHandled = false
    private var ended = true
    private var ownsGroup = false
    private var retries = 0
    private var startedAt = 0L
    private var peerAddress: String = ""

    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT >= P2pPolicy.FIRST_SDK_WITHOUT_LOCATION &&
            ContextCompat.checkSelfPermission(app, P2pPolicy.NEARBY_PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** Inicia la prueba con [peer]. Solo se llama después de la confirmación explícita del usuario. */
    fun start(peer: P2pPeer) {
        if (_ui.value.running) return
        startedAt = SystemClock.elapsedRealtime()
        cancelRequested = false
        groupHandled = false
        ownsGroup = false
        retries = 0
        ended = false
        _ui.value = WfdUi(running = true, started = true, observation = WfdObservation(peerName = peer.name))

        val address = peer.address
        if (address.isNullOrBlank()) {
            log("El dispositivo no informó una dirección utilizable: no se puede conectar.")
            updateObs { it.copy(connectAccepted = false) }
            finishRun()
            return
        }
        peerAddress = address
        if (!P2pPolicy.canRun(Build.VERSION.SDK_INT) || !hasPermission()) {
            log("Falta el permiso de dispositivos Wi-Fi cercanos o la versión de Android no lo permite.")
            updateObs { it.copy(connectAccepted = false) }
            finishRun()
            return
        }
        val m = app.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
        if (m == null) {
            log("El sistema no ofrece Wi-Fi Direct.")
            updateObs { it.copy(connectAccepted = false) }
            finishRun()
            return
        }
        manager = m
        val ch = m.initialize(app, Looper.getMainLooper(), null)
        channel = ch
        val rx = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION) onConnectionChanged()
            }
        }
        receiver = rx
        ContextCompat.registerReceiver(
            app, rx, IntentFilter(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        log("Prueba iniciada con «${peer.name}». Se puede cancelar en cualquier momento.")
        sendConnect()
    }

    /** Cancela la prueba y libera todo (sockets, grupo creado por esta prueba, receptor y canal). */
    fun cancel() {
        if (!_ui.value.running) return
        cancelRequested = true
        log("Prueba cancelada por el usuario.")
        finishRun()
    }

    // ---- Fase 1: solicitud de conexión ----

    private fun sendConnect() {
        val m = manager ?: return
        val ch = channel ?: return
        val config = WifiP2pConfig().apply {
            deviceAddress = peerAddress
            wps.setup = WpsInfo.PBC
            // Igual que el controlador de Android: ayuda a la convivencia entre Wi-Fi normal y Wi-Fi Direct.
            groupOwnerIntent = 0
        }
        try {
            m.connect(ch, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    if (cancelRequested || ended) return
                    updateObs { it.copy(connectAccepted = true) }
                    step(
                        WfdPhase.CONNECT_REQUEST, StepOutcome.OK,
                        "Android aceptó enviar la solicitud. Si es tu televisor, mirá su pantalla y aceptá el pedido.",
                    )
                    armGroupTimeout()
                }

                override fun onFailure(reason: Int) {
                    if (cancelRequested || ended) return
                    if (reason == WifiP2pManager.BUSY && retries < WfdTimeouts.CONNECT_MAX_RETRIES) {
                        retries++
                        log("Sistema ocupado (código 2): reintento $retries de ${WfdTimeouts.CONNECT_MAX_RETRIES}.")
                        scope.launch {
                            delay(WfdTimeouts.CONNECT_RETRY_DELAY_MS)
                            if (!cancelRequested && !ended) sendConnect()
                        }
                        return
                    }
                    updateObs { it.copy(connectAccepted = false, connectFailureCode = reason) }
                    step(WfdPhase.CONNECT_REQUEST, StepOutcome.FAILED, "Android rechazó la solicitud: ${P2pText.failure(reason)}.")
                    finishRun()
                }
            })
        } catch (e: SecurityException) {
            updateObs { it.copy(connectAccepted = false) }
            step(WfdPhase.CONNECT_REQUEST, StepOutcome.FAILED, "Excepción de seguridad: ${e.message ?: "sin detalle"}.")
            finishRun()
        }
    }

    // ---- Fase 2: formación del grupo ----

    private fun armGroupTimeout() {
        groupTimeout?.cancel()
        groupTimeout = scope.launch {
            delay(WfdTimeouts.GROUP_SECONDS * 1000L)
            if (!groupHandled && !cancelRequested && !ended) {
                updateObs { it.copy(groupFormed = false) }
                step(
                    WfdPhase.GROUP_FORMATION, StepOutcome.TIMEOUT,
                    "No se formó el grupo en ${WfdTimeouts.GROUP_SECONDS} s (el televisor no aceptó, no respondió o no es conectable).",
                )
                finishRun()
            }
        }
    }

    private fun onConnectionChanged() {
        if (groupHandled || cancelRequested || ended) return
        val m = manager ?: return
        val ch = channel ?: return
        try {
            m.requestConnectionInfo(ch) { info -> if (info != null && info.groupFormed) onGroupFormed(info) }
        } catch (e: SecurityException) {
            log("Excepción de seguridad al consultar la conexión: ${e.message ?: "sin detalle"}.")
        }
    }

    private fun onGroupFormed(info: WifiP2pInfo) {
        if (groupHandled || cancelRequested || ended) return
        groupHandled = true
        groupTimeout?.cancel()
        val m = manager ?: return
        val ch = channel ?: return
        val ownerAddress = info.groupOwnerAddress?.hostAddress
        try {
            m.requestGroupInfo(ch) { group ->
                if (cancelRequested || ended) return@requestGroupInfo
                val iface = group?.getInterface()
                val matches = group != null && (
                    group.owner?.deviceAddress.equals(peerAddress, ignoreCase = true) ||
                        group.clientList.any { it.deviceAddress.equals(peerAddress, ignoreCase = true) }
                    )
                updateObs {
                    it.copy(
                        groupFormed = true, weAreGroupOwner = info.isGroupOwner,
                        groupInterface = iface, groupOwnerAddress = ownerAddress,
                    )
                }
                if (!matches) {
                    // No se toca un grupo que no creó esta prueba.
                    step(
                        WfdPhase.GROUP_FORMATION, StepOutcome.FAILED,
                        "Se formó un grupo, pero no con el dispositivo elegido: no se usa ni se modifica.",
                    )
                    finishRun()
                    return@requestGroupInfo
                }
                ownsGroup = true
                step(
                    WfdPhase.GROUP_FORMATION, StepOutcome.OK,
                    "Grupo formado. Propietario: ${if (info.isGroupOwner) "este teléfono" else "el otro dispositivo"} · interfaz ${iface ?: "?"}.",
                )
                if (iface == null) {
                    step(WfdPhase.LISTENING, StepOutcome.FAILED, "Android no informó la interfaz del grupo.")
                    finishRun()
                } else {
                    ioJob = scope.launch(Dispatchers.IO) { runRtsp(iface) }
                }
            }
        } catch (e: SecurityException) {
            step(WfdPhase.GROUP_FORMATION, StepOutcome.FAILED, "Excepción de seguridad: ${e.message ?: "sin detalle"}.")
            finishRun()
        }
    }

    // ---- Fases 3 a 7: escucha RTSP y negociación M1/M2/M3 (hilo de red) ----

    private fun groupAddress(iface: String): InetAddress? = try {
        NetworkInterface.getByName(iface)?.inetAddresses?.toList()?.firstOrNull { it is Inet4Address }
    } catch (e: IOException) {
        null
    }

    private fun runRtsp(iface: String) {
        val local = groupAddress(iface)
        if (local == null) {
            updateObs { it.copy(listening = false) }
            step(WfdPhase.LISTENING, StepOutcome.FAILED, "No hay dirección IPv4 en la interfaz del grupo ($iface).")
            finishRun()
            return
        }
        val server = try {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(local, WfdTimeouts.RTSP_PORT))
                soTimeout = 1000
            }
        } catch (e: IOException) {
            updateObs { it.copy(listening = false) }
            step(WfdPhase.LISTENING, StepOutcome.FAILED, "No se pudo abrir el puerto ${WfdTimeouts.RTSP_PORT}: ${e.message ?: "sin detalle"}.")
            finishRun()
            return
        }
        serverSocket = server
        updateObs { it.copy(listening = true, localAddress = local.hostAddress) }
        step(
            WfdPhase.LISTENING, StepOutcome.OK,
            "Escuchando solo en ${local.hostAddress}:${WfdTimeouts.RTSP_PORT} (dirección del grupo), hasta ${WfdTimeouts.RTSP_SECONDS} s.",
        )

        var client: Socket? = null
        val deadline = SystemClock.elapsedRealtime() + WfdTimeouts.RTSP_SECONDS * 1000L
        while (!cancelRequested && client == null && SystemClock.elapsedRealtime() < deadline) {
            try {
                client = server.accept()
            } catch (e: SocketTimeoutException) {
                // sigue esperando
            } catch (e: IOException) {
                break
            }
        }
        runCatching { server.close() } // una sola conexión: se cierra la escucha de inmediato
        serverSocket = null
        if (cancelRequested || ended) {
            runCatching { client?.close() }
            return
        }
        if (client == null) {
            step(WfdPhase.TCP_FROM_TV, StepOutcome.TIMEOUT, "El televisor no abrió ninguna conexión TCP en ${WfdTimeouts.RTSP_SECONDS} s.")
            finishRun()
            return
        }
        clientSocket = client
        val remote = client.inetAddress?.hostAddress
        updateObs { it.copy(tvRemoteAddress = remote) }
        step(WfdPhase.TCP_FROM_TV, StepOutcome.OK, "El televisor se conectó desde $remote.")
        negotiate(client)
        finishRun()
    }

    private fun negotiate(client: Socket) {
        val deadline = SystemClock.elapsedRealtime() + WfdTimeouts.RTSP_SECONDS * 1000L
        val parser = RtspParser()
        val buffer = ByteArray(4096)
        var nextCseq = 1
        var m1Cseq = -1
        var m3Cseq = -1
        var m1Done = false
        var m2Done = false
        var m3Sent = false
        var m3Done = false
        var closedByTv = false

        try {
            client.soTimeout = 1000
            val input = client.getInputStream()
            val output = client.getOutputStream()

            fun send(text: String) {
                output.write(text.toByteArray(Charsets.ISO_8859_1))
                output.flush()
                log("Enviado: ${text.lineSequence().first()}")
            }

            m1Cseq = nextCseq++
            send(WfdRtsp.m1(m1Cseq))

            fun handle(msg: RtspMessage) {
                log("Recibido: ${msg.summary}" + if (msg.body.isNotBlank()) " · ${msg.body.trim().take(300)}" else "")
                if (!msg.isRequest) {
                    when (msg.cseq) {
                        m1Cseq -> {
                            m1Done = true
                            updateObs { it.copy(m1Status = msg.statusCode, m1Public = msg.header("public")) }
                            step(WfdPhase.M1, if (msg.statusCode == 200) StepOutcome.OK else StepOutcome.FAILED, "Respuesta RTSP ${msg.statusCode}.")
                        }
                        m3Cseq -> {
                            m3Done = true
                            val params = WfdRtsp.parseParameters(msg.body)
                            updateObs { it.copy(m3Status = msg.statusCode, m3Params = params) }
                            step(
                                WfdPhase.M3, if (msg.statusCode == 200) StepOutcome.OK else StepOutcome.FAILED,
                                "Respuesta RTSP ${msg.statusCode} con ${params.size} parámetro(s).",
                            )
                        }
                        else -> log("Respuesta con CSeq inesperado (${msg.cseq}): se ignora.")
                    }
                } else {
                    val cseq = msg.cseq ?: 0
                    when (msg.method) {
                        "OPTIONS" -> {
                            updateObs { it.copy(m2Method = msg.method, m2Public = msg.header("public"), m2Require = msg.header("require")) }
                            send(WfdRtsp.m2Response(cseq))
                            m2Done = true
                            step(WfdPhase.M2, StepOutcome.OK, "Recibido y respondido.")
                        }
                        "GET_PARAMETER" -> send(WfdRtsp.ok(cseq))
                        else -> {
                            send(WfdRtsp.notAllowed(cseq))
                            log("Método ${msg.method} no admitido en esta etapa (no se hace SETUP ni PLAY).")
                        }
                    }
                }
                if (m1Done && m2Done && !m3Sent) {
                    m3Cseq = nextCseq++
                    send(WfdRtsp.m3(m3Cseq))
                    m3Sent = true
                }
            }

            while (!cancelRequested && !m3Done && SystemClock.elapsedRealtime() < deadline) {
                val n = try {
                    input.read(buffer)
                } catch (e: SocketTimeoutException) {
                    0
                }
                if (n < 0) {
                    closedByTv = true
                    log("El televisor cerró la conexión.")
                    break
                }
                if (n > 0) {
                    parser.feed(buffer, n)
                    if (parser.overflow) {
                        log("El televisor envió datos que no son un mensaje RTSP válido o demasiado grandes: se corta.")
                        break
                    }
                    while (true) handle(parser.next() ?: break)
                }
            }
        } catch (e: IOException) {
            log("Error de red durante la negociación: ${e.message ?: "sin detalle"}.")
            closedByTv = true
        }

        if (cancelRequested) return
        // Fases que no llegaron a completarse: se registra por qué.
        val reason = if (closedByTv) StepOutcome.FAILED else StepOutcome.TIMEOUT
        val why = if (closedByTv) "conexión cerrada antes de completarse" else "sin respuesta en ${WfdTimeouts.RTSP_SECONDS} s"
        if (!m1Done) step(WfdPhase.M1, reason, "Sin respuesta al M1: $why.")
        if (!m2Done) step(WfdPhase.M2, reason, "El televisor no envió su M2: $why.")
        if (!m3Done) step(WfdPhase.M3, if (m3Sent) reason else StepOutcome.SKIPPED, if (m3Sent) "Sin respuesta al M3: $why." else "No se envió: faltó M1 o M2.")
    }

    // ---- Cierre ----

    @Synchronized
    private fun finishRun() {
        if (ended) return
        ended = true
        groupTimeout?.cancel()
        groupTimeout = null
        runCatching { clientSocket?.close() }
        runCatching { serverSocket?.close() }
        clientSocket = null
        serverSocket = null

        // Las fases que no se alcanzaron quedan registradas como tales, no como éxito ni como fallo.
        val recorded = _ui.value.steps.map { it.phase }.toSet()
        val pendingOutcome = if (cancelRequested) StepOutcome.CANCELLED else StepOutcome.SKIPPED
        for (phase in WfdPhase.values()) {
            if (phase != WfdPhase.CLEANUP && phase !in recorded) {
                step(phase, pendingOutcome, "No se llegó a esta fase.")
            }
        }

        val m = manager
        val ch = channel
        var cleanupDetail = "Sockets cerrados."
        if (m != null && ch != null) {
            val closeChannel: () -> Unit = { if (Build.VERSION.SDK_INT >= 27) runCatching { ch.close() } }
            if (ownsGroup) {
                try {
                    m.removeGroup(ch, object : WifiP2pManager.ActionListener {
                        override fun onSuccess() {
                            log("Grupo Wi-Fi Direct de la prueba eliminado.")
                            closeChannel()
                        }

                        override fun onFailure(reason: Int) {
                            log("No se pudo confirmar la eliminación del grupo: ${P2pText.failure(reason)}.")
                            closeChannel()
                        }
                    })
                    cleanupDetail += " Se pidió eliminar el grupo creado por la prueba."
                } catch (e: SecurityException) {
                    closeChannel()
                }
            } else {
                runCatching { m.cancelConnect(ch, null) }
                closeChannel()
                cleanupDetail += " Solicitud pendiente cancelada."
            }
        }
        receiver?.let { runCatching { app.unregisterReceiver(it) } }
        receiver = null
        channel = null
        manager = null

        updateObs { it.copy(finished = true, cancelled = cancelRequested) }
        step(WfdPhase.CLEANUP, StepOutcome.OK, cleanupDetail)
        _ui.update { it.copy(running = false) }
    }

    // ---- Utilidades ----

    private fun elapsed(): Long = SystemClock.elapsedRealtime() - startedAt

    private fun updateObs(block: (WfdObservation) -> WfdObservation) {
        _ui.update { it.copy(observation = block(it.observation)) }
    }

    private fun step(phase: WfdPhase, outcome: StepOutcome, detail: String) {
        val s = WfdStep(phase, outcome, elapsed(), detail)
        _ui.update { it.copy(steps = it.steps + s) }
        log(WfdDiagnosticText.stepLine(s))
    }

    private fun log(text: String) {
        val line = String.format(java.util.Locale.US, "+%.1f s  %s", elapsed() / 1000.0, text)
        _ui.update { it.copy(log = (it.log + line).takeLast(120)) }
    }
}
