package com.opentv.app.iptv

import com.opentv.app.stream.StreamHub
import java.io.BufferedInputStream
import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Servidor HTTP/1.1 mínimo para el canal IPTV: playlist HLS, segmentos, lista M3U y TS continuo. Un hilo por
 * conexión, con tope ([maxClients]); solo atiende a las direcciones que [acceptClient] permita (en la app, IPv4 de
 * la red local). No usa dependencias de Android, así que se prueba en la JVM con sockets reales en loopback.
 */
class IptvServer(
    private val router: IptvRouter,
    private val hub: StreamHub,
    private val acceptClient: (InetAddress) -> Boolean,
    private val maxClients: Int = 6,
    /** Dirección Wi‑Fi del propio teléfono: los pedidos que vienen de ella (o de loopback) NO cuentan como otro equipo. */
    private val ownAddress: InetAddress? = null,
) {
    private var server: ServerSocket? = null
    private val pool = Executors.newCachedThreadPool { r -> Thread(r, "opentv-iptv-http").apply { isDaemon = true } }
    private val open = AtomicInteger(0)
    private val liveOpen = AtomicInteger(0)
    private val requestCount = AtomicInteger(0)
    val bytesSent = AtomicLong(0)
    private val externalCount = AtomicInteger(0)
    private val externalIps = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    @Volatile var lastExternal: String = ""
        private set

    /** Pedidos recibidos desde OTROS equipos de la red (la única prueba de que el televisor llega al teléfono). */
    val externalRequests: Int get() = externalCount.get()
    val externalClientIps: Set<String> get() = externalIps.toSet()
    @Volatile private var running = false
    private val recent = java.util.concurrent.ConcurrentLinkedDeque<String>()

    /** Conexiones abiertas ahora. */
    val activeClients: Int get() = open.get()
    val requests: Int get() = requestCount.get()
    /** Últimos pedidos (`GET /ruta · estado`), para ver desde el teléfono qué hizo la app IPTV. */
    val history: List<String> get() = recent.toList()

    fun start(bindAddress: InetAddress?, port: Int = 0): Int {
        val socket = ServerSocket()
        socket.reuseAddress = true
        socket.bind(InetSocketAddress(bindAddress, port), 16)
        server = socket
        running = true
        Thread({ acceptLoop(socket) }, "opentv-iptv-accept").apply { isDaemon = true; start() }
        return socket.localPort
    }

    fun stop() {
        running = false
        runCatching { server?.close() }
        server = null
        pool.shutdownNow()
    }

    private fun note(line: String) {
        recent.addLast(line)
        while (recent.size > 12) recent.pollFirst()
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (running) {
            val client = try { socket.accept() } catch (e: IOException) { return }
            if (!acceptClient(client.inetAddress) || open.get() >= maxClients) {
                runCatching { client.close() }
                continue
            }
            open.incrementAndGet()
            try {
                pool.execute { try { handle(client) } finally { open.decrementAndGet(); runCatching { client.close() } } }
            } catch (e: RuntimeException) {
                open.decrementAndGet(); runCatching { client.close() }
            }
        }
    }

    private fun handle(client: Socket) {
        client.soTimeout = 10_000
        client.tcpNoDelay = true
        val input = BufferedInputStream(client.getInputStream())
        val out = client.getOutputStream()
        // Conexión persistente: los reproductores HLS piden playlist y segmentos por la misma conexión.
        while (running) {
            val head = try { readHead(input) } catch (e: SocketTimeoutException) { return } catch (e: IOException) { return } ?: return
            requestCount.incrementAndGet()
            if (isExternal(client.inetAddress, ownAddress)) {
                externalCount.incrementAndGet()
                externalIps.add(client.inetAddress.hostAddress ?: "?")
            }
            val lines = head.split("\r\n")
            val parts = lines[0].split(" ")
            if (parts.size < 3) { writeSimple(out, 400, "Pedido inválido", close = true); return }
            val headers = lines.drop(1).filter { it.contains(':') }.associate { it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim() }
            val method = parts[0]
            val path = parts[1]
            val keepAlive = !(headers["connection"]?.equals("close", true) ?: (parts[2] == "HTTP/1.0"))
            val response = router.route(method, path, headers["host"], headers["range"])
            if (isExternal(client.inetAddress, ownAddress)) {
                lastExternal = "${client.inetAddress.hostAddress} · $method ${path.substringBefore('?').substringAfterLast('/')} · " +
                    (if (response is IptvResponse.Body) response.status.toString() else "200 (continuo)") +
                    (headers["user-agent"]?.let { " · " + it.take(40) } ?: "")
            }
            when (response) {
                is IptvResponse.Body -> {
                    note("$method ${path.substringBefore('?').substringAfterLast('/')} · ${response.status}")
                    writeBody(out, response, head = method == "HEAD", keepAlive = keepAlive)
                    if (!keepAlive) return
                }
                IptvResponse.LiveTs -> {
                    note("$method live.ts · 200 (continuo)")
                    streamLive(client, out, head = method == "HEAD")
                    return
                }
            }
        }
    }

    private fun readHead(input: BufferedInputStream): String? {
        val sb = StringBuilder()
        while (sb.length < MAX_HEAD) {
            val b = input.read()
            if (b < 0) return null
            sb.append(b.toChar())
            if (sb.length >= 4 && sb.endsWith("\r\n\r\n")) return sb.substring(0, sb.length - 4)
        }
        return null
    }

    private fun writeBody(out: OutputStream, r: IptvResponse.Body, head: Boolean, keepAlive: Boolean) {
        val sb = StringBuilder()
        sb.append("HTTP/1.1 ").append(r.status).append(' ').append(reason(r.status)).append("\r\n")
        sb.append("Content-Type: ").append(r.contentType).append("\r\n")
        sb.append("Content-Length: ").append(r.body.size).append("\r\n")
        for ((k, v) in r.headers) sb.append(k).append(": ").append(v.replace('\n', ' ')).append("\r\n")
        sb.append("Connection: ").append(if (keepAlive) "keep-alive" else "close").append("\r\n\r\n")
        out.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
        if (!head) { out.write(r.body); bytesSent.addAndGet(r.body.size.toLong()) }
        out.flush()
    }

    private fun writeSimple(out: OutputStream, status: Int, message: String, close: Boolean) =
        writeBody(out, IptvResponse.Body(status, "text/plain; charset=utf-8", message.toByteArray()), head = false, keepAlive = !close)

    private fun streamLive(client: Socket, out: OutputStream, head: Boolean) {
        val header = "HTTP/1.1 200 OK\r\nContent-Type: video/MP2T\r\nCache-Control: no-cache, no-store\r\n" +
            "Access-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n"
        out.write(header.toByteArray(Charsets.ISO_8859_1)); out.flush()
        if (head) return
        client.soTimeout = 0
        liveOpen.incrementAndGet()
        val sub = hub.subscribe()
        try {
            while (running && !sub.closed) {
                val chunk = sub.poll(1_000) ?: continue
                out.write(chunk)
                bytesSent.addAndGet(chunk.size.toLong())
            }
        } catch (e: IOException) {
            // el cliente cortó: es lo normal
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            hub.unsubscribe(sub)
            liveOpen.decrementAndGet()
        }
    }

    private fun reason(status: Int) = when (status) {
        200 -> "OK"; 206 -> "Partial Content"; 400 -> "Bad Request"; 404 -> "Not Found"
        405 -> "Method Not Allowed"; 416 -> "Range Not Satisfiable"; 503 -> "Service Unavailable"
        else -> "Status"
    }

    companion object {
        private const val MAX_HEAD = 8 * 1024

        /** Un pedido es «de otro equipo» si no viene de loopback ni de la propia dirección del teléfono. */
        fun isExternal(client: InetAddress, own: InetAddress?): Boolean =
            !client.isLoopbackAddress && !client.isAnyLocalAddress && (own == null || client != own)
    }
}
