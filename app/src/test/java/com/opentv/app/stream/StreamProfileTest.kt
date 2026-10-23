package com.opentv.app.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamProfileTest {

    @Test
    fun h264ProfilesNeverClaimMpeg2() {
        StreamProfile.values().forEach {
            assertFalse(it.label, it.protocolInfo().contains("MPEG_TS_HD_NA_ISO") && !it.protocolInfo().contains("AVC_"))
            assertFalse(it.protocolInfo().contains("MPEG_TS_SD"))
        }
    }

    @Test
    fun protocolInfoAndFeaturesMatch() {
        val p = StreamProfile.AVC_TS
        assertTrue(p.protocolInfo().startsWith("http-get:*:video/mpeg:DLNA.ORG_PN=AVC_TS_HD_NA_ISO;"))
        assertEquals("DLNA.ORG_PN=AVC_TS_HD_NA_ISO;DLNA.ORG_OP=00;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000", p.contentFeatures())
        assertEquals("http-get:*:video/mpeg:*", StreamProfile.GENERIC.protocolInfo())
        assertNull(StreamProfile.GENERIC.contentFeatures())
        assertTrue(StreamProfile.DLNA_TTS.protocolInfo().contains("video/vnd.dlna.mpeg-tts"))
    }

    @Test
    fun cyclesThroughEveryProfile() {
        var p = StreamProfile.AVC_TS
        val seen = mutableSetOf<StreamProfile>()
        repeat(StreamProfile.values().size) { seen += p; p = p.next() }
        assertEquals(StreamProfile.values().toSet(), seen)
        assertEquals(StreamProfile.AVC_TS, p)
    }

    @Test
    fun resolutionsAreSixteenByNine() {
        StreamProfile.values().forEach { assertEquals(16 * it.height, 9 * it.width) }
        assertFalse(StreamProfile.NO_META.sendMetadata)
    }
}
