package com.opentv.app.iptv

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoLevelsTest {
    @Test fun nivel720p60Es32() = assertEquals(AvcLevels.L32, AvcLevels.forStream(1280, 720, 60))
    @Test fun nivel720p30Es31() = assertEquals(AvcLevels.L31, AvcLevels.forStream(1280, 720, 30))
    @Test fun nivel1080p30Es40() = assertEquals(AvcLevels.L40, AvcLevels.forStream(1920, 1080, 30))
    @Test fun nivel1080p60Es42() = assertEquals(AvcLevels.L42, AvcLevels.forStream(1920, 1080, 60))
    @Test fun nivelImposibleEsNulo() = assertNull(AvcLevels.forStream(8192, 4320, 120))
    @Test fun nombresDeNivel() { assertEquals("4.2", AvcLevels.name(AvcLevels.L42)); assertEquals("?", AvcLevels.name(1)) }

    @Test fun pickerRespetaLoPedidoSiSeSoporta() {
        val c = IptvProfilePicker.pick(IptvPreset.FULL_HD_60) { _, _, _ -> true }
        assertEquals(IptvPreset.FULL_HD_60, c.preset); assertNull(c.note)
    }
    @Test fun pickerBajaDe1080p60A720p60() {
        val c = IptvProfilePicker.pick(IptvPreset.FULL_HD_60) { w, _, _ -> w <= 1280 }
        assertEquals(IptvPreset.HD_60, c.preset); assertTrue(c.note!!.contains("1080p"))
    }
    @Test fun pickerBajaA30SiNoHay60() {
        val c = IptvProfilePicker.pick(IptvPreset.HD_60) { _, _, f -> f <= 30 }
        assertEquals(IptvPreset.BALANCED, c.preset)
    }
    @Test fun pickerNuncaSubeDeLoPedido() {
        val c = IptvProfilePicker.pick(IptvPreset.SAFE) { _, _, _ -> true }
        assertEquals(IptvPreset.SAFE, c.preset)
    }
    @Test fun pickerSinSoporteConservaLoPedidoYAvisa() {
        val c = IptvProfilePicker.pick(IptvPreset.BALANCED) { _, _, _ -> false }
        assertEquals(IptvPreset.BALANCED, c.preset); assertTrue(c.note != null)
    }

    private fun pcm(vararg samples: Int): ByteArray {
        val out = ByteArray(samples.size * 2)
        samples.forEachIndexed { i, v -> out[2 * i] = (v and 0xFF).toByte(); out[2 * i + 1] = (v shr 8).toByte() }
        return out
    }
    @Test fun ceroEsSilencio() { assertFalse(AudioActivityMeter.isSound(ByteArray(4096))); assertEquals(-96.0, AudioActivityMeter.rmsDb(ByteArray(64)), 0.0) }
    @Test fun ondaFuerteEsSonido() = assertTrue(AudioActivityMeter.isSound(pcm(*IntArray(512) { if (it % 2 == 0) 12000 else -12000 })))
    @Test fun ruidoMinimoNoCuenta() = assertFalse(AudioActivityMeter.isSound(pcm(*IntArray(512) { if (it % 2 == 0) 1 else -1 })))
    @Test fun estadoDeAudio() {
        assertEquals(IptvAudio.SILENT_TRACK, AudioActivityMeter.state(false, 1_000, 2_000))
        assertEquals(IptvAudio.CAPTURING_NO_SOUND, AudioActivityMeter.state(true, 0, 2_000))
        assertEquals(IptvAudio.CAPTURING_NO_SOUND, AudioActivityMeter.state(true, 1_000, 20_000))
        assertEquals(IptvAudio.CAPTURING_SOUND, AudioActivityMeter.state(true, 19_000, 20_000))
    }

    @Test fun accesoCortoUsaTv() { assertEquals("tv", IptvAccess.SHORT.fixedToken); assertNull(IptvAccess.CODE.fixedToken); assertEquals(IptvAccess.CODE, IptvAccess.SHORT.next()) }
    @Test fun presetsDe60Fps() { assertEquals(60, IptvPreset.HD_60.fps); assertEquals(60, IptvPreset.FULL_HD_60.fps); assertEquals(IptvPreset.SAFE, IptvPreset.FULL_HD_60.next()) }

    @Test fun externoDistingueElPropioTelefono() {
        val own = InetAddress.getByName("192.168.1.20")
        assertFalse(IptvServer.isExternal(InetAddress.getByName("192.168.1.20"), own))
        assertFalse(IptvServer.isExternal(InetAddress.getByName("127.0.0.1"), own))
        assertTrue(IptvServer.isExternal(InetAddress.getByName("192.168.1.33"), own))
    }
}
