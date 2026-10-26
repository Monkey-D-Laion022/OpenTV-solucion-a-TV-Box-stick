package com.opentv.app.stream

import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Servidor HTTP/1.0 que entrega la pantalla como MJPEG para el navegador del TV (sin DLNA ni Miracast).
 * Rutas, solo para direcciones que [acceptClient] permita. Con [token] vacío es la URL corta (`http://IP:puerto/`); con
 * código, todo cuelga de `/<token>/`:
 * - `/` página con la imagen en vivo;
 * - `/stream.mjpg` (o `/s`) flujo `multipart/x-mixed-replace`;
 * - `/snapshot.jpg` (o `/f`) un solo fotograma; `/lento` (o `/l`) página que se recarga sola (navegadores muy viejos).
 * Solo HTTP: un TV viejo no sabría abrir HTTPS con certificados modernos, y en una red local no hay certificado válido.
 */
class MjpegHttpServer(
    private val hub: MjpegHub,
    val token: String,
    private val acceptClient: (InetAddress) -> Boolean,
    private val maxClients: Int = 4,
    private val idleResendMs: Long = 1_000,
) {
    private var server: ServerSocket? = null
    private val clients = AtomicInteger(0)
    @Volatile private var running = false

    val activeClients: Int get() = clients.get()
    private val events = java.util.concurrent.CopyOnWriteArrayList<String>()

    /** Últimos pedidos recibidos (quién y qué), para saber si el TV llegó al servidor. */
    val history: List<String> get() = events.takeLast(8)

    private fun note(text: String) {
        events.add(text)
    }

    private val prefix: String = if (token.isEmpty()) "/" else "/$token/"
    val bytesSent = AtomicLong(0)
    val framesSent = AtomicLong(0)

    /** Empieza a escuchar y devuelve el puerto: el primero libre de [preferredPorts] (fijo y fácil de teclear) o uno al azar. */
    fun start(bindAddress: InetAddress?, preferredPorts: List<Int> = emptyList()): Int {
        var socket: ServerSocket? = null
        for (port in preferredPorts + 0) {
            val candidate = ServerSocket()
            try {
                candidate.reuseAddress = true
                candidate.bind(InetSocketAddress(bindAddress, port), 8)
                socket = candidate
                break
            } catch (e: IOException) {
                runCatching { candidate.close() }
                if (port == 0) throw e
            }
        }
        socket!!
        server = socket
        running = true
        Thread({ acceptLoop(socket) }, "opentv-mjpeg-accept").apply { isDaemon = true }.start()
        return socket.localPort
    }

    fun stop() {
        running = false
        runCatching { server?.close() }
        server = null
        hub.close()
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (running) {
            val client = try {
                socket.accept()
            } catch (e: IOException) {
                return
            }
            if (!acceptClient(client.inetAddress)) {
                note("rechazado: ${client.inetAddress.hostAddress} (no es de la red local)")
                closeQuietly(client)
                continue
            }
            if (clients.incrementAndGet() > maxClients) {
                clients.decrementAndGet()
                runCatching { respond(client, "503 Service Unavailable", "text/plain", "Demasiados clientes".toByteArray()) }
                closeQuietly(client)
                continue
            }
            Thread({ serve(client) }, "opentv-mjpeg-client").apply { isDaemon = true }.start()
        }
    }

    private fun serve(client: Socket) {
        try {
            client.soTimeout = REQUEST_TIMEOUT_MS
            val line = readRequestLine(client) ?: return
            val parts = line.split(" ")
            val method = parts.getOrNull(0)
            val target = parts.getOrNull(1)?.substringBefore('?').orEmpty()
            if (method != "GET" && method != "HEAD") {
                respond(client, "405 Method Not Allowed", "text/plain", ByteArray(0))
                return
            }
            val base = prefix
            if (!target.startsWith(base) && !(token.isNotEmpty() && target == "/$token")) {
                respond(client, "404 Not Found", "text/plain", ByteArray(0))
                return
            }
            val head = method == "HEAD"
            note("${client.inetAddress.hostAddress} pidió $method $target")
            when (if (token.isNotEmpty() && target == "/$token") "" else target.removePrefix(base)) {
                "", "index.html" -> respond(client, "200 OK", "text/html; charset=utf-8", MjpegPages.live(prefix).toByteArray(), head)
                "lento", "l" -> respond(client, "200 OK", "text/html; charset=utf-8", MjpegPages.slow(prefix).toByteArray(), head)
                "snapshot.jpg", "f" -> {
                    val frame = hub.latest() ?: hub.awaitNewer(0, 3_000)
                    if (frame == null) respond(client, "503 Service Unavailable", "text/plain", "Todavía no hay imagen".toByteArray(), head)
                    else respond(client, "200 OK", "image/jpeg", frame.jpeg, head)
                }
                "stream.mjpg", "s" -> if (head) respond(client, "200 OK", STREAM_TYPE, ByteArray(0), true) else stream(client)
                else -> respond(client, "404 Not Found", "text/plain", ByteArray(0), head)
            }
        } catch (e: IOException) {
            // El navegador cerró la conexión: es lo normal al cerrar la pestaña.
        } finally {
            clients.decrementAndGet() // antes de cerrar: quien espera el cierre ya ve el cupo libre
            closeQuietly(client)
        }
    }

    private fun stream(client: Socket) {
        client.soTimeout = 0
        client.tcpNoDelay = true
        val out = client.getOutputStream()
        out.write(
            ("HTTP/1.0 200 OK\r\nContent-Type: $STREAM_TYPE\r\nCache-Control: no-cache, no-store\r\nPragma: no-cache\r\nConnection: close\r\n\r\n")
                .toByteArray(Charsets.US_ASCII),
        )
        var lastSeq = 0L
        var lastFrame: MjpegHub.Frame? = null
        while (running && !hub.isClosed) {
            val newer = hub.awaitNewer(lastSeq, idleResendMs)
            val frame = newer ?: lastFrame ?: continue // sin novedades: se repite el último para mantener viva la conexión
            writeFrame(out, frame.jpeg)
            lastSeq = frame.seq
            lastFrame = frame
        }
    }

    private fun writeFrame(out: OutputStream, jpeg: ByteArray) {
        out.write("--$BOUNDARY_LINE\r\nContent-Type: image/jpeg\r\nContent-Length: ${jpeg.size}\r\n\r\n".toByteArray(Charsets.US_ASCII))
        out.write(jpeg)
        out.write("\r\n".toByteArray(Charsets.US_ASCII))
        out.flush()
        bytesSent.addAndGet(jpeg.size.toLong())
        framesSent.incrementAndGet()
    }

    private fun respond(client: Socket, status: String, type: String, body: ByteArray, headOnly: Boolean = false) {
        val out = client.getOutputStream()
        out.write(
            "HTTP/1.0 $status\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nCache-Control: no-cache\r\nConnection: close\r\n\r\n"
                .toByteArray(Charsets.US_ASCII),
        )
        if (!headOnly) out.write(body)
        out.flush()
    }

    /** Lee la primera línea del pedido y descarta los encabezados (máx. 8 KB). */
    private fun readRequestLine(client: Socket): String? {
        val input = client.getInputStream()
        val text = StringBuilder()
        var total = 0
        while (total < MAX_REQUEST_BYTES) {
            val b = input.read()
            if (b < 0) return null
            total++
            text.append(b.toChar())
            if (text.endsWith("\r\n\r\n") || text.endsWith("\n\n")) break
        }
        return text.lineSequence().firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun closeQuietly(client: Socket) {
        runCatching { client.close() }
    }

    private companion object {
        const val BOUNDARY_LINE = MjpegPages.BOUNDARY
        const val STREAM_TYPE = "multipart/x-mixed-replace; boundary=${MjpegPages.BOUNDARY}"
        const val REQUEST_TIMEOUT_MS = 5_000
        const val MAX_REQUEST_BYTES = 8 * 1024
    }
}
