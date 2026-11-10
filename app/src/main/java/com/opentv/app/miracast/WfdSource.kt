package com.opentv.app.miracast

import com.opentv.app.iptv.IptvPreset

/**
 * Miracast (Wi‑Fi Display) COMPLETO desde el lado de la FUENTE, con APIs públicas: tras M1–M3 (ya hechos por el diagnóstico)
 * se envían M4 y M5, se atienden SETUP y PLAY del televisor y se transmite MPEG‑TS dentro de RTP por UDP.
 * Todo este archivo es lógica PURA (sin Android ni red): elige el formato, arma/lee los mensajes RTSP y lleva el estado.
 *
 * Hallazgo (6.7): hasta la 6.6 el diagnóstico se detenía por diseño tras M3 ("no se envía M4/M5, no hay SETUP/PLAY"). Un
 * televisor que ve llegar M1–M3 y luego nada se queda en «Obteniendo señal de Miracast»: ese síntoma se explica por la propia
 * detención de OpenTV y NO demuestra incompatibilidad del LG. Esta implementación continúa el protocolo; que el LG muestre
 * imagen y audio solo se sabrá probándolo (NO verificado en hardware).
 */
data class WfdChoice(
    val mode: WfdMode,
    val preset: IptvPreset,
    /** Perfil y nivel H.264 que se declaran en M4 (un solo bit cada uno). */
    val profileBit: Int,
    val levelBit: Int,
    val audio: WfdAudioChoice,
    val note: String,
)

enum class WfdAudioChoice(val m4Value: String, val label: String) {
    AAC("AAC 00000001 00", "AAC 48 kHz estéreo"),
    NONE("none", "sin audio (el televisor no ofrece AAC)"),
}

object WfdFormatChooser {
    private data class Cand(val w: Int, val h: Int, val fps: Int, val preset: IptvPreset, val level: Int)

    /** De mayor a menor exigencia. Nivel H.264 mínimo: 3.1=0x01, 3.2=0x02, 4.0=0x04, 4.1=0x08, 4.2=0x10. */
    private val CANDIDATES = listOf(
        Cand(1920, 1080, 60, IptvPreset.FULL_HD_60, 0x10),
        Cand(1920, 1080, 30, IptvPreset.FULL_HD, 0x04),
        Cand(1280, 720, 60, IptvPreset.HD_60, 0x02),
        Cand(1280, 720, 30, IptvPreset.BALANCED, 0x01),
        Cand(854, 480, 30, IptvPreset.COMPAT_480, 0x01),
    )

    /** Elige lo mejor que el televisor declara aceptar y que OpenTV sabe producir, sin pasar de [maxPreset] si se da. */
    fun choose(m3: Map<String, String>, maxPreset: IptvPreset? = null): Pair<WfdChoice?, String> {
        val v = m3["wfd_video_formats"]?.let { WfdVideoFormats.parse(it) }
            ?: return null to "El televisor no declaró wfd_video_formats interpretable: no se puede elegir formato."
        if (v.noVideo) return null to "El televisor declara «none» como video."
        val profileBit = when { v.profiles and 0x01 != 0 -> 0x01; v.profiles and 0x02 != 0 -> 0x02; else -> return null to "El televisor no declara perfil CBP ni CHP." }
        val limit = maxPreset?.let { it.width.toLong() * it.height * it.fps }
        for (c in CANDIDATES) {
            if (limit != null && c.preset.width.toLong() * c.preset.height * c.preset.fps > limit) continue
            val mode = v.modes.firstOrNull { !it.interlaced && it.width == c.w && it.height == c.h && it.fps == c.fps } ?: continue
            val levelBit = listOf(0x01, 0x02, 0x04, 0x08, 0x10).firstOrNull { it >= c.level && v.levels and it != 0 } ?: continue
            val audio = if (m3["wfd_audio_codecs"]?.contains("AAC", ignoreCase = true) == true) WfdAudioChoice.AAC else WfdAudioChoice.NONE
            return WfdChoice(mode, c.preset, profileBit, levelBit, audio,
                "Se elige ${mode.label} (${c.preset.shortLabel}) de entre ${v.modes.size} modo(s) que declara el televisor; nivel H.264 0x${levelBit.toString(16)}, perfil ${if (profileBit == 1) "CBP" else "CHP"}.") to "ok"
        }
        return null to "Ningún modo de los ${v.modes.size} que declara el televisor coincide con los que OpenTV produce (1080p60/30, 720p60/30, 854x480p30)."
    }
}

/** Puertos RTP del receptor, del valor `RTP/AVP/UDP;unicast 19000 0 mode=play`. */
fun parseClientRtpPort(value: String?): Int? =
    value?.let { Regex("""unicast\s+(\d+)""").find(it)?.groupValues?.get(1)?.toIntOrNull() }?.takeIf { it in 1..65535 }

