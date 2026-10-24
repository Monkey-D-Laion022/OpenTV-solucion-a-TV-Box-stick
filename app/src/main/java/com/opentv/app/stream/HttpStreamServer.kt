package com.opentv.app.stream

import java.io.BufferedInputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Servidor HTTP mínimo que entrega el flujo TS en vivo al TV. Solo atiende `GET`/`HEAD` de una única
 * ruta con un código aleatorio (nadie más en la red puede adivinarla), solo a direcciones que
 * [acceptClient] permita (en la app: IPv4 de la red local) y con un máximo de clientes.
 */
class HttpStreamServer(
    private val hub: StreamHub,
    token: String,
    private val acceptClient: (InetAddress) -> Boolean,
    private val maxClients: Int = 2,
    private val profile: StreamProfile = StreamProfile.AVC_TS,
) {
    val path: String = "/screen-$token.ts"

    private var server: ServerSocket? = null
    private val clients = AtomicInteger(0)
    @Volatile private var running = false

    /** Conexiones abiertas ahora, pedidos recibidos y el último pedido (para diagnóstico en el teléfono). */
    val activeClients: Int get() = clients.get()
    private val requestCount = AtomicInteger(0)
    val requests: Int get() = requestCount.get()
    @Volatile var lastRequest: String = ""
        private set
    val bytesSent = java.util.concurrent.atomic.AtomicLong(0)

    /** Cómo terminó la última conexión de video (quién cortó y cuánto duró). */
    @Volatile var lastClose: String = ""
        private set

    /** Empieza a escuchar y devuelve el puerto. Con [bindAddress] nulo escucha en todas las interfaces. */
    fun start(bindAddress: InetAddress?): Int {
        val socket = ServerSocket()
        socket.reuseAddress = true
        socket.bind(InetSocketAddress(bindAddress, 0), BACKLOG)
        server = socket
        running = true
        Thread({ acceptLoop(socket) }, "opentv-stream-accept").apply { isDaemon = true }.start()
        return socket.localPort
    }

    fun stop() {
        running = false
        try {
            server?.close()
        } catch (e: IOException) {
            // Ya estaba cerrado.
        }
        server = null
        hub.closeAll()
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (running) {
            val client = try {
                socket.accept()
            } catch (e: IOException) {
                return
            }
            if (!acceptClient(client.inetAddress)) {
                closeQuietly(client)
                continue
            }
            if (clients.incrementAndGet() > maxClients) {
                clients.decrementAndGet()
                respondAndClose(client, "503 Service Unavailable")
                continue
            }
            Thread({ serve(client) }, "opentv-stream-client").apply { isDaemon = true }.start()
        }
    }

    private fun serve(client: Socket) {
        val subscriber = hub.subscribe()
        try {
            client.soTimeout = REQUEST_TIMEOUT_MS
            val request = readRequest(client) ?: return
            val requestLine = request.line
            val parts = requestLine.split(" ")
            val method = parts.getOrNull(0)
            val target = parts.getOrNull(1)?.substringBefore('?')
            requestCount.incrementAndGet()
            lastRequest = "$method ${if (target == path) "(enlace correcto)" else target} · ${request.userAgent.ifEmpty { "sin user-agent" }}"
            when {
                method != "GET" && method != "HEAD" -> respond(client, "405 Method Not Allowed")
                target != path -> respond(client, "404 Not Found")
                else -> {
                    client.soTimeout = 0
                    client.tcpNoDelay = true
                    runCatching { client.sendBufferSize = 256 * 1024 }
                    val out = client.getOutputStream()
                    out.write(headers("200 OK").toByteArray(Charsets.US_ASCII))
                    out.flush()
                    if (method == "GET") {
                        val startMs = System.currentTimeMillis()
                        var sent = 0L
                        var reason = "OpenTV detuvo el envío"
                        try {
                            while (running && !subscriber.closed) {
                                val chunk = subscriber.poll(POLL_MS) ?: continue
                                out.write(chunk)
                                out.flush()
                                sent += chunk.size
                                bytesSent.addAndGet(chunk.size.toLong())
                            }
                        } catch (e: IOException) {
                            reason = "el TV cerró la conexión"
                        } finally {
                            lastClose = "$reason tras ${(System.currentTimeMillis() - startMs) / 1000.0} s y ${sent / 1024} KB"
                        }
                    }
                }
            }
        } catch (e: IOException) {
            // El TV cerró la conexión: es lo normal al detener la reproducción.
        } finally {
            hub.unsubscribe(subscriber)
            closeQuietly(client)
            clients.decrementAndGet()
        }
    }

    private class Request(val line: String, val userAgent: String)

    private fun readRequest(client: Socket): Request? {
        val input = BufferedInputStream(client.getInputStream())
        val line = StringBuilder()
        var total = 0
        var first: String? = null
        var userAgent = ""
        try {
            while (total < MAX_REQUEST_BYTES) {
                val b = input.read()
                if (b < 0) return first?.let { Request(it, userAgent) }
                total++
                if (b == '\n'.code) {
                    val text = line.toString().trimEnd('\r')
                    if (first == null) first = text
                    else if (text.startsWith("user-agent:", ignoreCase = true)) userAgent = text.substringAfter(':').trim().take(80)
                    if (text.isEmpty()) return Request(first!!, userAgent) // fin de cabeceras
                    line.setLength(0)
                } else {
                    line.append(b.toChar())
                }
            }
        } catch (e: SocketTimeoutException) {
            return first?.let { Request(it, userAgent) }
        }
        return null
    }

    private fun respond(client: Socket, status: String) {
        client.getOutputStream().apply {
            write("HTTP/1.0 $status\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
            flush()
        }
    }

    private fun respondAndClose(client: Socket, status: String) {
        try {
            respond(client, status)
        } catch (e: IOException) {
            // Sin importancia.
        } finally {
            closeQuietly(client)
        }
    }

    private fun headers(status: String): String = buildString {
        append("HTTP/1.0 ").append(status).append("\r\n")
        append("Content-Type: ").append(profile.mime).append("\r\n")
        append("Cache-Control: no-cache\r\n")
        append("Pragma: no-cache\r\n")
        append("Accept-Ranges: none\r\n")
        append("Connection: close\r\n")
        append("Server: Linux/3.0 UPnP/1.0 DLNADOC/1.50 OpenTV/1.0\r\n")
        append("transferMode.dlna.org: Streaming\r\n")
        append("realTimeInfo.dlna.org: DLNA.ORG_TLAG=*\r\n")
        profile.contentFeatures()?.let { append("contentFeatures.dlna.org: ").append(it).append("\r\n") }
        append("\r\n")
    }

    private fun closeQuietly(socket: Socket) {
        try {
            socket.close()
        } catch (e: IOException) {
            // Sin importancia.
        }
    }

    private companion object {
        const val BACKLOG = 4
        const val REQUEST_TIMEOUT_MS = 5_000
        const val MAX_REQUEST_BYTES = 8 * 1024
        const val POLL_MS = 500L
    }
}
