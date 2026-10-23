package com.opentv.app.stream

import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.Socket
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpStreamServerTest {

    private val hub = StreamHub()
    private var server: HttpStreamServer? = null

    @After
    fun tearDown() {
        server?.stop()
    }

    private fun start(accept: (InetAddress) -> Boolean = { true }, maxClients: Int = 2): Int {
        val s = HttpStreamServer(hub, "tok123", accept, maxClients)
        server = s
        return s.start(InetAddress.getLoopbackAddress())
    }

    private fun request(port: Int, line: String): Socket {
        val socket = Socket(InetAddress.getLoopbackAddress(), port)
        socket.soTimeout = 3_000
        socket.getOutputStream().apply {
            write("$line\r\nHost: x\r\n\r\n".toByteArray())
            flush()
        }
        return socket
    }

    private fun readHeaders(socket: Socket): String {
        val sb = StringBuilder()
        val input = socket.getInputStream()
        while (!sb.endsWith("\r\n\r\n")) {
            val b = input.read()
            if (b < 0) break
            sb.append(b.toChar())
        }
        return sb.toString()
    }

    @Test
    fun streamsPublishedChunksWithDlnaHeaders() {
        val port = start()
        request(port, "GET /screen-tok123.ts HTTP/1.1").use { socket ->
            val headers = readHeaders(socket)
            assertTrue(headers.startsWith("HTTP/1.0 200 OK"))
            assertTrue(headers.contains("Content-Type: video/mpeg"))
            assertTrue(headers.contains("transferMode.dlna.org: Streaming"))
            assertTrue(headers.contains("DLNA.ORG_PN=MPEG_TS_HD_NA_ISO"))

            // Espera a que el cliente quede suscrito y publica una clave.
            val deadline = System.currentTimeMillis() + 2_000
            while (hub.subscriberCount == 0 && System.currentTimeMillis() < deadline) Thread.sleep(10)
            val chunk = ByteArray(376) { (it % 251).toByte() }
            hub.publish(chunk, keyStart = true)

            val received = ByteArrayOutputStream()
            val buffer = ByteArray(376)
            var read = 0
            while (read < chunk.size) {
                val n = socket.getInputStream().read(buffer, 0, chunk.size - read)
                if (n < 0) break
                received.write(buffer, 0, n)
                read += n
            }
            assertArrayEquals(chunk, received.toByteArray())
        }
    }

    @Test
    fun wrongPathAndMethodAreRejected() {
        val port = start()
        request(port, "GET /otra-cosa.ts HTTP/1.1").use { assertTrue(readHeaders(it).startsWith("HTTP/1.0 404")) }
        request(port, "POST /screen-tok123.ts HTTP/1.1").use { assertTrue(readHeaders(it).startsWith("HTTP/1.0 405")) }
    }

    @Test
    fun clientsNotAllowedAreDropped() {
        val port = start(accept = { false })
        Socket(InetAddress.getLoopbackAddress(), port).use { socket ->
            socket.soTimeout = 3_000
            socket.getOutputStream().write("GET /screen-tok123.ts HTTP/1.1\r\n\r\n".toByteArray())
            assertEquals(-1, socket.getInputStream().read())
        }
    }

    @Test
    fun headOnlyReturnsHeaders() {
        val port = start()
        request(port, "HEAD /screen-tok123.ts HTTP/1.1").use { socket ->
            assertTrue(readHeaders(socket).startsWith("HTTP/1.0 200 OK"))
            assertEquals(-1, socket.getInputStream().read())
        }
    }

    @Test
    fun extraClientsGet503() {
        val port = start(maxClients = 1)
        request(port, "GET /screen-tok123.ts HTTP/1.1").use { first ->
            readHeaders(first)
            request(port, "GET /screen-tok123.ts HTTP/1.1").use { second ->
                assertTrue(readHeaders(second).startsWith("HTTP/1.0 503"))
            }
        }
    }
}
