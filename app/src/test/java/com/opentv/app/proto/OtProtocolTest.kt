package com.opentv.app.proto

import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OtProtocolTest {

    private val idS = OtIdentity(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8), "Teléfono", OtCaps.H264 or OtCaps.MPEG_TS)
    private val idR = OtIdentity(byteArrayOf(9, 9, 9, 9, 9, 9, 9, 9), "TV sala", OtCaps.H264 or OtCaps.MPEG_TS)

    // --- tramas ---
    @Test fun `trama ida y vuelta y lectura en pedazos`() {
        val a = OtFrameCodec.encode(OtFrame(OtWire.Type.TS_DATA, OtWire.FLAG_KEY_START, ByteArray(500) { it.toByte() }))
        val b = OtFrameCodec.encode(OtFrame(OtWire.Type.PING, 0, ByteArray(8) { 7 }))
        val r = OtFrameReader()
        val all = a + b
        val got = ArrayList<OtFrame>()
        for (byte in all) got += r.feed(byteArrayOf(byte))
        assertEquals(2, got.size)
        assertEquals(OtWire.FLAG_KEY_START, got[0].flags)
        assertArrayEquals(ByteArray(500) { it.toByte() }, got[0].payload)
        assertEquals(0, r.pendingBytes)
    }

    @Test fun `rechaza tipo desconocido y largo excesivo`() {
        try { OtFrameReader().feed(byteArrayOf(0x77, 0, 0, 0, 0, 1, 0)); fail() } catch (_: OtProtocolException) {}
        try { OtFrameReader().feed(byteArrayOf(0x20, 0, 0x7F, 0, 0, 0)); fail() } catch (_: OtProtocolException) {}
    }

    @Test fun `mensajes ida y vuelta`() {
        val h = OtHello(OtWire.ROLE_SENDER, idS.deviceId, "Teléfono ñ", 7, ByteArray(16) { 3 }, OtWire.MODE_RESUME, ByteArray(0))
        assertEquals(h, OtHello.decode(h.encode()))
        val o = OtOffer(1, 1280, 720, 30, 3_000_000, 1, 1)
        assertEquals(o, OtOffer.decode(o.encode()))
        val a = OtAccept(true, 2_500_000, 30, 250)
        assertEquals(a, OtAccept.decode(a.encode()))
        val s = OtStats(120, 3, 9)
        assertEquals(s, OtStats.decode(s.encode()))
        val c = OtControl(OtWire.Control.SET_BITRATE, 1_500_000)
        assertEquals(c, OtControl.decode(c.encode()))
        assertEquals(OtError(2, "no"), OtError.decode(OtError(2, "no").encode()))
    }

    @Test fun `mensajes truncados o inconsistentes se rechazan`() {
        val h = OtHello(OtWire.ROLE_SENDER, idS.deviceId, "x", 1, ByteArray(16), OtWire.MODE_RESUME, ByteArray(0)).encode()
        try { OtHello.decode(h.copyOf(h.size - 3)); fail() } catch (_: OtProtocolException) {}
        val badVersion = h.copyOf().also { it[0] = 9 }
        try { OtHello.decode(badVersion); fail() } catch (_: OtProtocolException) {}
        try { OtOffer.decode(ByteArray(3)); fail() } catch (_: OtProtocolException) {}
        // modo emparejar sin clave pública
        val noKey = OtHello(OtWire.ROLE_RECEIVER, idR.deviceId, "x", 1, ByteArray(16), OtWire.MODE_PAIR, ByteArray(0)).encode()
        try { OtHello.decode(noKey); fail() } catch (_: OtProtocolException) {}
    }

    // --- criptografía ---
    @Test fun `HKDF coincide con el vector de la RFC 5869 caso 1`() {
        fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        val okm = OtCrypto.hkdf(hex("000102030405060708090a0b0c"), ByteArray(22) { 0x0b }, hex("f0f1f2f3f4f5f6f7f8f9"), 42)
        assertEquals(
            "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865",
            okm.joinToString("") { "%02x".format(it) },
        )
    }

    @Test fun `ECDH da el mismo secreto y la clave publica se codifica a 65 bytes`() {
        val a = OtCrypto.generateKeyPair(); val b = OtCrypto.generateKeyPair()
        val pa = OtCrypto.encodePublic(a.public)
        assertEquals(65, pa.size); assertEquals(4, pa[0].toInt())
        val z1 = OtCrypto.agree(a.private, OtCrypto.decodePublic(OtCrypto.encodePublic(b.public)))
        val z2 = OtCrypto.agree(b.private, OtCrypto.decodePublic(pa))
        assertArrayEquals(z1, z2); assertEquals(32, z1.size)
    }

    @Test fun `punto fuera de la curva se rechaza`() {
        val bad = ByteArray(65).also { it[0] = 4; it[64] = 1 }
        try { OtCrypto.decodePublic(bad); fail() } catch (_: OtProtocolException) {}
    }

    @Test fun `cifrado detecta alteracion de carga cabecera y orden`() {
        val key = ByteArray(32) { it.toByte() }
        val tx = OtFrameCipher(key, 1); val rx = OtFrameCipher(key, 1)
        val f1 = tx.seal(OtFrame(OtWire.Type.TS_DATA, 1, "hola".toByteArray()))
        val f2 = tx.seal(OtFrame(OtWire.Type.TS_DATA, 0, "mundo".toByteArray()))
        assertEquals("hola", String(rx.open(f1).payload))
        assertEquals("mundo", String(rx.open(f2).payload))
        // alterar un bit
        val t2 = OtFrameCipher(key, 1); val r2 = OtFrameCipher(key, 1)
        val s = t2.seal(OtFrame(OtWire.Type.TS_DATA, 0, ByteArray(40) { 5 }))
        val flipped = OtFrame(s.type, s.flags, s.payload.copyOf().also { it[3] = (it[3].toInt() xor 1).toByte() })
        try { r2.open(flipped); fail() } catch (_: OtProtocolException) {}
        // alterar flags (va en el AAD)
        val t3 = OtFrameCipher(key, 1); val r3 = OtFrameCipher(key, 1)
        val s3 = t3.seal(OtFrame(OtWire.Type.TS_DATA, 0, ByteArray(10)))
        try { r3.open(OtFrame(s3.type, 1, s3.payload)); fail() } catch (_: OtProtocolException) {}
        // repetir una trama (contador distinto)
        val t4 = OtFrameCipher(key, 1); val r4 = OtFrameCipher(key, 1)
        val s4 = t4.seal(OtFrame(OtWire.Type.PING, 0, ByteArray(8)))
        r4.open(s4)
        try { r4.open(s4); fail() } catch (_: OtProtocolException) {}
    }

    @Test fun `el SAS depende de ambos saludos y tiene 6 digitos`() {
        val z = ByteArray(32) { 1 }
        val a = OtCrypto.sas(z, byteArrayOf(1), byteArrayOf(2))
        assertEquals(6, a.length); assertTrue(a.all { it.isDigit() })
        assertEquals(a, OtCrypto.sas(z, byteArrayOf(1), byteArrayOf(2)))
        assertFalse(a == OtCrypto.sas(z, byteArrayOf(1), byteArrayOf(3)))
    }

    // --- protocolo completo por sockets reales ---
    private class Pair2(val sender: OtSecureSession?, val receiver: OtSecureSession?, val senderError: Throwable?, val receiverError: Throwable?)

    private fun connect(
        storeS: OtPeerStore, storeR: OtPeerStore, pair: Boolean,
        sasS: (String) -> Boolean = { true }, sasR: (String) -> Boolean = { true },
        sasSeenS: MutableList<String> = ArrayList(), sasSeenR: MutableList<String> = ArrayList(),
    ): Pair2 {
        val pool = Executors.newFixedThreadPool(2)
        ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1")).use { server ->
            val fs = pool.submit<Result<OtSecureSession>> {
                runCatching {
                    val c = server.accept().also { it.soTimeout = 5000 }
                    OtHandshake.asSender(OtChannel(c.getInputStream(), c.getOutputStream()), idS, storeS) { sasSeenS += it; sasS(it) }
                }
            }
            val fr = pool.submit<Result<OtSecureSession>> {
                runCatching {
                    val c = Socket("127.0.0.1", server.localPort).also { it.soTimeout = 5000 }
                    OtHandshake.asReceiver(OtChannel(c.getInputStream(), c.getOutputStream()), idR, storeR, pair) { sasSeenR += it; sasR(it) }
                }
            }
            val rs = fs.get(10, TimeUnit.SECONDS); val rr = fr.get(10, TimeUnit.SECONDS)
            pool.shutdownNow()
            return Pair2(rs.getOrNull(), rr.getOrNull(), rs.exceptionOrNull(), rr.exceptionOrNull())
        }
    }

    @Test fun `emparejamiento y reconexion con datos cifrados en ambos sentidos`() {
        val storeS = OtMemoryPeerStore(); val storeR = OtMemoryPeerStore()
        val seenS = ArrayList<String>(); val seenR = ArrayList<String>()
        val first = connect(storeS, storeR, pair = true, sasSeenS = seenS, sasSeenR = seenR)
        assertNotNull(first.sender); assertNotNull(first.receiver)
        assertEquals(seenS, seenR) // el usuario ve el mismo código en los dos equipos
        assertTrue(first.sender!!.justPaired)
        assertNotNull(storeS.secretFor(idR.deviceId)); assertNotNull(storeR.secretFor(idS.deviceId))
        assertArrayEquals(storeS.secretFor(idR.deviceId), storeR.secretFor(idS.deviceId))
        first.sender!!.send(OtWire.Type.TS_DATA, OtWire.FLAG_KEY_START, ByteArray(188) { 0x47 })
        val got = first.receiver!!.receive()!!
        assertEquals(OtWire.Type.TS_DATA, got.type); assertEquals(188, got.payload.size)
        first.receiver!!.send(OtWire.Type.STATS, 0, OtStats(100, 0, 0).encode())
        assertEquals(OtWire.Type.STATS, first.sender!!.receive()!!.type)

        val again = connect(storeS, storeR, pair = false)
        assertNotNull(again.sender); assertNotNull(again.receiver)
        assertFalse(again.sender!!.justPaired)
        again.sender!!.send(OtWire.Type.PING, 0, ByteArray(8))
        assertEquals(OtWire.Type.PING, again.receiver!!.receive()!!.type)
    }

    @Test fun `si el usuario rechaza el codigo no se guarda nada`() {
        val storeS = OtMemoryPeerStore(); val storeR = OtMemoryPeerStore()
        val r = connect(storeS, storeR, pair = true, sasR = { false })
        assertNull(r.sender); assertNull(r.receiver)
        assertNull(storeS.secretFor(idR.deviceId)); assertNull(storeR.secretFor(idS.deviceId))
    }

    @Test fun `receptor no emparejado no puede reconectar`() {
        val r = connect(OtMemoryPeerStore(), OtMemoryPeerStore(), pair = false)
        assertNull(r.sender); assertNull(r.receiver)
        assertTrue(r.receiverError is Exception)
    }

    @Test fun `secreto distinto en un lado falla la autenticacion`() {
        val storeS = OtMemoryPeerStore(); val storeR = OtMemoryPeerStore()
        storeS.save(idR.deviceId, "r", ByteArray(32) { 1 })
        storeR.save(idS.deviceId, "s", ByteArray(32) { 2 })
        val r = connect(storeS, storeR, pair = false)
        assertNull(r.sender); assertNull(r.receiver)
    }

    @Test fun `descubrimiento TXT ida y vuelta y entradas hostiles`() {
        val adv = OtDiscovery.Advert(OtDiscovery.deviceIdHex(idS.deviceId), "Teléfono", OtWire.ROLE_SENDER, 5, 8765)
        assertEquals(adv, OtDiscovery.parse(OtDiscovery.txt(adv), 8765))
        assertNull(OtDiscovery.parse(mapOf("v" to "2"), 8765))
        assertNull(OtDiscovery.parse(OtDiscovery.txt(adv) + ("id" to "zz"), 8765))
        assertNull(OtDiscovery.parse(OtDiscovery.txt(adv), 0))
        val n = OtDiscovery.parse(OtDiscovery.txt(adv) + ("n" to "a\u0000b\n"), 8765)!!
        assertEquals("ab", n.name)
    }

    // --- adaptación ---
    @Test fun `el bitrate baja con congestion y sube despacio sin pasar el maximo`() {
        val c = OtBitrateController(maxBps = 4_000_000, startBps = 4_000_000)
        assertNull(c.onStats(OtStats(100, 0, 0)))
        val down = c.onStats(OtStats(100, 5, 0))!!
        assertEquals(2_800_000L, down)
        var ups = 0L
        repeat(5) { c.onStats(OtStats(100, 5, 0))?.let { ups = it } }
        assertEquals(3_360_000L, ups)
        repeat(100) { c.onStats(OtStats(100, 5, 0)) }
        assertTrue(c.currentBps <= 4_000_000L)
        repeat(30) { c.onStats(OtStats(10_000, 5, 0)) } // búfer enorme = congestión
        assertEquals(500_000L, c.currentBps) // piso = max/8
    }

    @Test fun `la compuerta de envio descarta hasta el proximo fotograma clave`() {
        val g = OtSendGate(1000)
        assertTrue(g.allow(100, false))
        assertFalse(g.allow(2000, false)); assertTrue(g.isSkipping)
        assertFalse(g.allow(100, false))
        assertFalse(g.allow(900, true)) // clave pero la cola todavía no bajó a la mitad
        assertTrue(g.allow(100, true)); assertFalse(g.isSkipping)
    }
}
