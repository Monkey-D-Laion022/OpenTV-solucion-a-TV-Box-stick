package com.opentv.app.miracast

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.opentv.app.iptv.CapturePipeline
import com.opentv.app.iptv.IptvService
import com.opentv.app.iptv.PipelineSink
import com.opentv.app.iptv.StopReason
import com.opentv.app.stream.MirrorRequest
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Transmite la pantalla (y el audio, si hay permiso) al televisor por RTP/UDP con MPEG‑TS una vez que el intercambio RTSP
 * llegó a PLAY. Reutiliza la misma tubería de captura que IPTV/DLNA ([CapturePipeline]) con H.264 Baseline (lo que WFD llama
 * CBP). Los datagramas salen desde el hilo del codificador; no se bloquea. NO verificado en hardware.
 */
class WfdSourceStreamer(
    context: Context,
    private val request: MirrorRequest,
    private val captureAudio: Boolean,
    private val onProblem: (String) -> Unit,
) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var pipe: CapturePipeline? = null
    private var socket: DatagramSocket? = null
    @Volatile var packets = 0L; private set
    @Volatile var bytes = 0L; private set
    @Volatile var sendErrors = 0L; private set
    @Volatile var running = false; private set
    @Volatile private var stopped = false

    fun start(remote: InetAddress, port: Int, choice: WfdChoice) {
        val sock = DatagramSocket()
        socket = sock
        val rtp = RtpTsPacketizer()
        fun emit(list: List<ByteArray>) {
            for (d in list) {
                try { sock.send(DatagramPacket(d, d.size, remote, port)); packets++; bytes += d.size } catch (e: Exception) { sendErrors++ }
            }
        }
        val sink = object : PipelineSink {
            override fun onTs(chunk: ByteArray, keyStart: Boolean) = onTsKind(chunk, keyStart, false)
            override fun onTsKind(chunk: ByteArray, keyStart: Boolean, video: Boolean) {
                synchronized(rtp) {
                    val now = System.currentTimeMillis()
                    emit(rtp.add(chunk, now))
                    if (video) emit(rtp.flush(now)) // fin de cuadro: no se retiene nada
                }
            }
        }
        // Android 14+ exige un servicio en primer plano de tipo mediaProjection YA iniciado antes de getMediaProjection(). El camino Miracast
        // no lo arrancaba (IPTV, DLNA y OpenTV Link sí): el televisor completaba M1–M5 pero Android rechazaba la captura y salían 0 paquetes RTP.
        // Se arranca IptvService (declarado mediaPlayback|mediaProjection) y se espera a que esté en primer plano; no se bloquea el hilo RTSP.
        IptvService.start(appContext)
        Thread({
            var waited = 0
            while (!IptvService.ready && waited < 3_000 && !stopped) { try { Thread.sleep(50) } catch (e: InterruptedException) { return@Thread }; waited += 50 }
            if (stopped) return@Thread
            if (!IptvService.ready) {
                onProblem("Android no dejó iniciar el servicio en primer plano necesario para capturar la pantalla; no se transmite.")
                stop(); return@Thread
            }
            startCapture(choice, sink)
        }, "opentv-wfd-start").apply { isDaemon = true; start() }
    }

    private fun startCapture(choice: WfdChoice, sink: PipelineSink) {
        main.post {
            if (stopped) return@post
            try {
                val p = CapturePipeline(
                    appContext, request, choice.preset, captureAudio && choice.audio == WfdAudioChoice.AAC, sink,
                    onProjectionStopped = { onProblem("Se terminó la captura de pantalla."); stop() },
                    usePump = true,
                    onEnded = { reason: StopReason, why: String -> onProblem("Tubería detenida ($reason): $why"); stop() },
                    forceBaseline = true,
                )
                pipe = p
                p.start()
                running = true
            } catch (e: RuntimeException) {
                onProblem("No se pudo iniciar la captura para Miracast: ${e.message ?: e.javaClass.simpleName}")
                stop()
            }
        }
    }

    fun requestKeyFrame() { main.post { pipe?.requestKeyFrame() } }

    fun stop() {
        stopped = true
        running = false
        main.post { runCatching { pipe?.stop() }; pipe = null }
        runCatching { socket?.close() }; socket = null
        main.post { IptvService.stopIfIdle(appContext) } // el servicio solo se apaga si ninguna otra transmisión sigue registrada
    }

    fun summary(): String = "RTP enviados: $packets paquetes, ${bytes / 1024} KB, errores de envío: $sendErrors"
}
