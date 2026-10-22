package com.opentv.app.connect

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Pruebas contra un servidor real en el equipo local (127.0.0.1). */
class SocketLanHttpTest {

    private fun server(handler: (Socket) -> Unit): Pair<ServerSocket, Thread> {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val worker = thread(isDaemon = true) {
            try {
                server.accept().use { handler(it) }
            } catch (e: IOException) {
                // El cliente cerró antes de tiempo: es parte de lo que se prueba.
            }
        }
        return server to worker
    }

    private fun readRequestHead(socket: Socket) {
        val input = socket.getInputStream()
        var matched = 0
        val end = "\r\n\r\n".toByteArray()
        while (matched < end.size) {
            val b = input.read()
            if (b < 0) return
            matched = if (b.toByte() == end[matched]) matched + 1 else if (b.toByte() == end[0]) 1 else 0
        }
    }

    private fun endpoint(server: ServerSocket) = LanEndpoint("127.0.0.1", server.localPort, "/desc.xml")

    @Test
    fun readsAWellFormedResponse() {
        val (server, _) = server { socket ->
            readRequestHead(socket)
            socket.getOutputStream().write(
                "HTTP/1.1 200 OK\r\nContent-Length: 5\r\nConnection: close\r\n\r\nhello".toByteArray(),
            )
        }
        server.use {
            runBlocking {
                val ep = endpoint(server)
                val response = SocketLanHttp().request(ep, HttpMessages.buildGet(ep))
                assertEquals(200, response.status)
                assertEquals("hello", response.body)
            }
        }
    }

    @Test
    fun connectionRefusedBecomesAnIoException() {
        val closed = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val ep = endpoint(closed)
        closed.close()
        runBlocking {
            try {
                SocketLanHttp(timeoutMs = 500).request(ep, HttpMessages.buildGet(ep))
                fail("Debía fallar")
            } catch (e: IOException) {
                // esperado
            }
        }
    }

    @Test
    fun garbageResponseBecomesAnIoException() {
        val (server, _) = server { socket ->
            readRequestHead(socket)
            socket.getOutputStream().write("esto no es http".toByteArray())
        }
        server.use {
            runBlocking {
                val ep = endpoint(server)
                try {
                    SocketLanHttp().request(ep, HttpMessages.buildGet(ep))
                    fail("Debía fallar")
                } catch (e: IOException) {
                    // esperado
                }
            }
        }
    }

    @Test
    fun silentServerTimesOutInsteadOfHanging() {
        val (server, _) = server { socket ->
            readRequestHead(socket)
            Thread.sleep(3_000)
        }
        server.use {
            runBlocking {
                val ep = endpoint(server)
                val started = System.nanoTime()
                try {
                    SocketLanHttp(timeoutMs = 300).request(ep, HttpMessages.buildGet(ep))
                    fail("Debía fallar")
                } catch (e: IOException) {
                    // esperado
                }
                val elapsedMs = (System.nanoTime() - started) / 1_000_000
                assertTrue("tardó $elapsedMs ms", elapsedMs < 2_500)
            }
        }
    }

    @Test
    fun oversizedResponsesAreCutOff() {
        val (server, _) = server { socket ->
            readRequestHead(socket)
            val out = socket.getOutputStream()
            out.write("HTTP/1.1 200 OK\r\nConnection: close\r\n\r\n".toByteArray())
            val chunk = ByteArray(8192) { 'a'.code.toByte() }
            repeat(200) { out.write(chunk) }
        }
        server.use {
            runBlocking {
                val ep = endpoint(server)
                val response = SocketLanHttp(maxResponseBytes = 1_000)
                    .request(ep, HttpMessages.buildGet(ep))
                assertTrue("cuerpo de ${response.body.length} bytes", response.body.length < 20_000)
            }
        }
    }
}
