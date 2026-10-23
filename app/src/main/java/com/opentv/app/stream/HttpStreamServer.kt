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
) {
    val path: String = "/screen-$token.ts"

    private var server: ServerSocket? = null
    private val clients = AtomicInteger(0)
    @Volatile private var running = false

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
            val requestLine = readRequestLine(client) ?: return
            val parts = requestLine.split(" ")
            val method = parts.getOrNull(0)
            val target = parts.getOrNull(1)?.substringBefore('?')
            when {
                method != "GET" && method != "HEAD" -> respond(client, "405 Method Not Allowed")
                target != path -> respond(client, "404 Not Found")
                else -> {
                    client.soTimeout = 0
                    val out = client.getOutputStream()
                    out.write(headers("200 OK").toByteArray(Charsets.US_ASCII))
                    out.flush()
                    if (method == "GET") {
                        while (running && !subscriber.closed) {
                            val chunk = subscriber.poll(POLL_MS) ?: continue
                            out.write(chunk)
                            out.flush()
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

    private fun readRequestLine(client: Socket): String? {
        val input = BufferedInputStream(client.getInputStream())
        val line = StringBuilder()
        var total = 0
        var first: String? = null
        try {
            while (total < MAX_REQUEST_BYTES) {
                val b = input.read()
                if (b < 0) return first
                total++
                if (b == '\n'.code) {
                    val text = line.toString().trimEnd('\r')
                    if (first == null) first = text
                    if (text.isEmpty()) return first // fin de cabeceras
                    line.setLength(0)
                } else {
                    line.append(b.toChar())
                }
            }
        } catch (e: SocketTimeoutException) {
            return first
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

    private fun headers(status: String): String =
        "HTTP/1.0 $status\r\n" +
            "Content-Type: video/mpeg\r\n" +
            "Cache-Control: no-cache\r\n" +
            "Connection: close\r\n" +
            "transferMode.dlna.org: Streaming\r\n" +
            "contentFeatures.dlna.org: DLNA.ORG_PN=MPEG_TS_HD_NA_ISO;DLNA.ORG_OP=00;DLNA.ORG_CI=0;" +
            "DLNA.ORG_FLAGS=01700000000000000000000000000000\r\n\r\n"

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