/** Puerto del encabezado `Transport: RTP/AVP/UDP;unicast;client_port=19000-19001`. */
fun parseTransportClientPort(transport: String?): Int? =
    transport?.let { Regex("""client_port=(\d+)""").find(it)?.groupValues?.get(1)?.toIntOrNull() }?.takeIf { it in 1..65535 }

object WfdSourceRtsp {
    fun presentationUrl(localIp: String) = "rtsp://$localIp/wfd1.0/streamid=0"

    /** M4: la fuente fija lo que usará (SET_PARAMETER). */
    fun m4(cseq: Int, localIp: String, rtpPort: Int, c: WfdChoice): String {
        val cea = if (c.mode.table == WfdTable.CEA) 1L shl c.mode.bit else 0L
        val hh = if (c.mode.table == WfdTable.HH) 1L shl c.mode.bit else 0L
        val vesa = if (c.mode.table == WfdTable.VESA) 1L shl c.mode.bit else 0L
        val body = "wfd_video_formats: 00 00 %02x %02x %08x %08x %08x 00 0000 0000 00 none none\r\n".format(c.profileBit, c.levelBit, cea, vesa, hh) +
            "wfd_audio_codecs: ${c.audio.m4Value}\r\n" +
            "wfd_presentation_URL: ${presentationUrl(localIp)} none\r\n" +
            "wfd_client_rtp_ports: RTP/AVP/UDP;unicast $rtpPort 0 mode=play\r\n"
        return setParameter(cseq, "rtsp://localhost/wfd1.0", body)
    }

    /** M5: pide al receptor que inicie SETUP. */
    fun m5(cseq: Int): String = setParameter(cseq, "rtsp://localhost/wfd1.0", "wfd_trigger_method: SETUP\r\n")

    private fun setParameter(cseq: Int, uri: String, body: String): String =
        "SET_PARAMETER $uri RTSP/1.0\r\nCSeq: $cseq\r\nContent-Type: text/parameters\r\nContent-Length: ${body.toByteArray(Charsets.ISO_8859_1).size}\r\n\r\n$body"

    fun setupResponse(cseq: Int, session: String, clientPort: Int, serverPort: Int): String =
        "RTSP/1.0 200 OK\r\nCSeq: $cseq\r\nSession: $session;timeout=30\r\nTransport: RTP/AVP/UDP;unicast;client_port=$clientPort;server_port=$serverPort\r\n\r\n"

    fun withSession(cseq: Int, session: String): String = "RTSP/1.0 200 OK\r\nCSeq: $cseq\r\nSession: $session\r\n\r\n"
}

/** Lo que el flujo pide hacer a quien lo maneja. */
sealed interface WfdAction {
    data class Send(val text: String) : WfdAction
    data class Log(val text: String) : WfdAction
    /** Empezar a transmitir RTP/TS a [port] del receptor con [choice]. */
    data class StartStream(val clientPort: Int, val choice: WfdChoice) : WfdAction
    object KeyFrame : WfdAction
    object StopStream : WfdAction
    data class Fail(val reason: String) : WfdAction
    object Done : WfdAction
}

/**
 * Continuación del intercambio tras M3. Estados: M4 → M5 → SETUP → PLAY → transmitiendo (con mantenimiento M16, petición de
 * cuadro clave M7 y TEARDOWN). Lógica pura.
 */
class WfdSourceFlow(private val localIp: String, private val maxPreset: IptvPreset? = null) {
    enum class State { IDLE, WAIT_M4, WAIT_M5, WAIT_SETUP, WAIT_PLAY, STREAMING, DONE, FAILED }

    var state = State.IDLE; private set
    var choice: WfdChoice? = null; private set
    private var m4Cseq = -1
    private var m5Cseq = -1
    private var clientPort = 0
    private val session = "6a1b2c3d"

    fun start(m3: Map<String, String>, nextCseq: Int): Pair<Int, List<WfdAction>> {
        val (c, why) = WfdFormatChooser.choose(m3, maxPreset)
        if (c == null) { state = State.FAILED; return nextCseq to listOf(WfdAction.Fail(why)) }
        val port = parseClientRtpPort(m3["wfd_client_rtp_ports"])
        if (port == null) { state = State.FAILED; return nextCseq to listOf(WfdAction.Fail("El televisor no declaró wfd_client_rtp_ports interpretable.")) }
        choice = c; clientPort = port; m4Cseq = nextCseq; state = State.WAIT_M4
        return nextCseq + 1 to listOf(WfdAction.Log(c.note), WfdAction.Send(WfdSourceRtsp.m4(m4Cseq, localIp, port, c)))
    }

