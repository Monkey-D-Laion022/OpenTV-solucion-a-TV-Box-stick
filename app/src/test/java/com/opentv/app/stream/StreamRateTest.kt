package com.opentv.app.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamRateTest {
    @Test
    fun rates() {
        assertEquals(30, StreamRate.FPS30.fps)
        assertEquals(60, StreamRate.FPS60.fps)
        assertTrue(StreamRate.FPS60.bitrate > StreamRate.FPS30.bitrate)
        assertEquals(StreamRate.FPS30, StreamRate.FPS15.next())
    }

    @Test
    fun only1080p60NeedsLevel42() {
        assertTrue(StreamRate.FPS60.needsLevel42(1080))
        assertFalse(StreamRate.FPS60.needsLevel42(720))
        assertFalse(StreamRate.FPS30.needsLevel42(1080))
    }

    @Test
    fun queueAndSendBufferScaleWithBitrate() {
        assertEquals(1_750_000, StreamRate.FPS60.hubQueueBytes(192 * 1024))
        assertEquals(192 * 1024 * 20, StreamRate.FPS15.hubQueueBytes(192 * 1024 * 20)) // nunca baja del valor base
        assertEquals(256 * 1024, StreamRate.FPS60.sendBufferBytes(16 * 1024))
        assertEquals(512 * 1024, StreamRate.FPS60.sendBufferBytes(512 * 1024))
    }

    private class Vol(override var level: Int) : VolumeControl
    private class Store(override var savedLevel: Int? = null) : MuteStore

    @Test
    fun muteSavesAndRestoresTheVolume() {
        val v = Vol(9); val s = Store()
        val m = PhoneMute(v, s)
        m.mute()
        assertEquals(0, v.level); assertTrue(m.isMuted)
        m.mute() // repetir no pisa el volumen original con 0
        assertEquals(9, s.savedLevel)
        m.restore()
        assertEquals(9, v.level); assertNull(s.savedLevel); assertFalse(m.isMuted)
        m.restore() // sin nada pendiente no toca el volumen
        assertEquals(9, v.level)
    }

    @Test
    fun aPendingSavedLevelFromACrashCanBeRestored() {
        val v = Vol(0); val s = Store(7)
        PhoneMute(v, s).restore()
        assertEquals(7, v.level)
    }

    @Test
    fun optionsDefaultsAreSafe() {
        val o = TransmitOptions()
        assertFalse(o.lockRotation.value); assertFalse(o.muteLocal.value)
        assertEquals(StreamRate.FPS30, o.rate.value)
        o.toggleLockRotation(); o.toggleMuteLocal(); o.cycleRate()
        assertTrue(o.lockRotation.value); assertTrue(o.muteLocal.value); assertEquals(StreamRate.FPS60, o.rate.value)
    }
}
