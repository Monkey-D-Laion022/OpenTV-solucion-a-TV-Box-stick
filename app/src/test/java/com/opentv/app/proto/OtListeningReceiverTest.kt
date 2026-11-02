package com.opentv.app.proto

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Modo televisor: el RECEPTOR escucha y el TELÉFONO (emisor) se conecta. El saludo no depende de quién abre el TCP:
 * el receptor habla primero en cuanto acepta la conexión. Se prueba con sockets reales en 127.0.0.1.
 */
class OtListeningReceiverTest {
    private val idPhone = OtIdentity(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8), "Teléfono", OtCaps.H264 or OtCaps.MPEG_TS)
    private val idTv = OtIdentity(byteArrayOf(9, 9, 9, 9, 9, 9, 9, 9), "TV sala", OtCaps.H264 or OtCaps.MPEG_TS)

    private class Out(val phone: OtSecureSession?, val tv: OtSecureSession?, val phoneError: Throwable?, val tvError: Throwable?)

    private fun run(phoneStore: OtPeerStore, tvStore: OtPeerStore, tvPairs: Boolean): Out {
        val pool = Executors.newFixedThreadPool(2)
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            val tv = pool.submit<Result<OtSecureSession>> {
                runCatching {
                    val c = server.accept().also { it.soTimeout = 5000 }
                    OtHandshake.asReceiver(OtChannel(c.getInputStream(), c.getOutputStream()), idTv, tvStore, tvPairs) { true }
                }
            }
            val phone = pool.submit<Result<OtSecureSession>> {
                runCatching {
                    val c = Socket("127.0.0.1", server.localPort).also { it.soTimeout = 5000 }
                    OtHandshake.asSender(OtChannel(c.getInputStream(), c.getOutputStream()), idPhone, phoneStore) { true }
                }
            }
            val rp = phone.get(10, TimeUnit.SECONDS)
            val rt = tv.get(10, TimeUnit.SECONDS)
            pool.shutdownNow()
            return Out(rp.getOrNull(), rt.getOrNull(), rp.exceptionOrNull(), rt.exceptionOrNull())
        }
    }

    @Test fun `el telefono se conecta al televisor que escucha, se empareja y se reconecta`() {
        val phoneStore = OtMemoryPeerStore(); val tvStore = OtMemoryPeerStore()
        val first = run(phoneStore, tvStore, tvPairs = true)
        val phone1: OtSecureSession? = first.phone
        val tv1: OtSecureSession? = first.tv
        assertNotNull("emparejamiento: ${first.phoneError} / ${first.tvError}", phone1)
        assertNotNull(tv1)
        assertNotNull(phoneStore.secretFor(idTv.deviceId))
        assertNotNull(tvStore.secretFor(idPhone.deviceId))

        val second = run(phoneStore, tvStore, tvPairs = false)
        val phone2: OtSecureSession? = second.phone
        val tv2: OtSecureSession? = second.tv
        assertNotNull("reconexión: ${second.phoneError} / ${second.tvError}", phone2)
        assertNotNull(tv2)
        if (phone2 != null && tv2 != null) {
            val data = ByteArray(300) { it.toByte() }
            phone2.send(OtWire.Type.TS_DATA, OtWire.FLAG_KEY_START, data)
            val got = tv2.receive()
            assertNotNull(got)
            assertEquals(OtWire.Type.TS_DATA, got!!.type)
            assertArrayEquals(data, got.payload)
        }
    }

    @Test fun `un telefono nuevo es rechazado si el televisor no abrio el emparejamiento`() {
        val out = run(OtMemoryPeerStore(), OtMemoryPeerStore(), tvPairs = false)
        assertTrue("el teléfono debe fallar", out.phone == null)
        assertTrue("el televisor debe fallar", out.tv == null)
    }

    @Test fun `el puerto del receptor es distinto al del emisor y el anuncio de receptor se interpreta`() {
        assertTrue(OtDiscovery.RECEIVER_PORT != OtDiscovery.DEFAULT_PORT)
        val adv = OtDiscovery.Advert(OtDiscovery.deviceIdHex(idTv.deviceId), "TV sala", OtWire.ROLE_RECEIVER, 5, OtDiscovery.RECEIVER_PORT)
        assertEquals(adv, OtDiscovery.parse(OtDiscovery.txt(adv), OtDiscovery.RECEIVER_PORT))
    }
}
