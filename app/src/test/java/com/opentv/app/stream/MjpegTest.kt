package com.opentv.app.stream

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.Socket
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MjpegTest {
    private val hub = MjpegHub()
    private var allow = true
    private val server = MjpegHttpServer(hub, "abc123", { allow }, maxClients = 2, idleResendMs = 150)
    private var port = 0

    private fun start() {
        port = server.start(InetAddress.getLoopbackAddress())
    }

    @After
    fun tearDown() = server.stop()

    private fun get(path: String, method: String = "GET"): String {
        Socket(InetAddress.getLoopbackAddress(), port).use { s ->
            s.soTimeout = 3_000
            s.getOutputStream().write("$method $path HTTP/1.1\r\nHost: x\r\n\r\n".toByteArray())
            return String(s.getInputStream().readBytes(), Charsets.ISO_8859_1)
        }
    }

    private fun readUntil(input: InputStream, marker: String, limit: Int = 20_000): String {
        val out = ByteArrayOutputStream()
        while (out.size() < limit) {
            val b = input.read()
            if (b < 0) break
            out.write(b)
            if (String(out.toByteArray(), Charsets.ISO_8859_1).contains(marker)) break
        }
        return String(out.toByteArray(), Charsets.ISO_8859_1)
    }

    @Test
    fun hubKeepsOnlyTheLatestFrame() {
        hub.publish(byteArrayOf(1)); hub.publish(byteArrayOf(2)); hub.publish(byteArrayOf(3))
        val f = hub.awaitNewer(0, 100)!!
        assertEquals(3L, f.seq)
        assertEquals(3, f.jpeg[0].toInt())
        assertNull(hub.awaitNewer(f.seq, 50)) // sin novedades
    }

    @Test
    fun hubWakesAWaitingClientAndClosesCleanly() {
        var got: MjpegHub.Frame? = null
        val t = Thread { got = hub.awaitNewer(0, 2_000) }
        t.start(); Thread.sleep(50)
        hub.publish(byteArrayOf(9))
        t.join(1_000)
        assertEquals(9, got!!.jpeg[0].toInt())
        hub.close()
        assertTrue(hub.isClosed)
        assertNull(hub.awaitNewer(99, 50))
    }

    @Test
    fun servesThePageUnderTheTokenOnly() {
        start()
        val page = get("/abc123/")
        assertTrue(page.startsWith("HTTP/1.0 200"))
        assertTrue(page.contains("/abc123/stream.mjpg"))
        assertTrue(get("/abc123").startsWith("HTTP/1.0 200"))
        assertTrue(get("/").startsWith("HTTP/1.0 404"))
        assertTrue(get("/otro/").startsWith("HTTP/1.0 404"))
        assertTrue(get("/abc123/", "POST").startsWith("HTTP/1.0 405"))
        assertTrue(get("/abc123/lento").contains("http-equiv=\"refresh\""))
    }

    @Test
    fun snapshotReturnsTheLatestJpeg() {
        start()
        hub.publish(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3))
        val r = get("/abc123/snapshot.jpg")
        assertTrue(r.contains("Content-Type: image/jpeg"))
        assertTrue(r.contains("Content-Length: 5"))
        assertTrue(get("/abc123/snapshot.jpg", "HEAD").length < r.length)
    }

    @Test
    fun streamSendsMultipartFramesAndRepeatsWhenIdle() {
        start()
        hub.publish(byteArrayOf(7, 7, 7))
        Socket(InetAddress.getLoopbackAddress(), port).use { s ->
            s.soTimeout = 3_000
            s.getOutputStream().write("GET /abc123/stream.mjpg HTTP/1.1\r\n\r\n".toByteArray())
            val first = readUntil(s.getInputStream(), "\r\n\r\n\u0007\u0007\u0007\r\n")
            assertTrue(first.contains("multipart/x-mixed-replace; boundary=opentvframe"))
            assertTrue(first.contains("--opentvframe\r\nContent-Type: image/jpeg\r\nContent-Length: 3"))
            // Sin imagen nueva el servidor repite la última (mantiene viva la conexión).
            val second = readUntil(s.getInputStream(), "--opentvframe")
            assertTrue(second.contains("--opentvframe"))
            hub.publish(byteArrayOf(8, 8))
            val third = readUntil(s.getInputStream(), "\b\b\r\n")
            assertTrue(third.contains("Content-Length: 2"))
        }
        assertTrue(server.framesSent.get() >= 2)
    }

    @Test
    fun rejectsClientsOutsideTheLocalNetwork() {
        allow = false
        start()
        Socket(InetAddress.getLoopbackAddress(), port).use { s ->
            s.soTimeout = 2_000
            s.getOutputStream().write("GET /abc123/ HTTP/1.1\r\n\r\n".toByteArray())
            assertEquals(0, s.getInputStream().readBytes().size) // cerrada sin responder
        }
    }

    @Test
    fun limitsConcurrentClients() {
        start()
        hub.publish(byteArrayOf(1))
        val open = (1..2).map {
            Socket(InetAddress.getLoopbackAddress(), port).also { s ->
                s.getOutputStream().write("GET /abc123/stream.mjpg HTTP/1.1\r\n\r\n".toByteArray())
                readUntil(s.getInputStream(), "--opentvframe")
            }
        }
        val third = get("/abc123/")
        assertTrue(third.startsWith("HTTP/1.0 503"))
        open.forEach { it.close() }
    }

    @Test
    fun qualityKeepsAspectAndEvenSides() {
        assertEquals(1280 to 720, MjpegQuality.MEDIUM.captureSize(1920, 1080))
        assertEquals(960 to 540, MjpegQuality.LOW.captureSize(1920, 1080))
        assertEquals(1080 to 1920, MjpegQuality.HIGH.captureSize(1080, 1920))
        assertEquals(720 to 1640, MjpegQuality.HIGH.captureSize(720, 1640))
        val (w, h) = MjpegQuality.LOW.captureSize(1081, 2401)
        assertTrue(w % 2 == 0 && h % 2 == 0 && maxOf(w, h) <= 960)
        assertEquals(MjpegQuality.LOW, MjpegQuality.HIGH.next())
    }

    @Test
    fun shortUrlServesFromTheRootWithShortAliases() {
        val shortServer = MjpegHttpServer(hub, "", { true })
        val p = shortServer.start(InetAddress.getLoopbackAddress())
        try {
            fun fetch(path: String): String = Socket(InetAddress.getLoopbackAddress(), p).use { s ->
                s.soTimeout = 3_000
                s.getOutputStream().write("GET $path HTTP/1.1\r\n\r\n".toByteArray())
                String(s.getInputStream().readBytes(), Charsets.ISO_8859_1)
            }
            val page = fetch("/")
            assertTrue(page.startsWith("HTTP/1.0 200"))
            assertTrue(page.contains("src=\"/stream.mjpg\""))
            assertTrue(fetch("/l").contains("http-equiv=\"refresh\""))
            hub.publish(byteArrayOf(1, 2))
            assertTrue(fetch("/f").contains("image/jpeg"))
            assertTrue(fetch("/otra-cosa").startsWith("HTTP/1.0 404"))
            assertTrue(shortServer.history.any { it.contains("pidió GET /") })
        } finally {
            shortServer.stop()
        }
    }

    @Test
    fun usesTheFirstFreePreferredPortAndFallsBackToAnyPort() {
        val busy = java.net.ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val free = java.net.ServerSocket(0, 1, InetAddress.getLoopbackAddress()).also { it.close() }.localPort
        val s = MjpegHttpServer(hub, "", { true })
        try {
            assertEquals(free, s.start(InetAddress.getLoopbackAddress(), listOf(busy.localPort, free)))
        } finally {
            s.stop(); busy.close()
        }
        val busy2 = java.net.ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val s2 = MjpegHttpServer(MjpegHub(), "", { true })
        try {
            val got = s2.start(InetAddress.getLoopbackAddress(), listOf(busy2.localPort))
            assertTrue(got != busy2.localPort && got > 0)
        } finally {
            s2.stop(); busy2.close()
        }
    }

    @Test
    fun pagesFitTheImageWithoutStretchingIt() {
        for (page in listOf(MjpegPages.live("/"), MjpegPages.slow("/"))) {
            assertTrue(page.contains("function fit()"))
            assertTrue(page.contains("Math.min(W/w,H/h)")) // escala única: conserva la proporción
            assertTrue(page.contains("visibility:hidden")) // oculta hasta conocer el tamaño real
            assertTrue(page.contains("<noscript>")) // sin JavaScript: ancho completo
        }
        assertTrue(MjpegPages.live("/x/").contains("src=\"/x/stream.mjpg\""))
        assertTrue(MjpegPages.slow("/x/").contains("src=\"/x/snapshot.jpg\""))
    }
}