    fun onMessage(msg: RtspMessage, nextCseq: Int): Pair<Int, List<WfdAction>> {
        val cseq = msg.cseq ?: 0
        if (!msg.isRequest) return responseTo(msg, nextCseq)
        return when (msg.method) {
            "SETUP" -> {
                val port = parseTransportClientPort(msg.header("transport")) ?: clientPort
                clientPort = port
                state = State.WAIT_PLAY
                nextCseq to listOf(WfdAction.Log("SETUP del televisor (puerto RTP $port)."), WfdAction.Send(WfdSourceRtsp.setupResponse(cseq, session, port, SERVER_RTP_PORT)))
            }
            "PLAY" -> {
                val c = choice
                if (c == null) nextCseq to listOf(WfdAction.Send(WfdRtsp.notAllowed(cseq)))
                else { state = State.STREAMING; nextCseq to listOf(WfdAction.Send(WfdSourceRtsp.withSession(cseq, session)), WfdAction.StartStream(clientPort, c)) }
            }
            "PAUSE" -> nextCseq to listOf(WfdAction.Send(WfdSourceRtsp.withSession(cseq, session)))
            "TEARDOWN" -> { state = State.DONE; nextCseq to listOf(WfdAction.Send(WfdSourceRtsp.withSession(cseq, session)), WfdAction.StopStream, WfdAction.Done) }
            "GET_PARAMETER" -> nextCseq to listOf(WfdAction.Send(WfdRtsp.ok(cseq))) // M16 de mantenimiento
            "SET_PARAMETER" -> {
                val idr = msg.body.contains("wfd_idr_request", ignoreCase = true)
                nextCseq to listOfNotNull(WfdAction.Send(WfdRtsp.ok(cseq)), if (idr) WfdAction.KeyFrame else null, if (idr) WfdAction.Log("M7: el televisor pidió un cuadro clave.") else null)
            }
            "OPTIONS" -> nextCseq to listOf(WfdAction.Send(WfdRtsp.m2Response(cseq)))
            else -> nextCseq to listOf(WfdAction.Send(WfdRtsp.notAllowed(cseq)), WfdAction.Log("Método ${msg.method} no admitido."))
        }
    }

    private fun responseTo(msg: RtspMessage, nextCseq: Int): Pair<Int, List<WfdAction>> {
        val ok = msg.statusCode == 200
        return when (msg.cseq) {
            m4Cseq -> if (!ok) fail(nextCseq, "El televisor rechazó M4 (RTSP ${msg.statusCode}): no acepta el formato elegido ${choice?.mode?.label}.")
            else { m5Cseq = nextCseq; state = State.WAIT_M5; nextCseq + 1 to listOf(WfdAction.Log("M4 aceptado."), WfdAction.Send(WfdSourceRtsp.m5(m5Cseq))) }
            m5Cseq -> if (!ok) fail(nextCseq, "El televisor rechazó M5 (RTSP ${msg.statusCode}).")
            else { state = State.WAIT_SETUP; nextCseq to listOf(WfdAction.Log("M5 aceptado: se espera el SETUP del televisor.")) }
            else -> nextCseq to listOf(WfdAction.Log("Respuesta con CSeq inesperado (${msg.cseq}): se ignora."))
        }
    }

    private fun fail(next: Int, why: String): Pair<Int, List<WfdAction>> { state = State.FAILED; return next to listOf(WfdAction.Fail(why)) }

    companion object { const val SERVER_RTP_PORT = 19000 }
}

/**
 * Empaqueta MPEG‑TS en RTP (tipo 33, hasta 7 paquetes TS de 188 B por datagrama, reloj de 90 kHz). Lógica pura:
 * [add] devuelve los datagramas completos; [flush] entrega lo que quede, para no sumar latencia al final de cada cuadro.
 */
class RtpTsPacketizer(private val ssrc: Int = 0x4F545653, startSeq: Int = 0) {
    private var seq = startSeq and 0xFFFF
    private var pending = ByteArray(0)

    fun add(ts: ByteArray, nowMs: Long): List<ByteArray> {
        require(ts.size % TS == 0) { "El bloque no es múltiplo de 188 bytes" }
        pending += ts
        val out = ArrayList<ByteArray>()
        while (pending.size >= PER_PACKET * TS) { out += build(pending.copyOfRange(0, PER_PACKET * TS), nowMs); pending = pending.copyOfRange(PER_PACKET * TS, pending.size) }
        return out
    }

    fun flush(nowMs: Long): List<ByteArray> {
        if (pending.isEmpty()) return emptyList()
        val p = build(pending, nowMs); pending = ByteArray(0); return listOf(p)
    }

    private fun build(payload: ByteArray, nowMs: Long): ByteArray {
        val ts90 = (nowMs * 90).toInt()
        val h = ByteArray(12)
        h[0] = 0x80.toByte(); h[1] = 33
        h[2] = (seq shr 8).toByte(); h[3] = seq.toByte()
        h[4] = (ts90 shr 24).toByte(); h[5] = (ts90 shr 16).toByte(); h[6] = (ts90 shr 8).toByte(); h[7] = ts90.toByte()
        h[8] = (ssrc shr 24).toByte(); h[9] = (ssrc shr 16).toByte(); h[10] = (ssrc shr 8).toByte(); h[11] = ssrc.toByte()
        seq = (seq + 1) and 0xFFFF
        return h + payload
    }

    companion object { const val TS = 188; const val PER_PACKET = 7 }
}
