package com.opentv.app.iptv

import com.opentv.app.stream.StreamHub
import java.net.InetAddress
import java.net.Socket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IptvLogicTest {

    private fun chunk(n: Int, fill: Int = n) = ByteArray(188 * 2) { fill.toByte() }

    private val status = object : IptvStatusSource {
        override val channelName = "OpenTV Pantalla"
        override val width = 1280
        override val height = 720
        override val fps = 30
        override val bitrateBps = 4_000_000
        override val withAudio = true
        override val clients = 1
        override val startedAtMs = 1_000L
    }

    private fun feed(seg: HlsSegmenter, seconds: Int, key: Int = 1_000) {
        // un fotograma clave por segundo y 29 bloques intermedios
        for (s in 0 until seconds) {
            seg.onChunk(chunk(1), true, s * key.toLong())
            repeat(5) { seg.onChunk(chunk(2), false, s * key.toLong() + 100L * (it + 1)) }
        }
    }

    @Test fun `no guarda nada antes del primer fotograma clave`() {
        val s = HlsSegmenter()
        s.onChunk(chunk(2), false, 0)
        s.onChunk(chunk(2), false, 100)
        assertNull(s.playlist())
        assertEquals(0, s.publishedSegments)
    }

    @Test fun `corta en fotograma clave cuando se cumplio la duracion`() {
        val s = HlsSegmenter(HlsConfig(targetSegmentMs = 2_000))
        feed(s, 7)
        // claves en 0..6 s: se cierran segmentos en 2, 4 y 6 s
        assertEquals(3, s.publishedSegments)
        val p = s.playlist()!!
        assertTrue(p.startsWith("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:2\n"))
        assertTrue(p.contains("#EXTINF:2.000,\nseg-0.ts\n"))
        assertFalse(p.contains("ENDLIST"))
    }

    @Test fun `un segmento siempre empieza en un bloque de fotograma clave`() {
        val s = HlsSegmenter(HlsConfig(targetSegmentMs = 1_000))
        feed(s, 4)
        for (seq in s.retainedSequences()) {
            val first = s.segment(seq)!!.data[0].toInt()
            assertEquals(1, first) // los bloques clave se llenaron con 1
        }
    }

    @Test fun `la ventana avanza y MEDIA-SEQUENCE sube`() {
        val s = HlsSegmenter(HlsConfig(targetSegmentMs = 1_000, windowSegments = 3, extraKept = 1))
        feed(s, 12)
        val p = s.playlist()!!
        val seq = Regex("MEDIA-SEQUENCE:(\\d+)").find(p)!!.groupValues[1].toLong()
        assertEquals(s.publishedSegments - 3, seq)
        assertEquals(3, Regex("#EXTINF").findAll(p).count())
        // retiene la ventana + 1 extra
        assertEquals(4, s.retainedSequences().size)
        assertNull(s.segment(0))
        assertNotNull(s.segment(s.publishedSegments - 1))
    }

    @Test fun `el tope de memoria descarta segmentos viejos pero nunca los de la ventana`() {
        val big = ByteArray(1_000_000) { 1 }
        val s = HlsSegmenter(HlsConfig(targetSegmentMs = 500, windowSegments = 2, extraKept = 5, maxBytes = 3_000_000))
        for (i in 0 until 12) s.onChunk(big, true, i * 500L)
        assertTrue(s.retainedBytes() <= 3_000_000)
        assertTrue(s.retainedSequences().size >= 2)
    }

    @Test fun `TARGETDURATION cubre al segmento mas largo`() {
        val segs = listOf(HlsSegment(5, 2_400, ByteArray(1)), HlsSegment(6, 1_900, ByteArray(1)))
        val p = HlsPlaylist.render(segs, ended = true)
        assertTrue(p.contains("#EXT-X-TARGETDURATION:3\n"))
        assertTrue(p.contains("#EXT-X-MEDIA-SEQUENCE:5\n"))
        assertTrue(p.contains("#EXTINF:2.400,"))
        assertTrue(p.trimEnd().endsWith("#EXT-X-ENDLIST"))
    }

    @Test fun `end cierra la emision y deja de aceptar datos`() {
        val s = HlsSegmenter(HlsConfig(targetSegmentMs = 1_000))
        feed(s, 3)
        val before = s.publishedSegments
        s.end()
        s.onChunk(chunk(1), true, 99_000)
        assertEquals(before, s.publishedSegments)
        assertTrue(s.playlist()!!.contains("ENDLIST"))
    }

    @Test fun `nombres de segmento solo aceptan el patron propio`() {
        assertEquals(12L, HlsNames.parseSegment("seg-12.ts"))
        assertNull(HlsNames.parseSegment("../seg-1.ts"))
        assertNull(HlsNames.parseSegment("seg-1.ts.bak"))
        assertNull(HlsNames.parseSegment("seg-.ts"))
    }

    @Test fun `M3U limpia comillas y saltos y exige http`() {
        val m = M3uBuilder.build(listOf(M3uChannel("a\"b", "Mi\ncanal, \"x\"", "http://192.168.1.5:8081/t/index.m3u8")))
        assertEquals(
            "#EXTM3U\n#EXTINF:-1 tvg-id=\"a'b\" tvg-name=\"Mi canal 'x'\" group-title=\"OpenTV\",Mi canal 'x'\nhttp://192.168.1.5:8081/t/index.m3u8\n",
            m,
        )
        try { M3uBuilder.build(listOf(M3uChannel("x", "x", "file:///etc/passwd"))); org.junit.Assert.fail() } catch (_: IllegalArgumentException) {}
    }

    @Test fun `router exige el token y solo GET y HEAD`() {
        val r = IptvRouter("abc", HlsSegmenter(), status) { 5_000L }
        assertEquals(404, (r.route("GET", "/index.m3u8", null) as IptvResponse.Body).status)
        assertEquals(404, (r.route("GET", "/zzz/index.m3u8", null) as IptvResponse.Body).status)
        assertEquals(405, (r.route("POST", "/abc/index.m3u8", null) as IptvResponse.Body).status)
        assertEquals(503, (r.route("GET", "/abc/index.m3u8", null) as IptvResponse.Body).status)
    }

    @Test fun `router entrega playlist segmento y M3U con la direccion del Host`() {
        val seg = HlsSegmenter(HlsConfig(targetSegmentMs = 1_000))
        feed(seg, 3)
        val r = IptvRouter("tok", seg, status) { 5_000L }
        val pl = r.route("GET", "/tok/index.m3u8?x=1", "h:1") as IptvResponse.Body
        assertEquals(200, pl.status)
        assertEquals("application/vnd.apple.mpegurl", pl.contentType)
        val s0 = r.route("GET", "/tok/seg-0.ts", null) as IptvResponse.Body
        assertEquals("video/MP2T", s0.contentType)
        assertEquals(0, s0.body.size % 188)
        assertEquals(404, (r.route("GET", "/tok/seg-999.ts", null) as IptvResponse.Body).status)
        val m3u = String((r.route("GET", "/tok/channel.m3u", "192.168.1.20:8081") as IptvResponse.Body).body)
        assertTrue(m3u.contains("http://192.168.1.20:8081/tok/index.m3u8"))
        val compat = String((r.route("GET", "/tok/compat.m3u", "x:1") as IptvResponse.Body).body)
        assertTrue(compat.contains("/tok/live.ts") && compat.contains("/tok/index.m3u8"))
        assertEquals(IptvResponse.LiveTs, r.route("GET", "/tok/live.ts", null))
    }

    @Test fun `rangos de bytes`() {
        assertEquals(0..9, IptvRouter.parseRange("bytes=0-9", 100))
        assertEquals(90..99, IptvRouter.parseRange("bytes=90-", 100))
        assertEquals(95..99, IptvRouter.parseRange("bytes=-5", 100))
        assertEquals(0..99, IptvRouter.parseRange("bytes=0-500", 100))
        assertNull(IptvRouter.parseRange("bytes=100-", 100))
        assertNull(IptvRouter.parseRange("bytes=9-3", 100))
        assertNull(IptvRouter.parseRange("items=0-3", 100))
        assertNull(IptvRouter.parseRange("bytes=-", 100))
    }

    @Test fun `router responde 206 y 416 en segmentos`() {
        val seg = HlsSegmenter(HlsConfig(targetSegmentMs = 1_000))
        feed(seg, 3)
        val r = IptvRouter("t", seg, status) { 0L }
        val part = r.route("GET", "/t/seg-0.ts", null, "bytes=0-187") as IptvResponse.Body
        assertEquals(206, part.status); assertEquals(188, part.body.size)
        assertEquals(416, (r.route("GET", "/t/seg-0.ts", null, "bytes=99999999-") as IptvResponse.Body).status)
    }

    @Test fun `status json con duracion y sin datos privados`() {
        val r = IptvRouter("t", HlsSegmenter(), status) { 11_000L }
        val j = String((r.route("GET", "/t/status.json", null) as IptvResponse.Body).body)
        assertTrue(j.contains("\"uptimeSeconds\":10") && j.contains("\"width\":1280"))
        assertFalse(j.contains("\"t\""))
    }

    // --- sockets reales en loopback ---------------------------------------------------------

    private fun http(port: Int, request: String): Pair<String, ByteArray> {
        Socket("127.0.0.1", port).use { s ->
            s.soTimeout = 3_000
            s.getOutputStream().write(request.toByteArray()); s.getOutputStream().flush()
            val all = s.getInputStream().readBytes()
            val split = String(all, Charsets.ISO_8859_1).indexOf("\r\n\r\n")
            return String(all, 0, split, Charsets.ISO_8859_1) to all.copyOfRange(split + 4, all.size)
        }
    }

    @Test fun `servidor real sirve playlist y segmento y rechaza clientes no permitidos`() {
        val seg = HlsSegmenter(HlsConfig(targetSegmentMs = 1_000))
        feed(seg, 3)
        val hub = StreamHub()
        val server = IptvServer(IptvRouter("tok", seg, status) { 0L }, hub, acceptClient = { it.isLoopbackAddress })
        val port = server.start(InetAddress.getByName("127.0.0.1"))
        try {
            val (h, body) = http(port, "GET /tok/index.m3u8 HTTP/1.1\r\nHost: x\r\nConnection: close\r\n\r\n")
            assertTrue(h.startsWith("HTTP/1.1 200 OK"))
            assertTrue(h.contains("Content-Length: ${body.size}"))
            assertTrue(String(body).contains("seg-0.ts"))
            val (h2, b2) = http(port, "GET /tok/seg-1.ts HTTP/1.1\r\nHost: x\r\nConnection: close\r\n\r\n")
            assertTrue(h2.contains("video/MP2T")); assertEquals(0, b2.size % 188)
            val (h3, _) = http(port, "HEAD /tok/seg-1.ts HTTP/1.1\r\nHost: x\r\nConnection: close\r\n\r\n")
            assertTrue(h3.startsWith("HTTP/1.1 200"))
            val (h4, _) = http(port, "GET /nope/x HTTP/1.1\r\nHost: x\r\nConnection: close\r\n\r\n")
            assertTrue(h4.startsWith("HTTP/1.1 404"))
            assertTrue(server.history.isNotEmpty())
        } finally { server.stop() }

        val closed = IptvServer(IptvRouter("tok", seg, status) { 0L }, hub, acceptClient = { false })
        val p2 = closed.start(InetAddress.getByName("127.0.0.1"))
        try {
            Socket("127.0.0.1", p2).use { s ->
                s.soTimeout = 2_000
                assertEquals(-1, s.getInputStream().read()) // cerrado sin responder
            }
        } finally { closed.stop() }
    }

    @Test fun `conexion persistente atiende dos pedidos seguidos`() {
        val seg = HlsSegmenter(HlsConfig(targetSegmentMs = 1_000))
        feed(seg, 3)
        val server = IptvServer(IptvRouter("t", seg, status) { 0L }, StreamHub(), acceptClient = { true })
        val port = server.start(InetAddress.getByName("127.0.0.1"))
        try {
            Socket("127.0.0.1", port).use { s ->
                s.soTimeout = 3_000
                val out = s.getOutputStream()
                out.write("GET /t/index.m3u8 HTTP/1.1\r\nHost: x\r\n\r\nGET /t/status.json HTTP/1.1\r\nHost: x\r\nConnection: close\r\n\r\n".toByteArray())
                out.flush()
                val text = String(s.getInputStream().readBytes(), Charsets.ISO_8859_1)
                assertEquals(2, Regex("HTTP/1.1 200 OK").findAll(text).count())
            }
        } finally { server.stop() }
    }

    @Test fun `TS continuo entrega lo que publica el hub desde el primer fotograma clave`() {
        val hub = StreamHub()
        val server = IptvServer(IptvRouter("t", HlsSegmenter(), status) { 0L }, hub, acceptClient = { true })
        val port = server.start(InetAddress.getByName("127.0.0.1"))
        try {
            Socket("127.0.0.1", port).use { s ->
                s.soTimeout = 3_000
                s.getOutputStream().write("GET /t/live.ts HTTP/1.1\r\nHost: x\r\n\r\n".toByteArray()); s.getOutputStream().flush()
                // esperar a que el servidor se suscriba
                var waited = 0
                while (hub.subscriberCount == 0 && waited < 2_000) { Thread.sleep(20); waited += 20 }
                assertEquals(1, hub.subscriberCount)
                hub.publish(ByteArray(188) { 7 }, keyStart = false) // se ignora: aún no hubo fotograma clave
                hub.publish(ByteArray(188) { 9 }, keyStart = true)
                val buf = ByteArray(2048)
                val sb = java.io.ByteArrayOutputStream()
                val deadline = System.currentTimeMillis() + 2_000
                while (System.currentTimeMillis() < deadline && !endsWith188(sb)) {
                    val n = try { s.getInputStream().read(buf) } catch (e: java.net.SocketTimeoutException) { -1 }
                    if (n > 0) sb.write(buf, 0, n) else break
                }
                val all = sb.toByteArray()
                val idx = String(all, Charsets.ISO_8859_1).indexOf("\r\n\r\n") + 4
                assertTrue(String(all, 0, idx - 4, Charsets.ISO_8859_1).contains("video/MP2T"))
                assertEquals(188, all.size - idx)
                assertEquals(9, all[idx].toInt())
            }
        } finally { server.stop() }
    }

    private fun endsWith188(sb: java.io.ByteArrayOutputStream): Boolean {
        val all = sb.toByteArray()
        val idx = String(all, Charsets.ISO_8859_1).indexOf("\r\n\r\n")
        return idx >= 0 && all.size - (idx + 4) >= 188
    }
}
