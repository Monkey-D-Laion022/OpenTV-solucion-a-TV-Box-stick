package com.opentv.app.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectModesTest {
    private fun dlna(maker: String = "LG Electronics", model: String = "NetCast") = TvTarget.DlnaTarget(
        UpnpDevice("uuid:1", "LG TV", maker, model, "http://10.0.0.5/d.xml", "urn:schemas-upnp-org:service:AVTransport:1", LanEndpoint("10.0.0.5", 80, "/ctl")),
    )

    @Test
    fun dlnaOffersMirrorWithSoundAndProjection() {
        assertEquals(listOf(ConnectMode.DLNA_MIRROR, ConnectMode.DLNA_PROJECT), ConnectModes.forTarget(dlna()))
        assertTrue(ConnectMode.DLNA_MIRROR.title.contains("sonido"))
    }

    @Test
    fun castOffersOnlyLinkSendingAndPointsToNativeCastForMirroring() {
        val cast = TvTarget.CastTarget(CastDevice("id", "Sala"))
        assertEquals(listOf(ConnectMode.CAST_OUTPUT), ConnectModes.forTarget(cast))
        assertTrue(ConnectMode.CAST_OUTPUT.detail.contains("Emisión nativa"))
    }

    @Test
    fun summaryDescribesTheDevice() {
        assertEquals("DLNA · LG Electronics NetCast", ConnectModes.summary(dlna()))
        assertEquals("DLNA", ConnectModes.summary(dlna("", "")))
        assertEquals("Google Cast", ConnectModes.summary(TvTarget.CastTarget(CastDevice("i", "n"))))
    }

    @Test
    fun audioNoteOnlyBeforeAndroid10() {
        assertNotNull(ConnectModes.audioNote(28))
        assertNull(ConnectModes.audioNote(29))
        assertNull(ConnectModes.audioNote(34))
    }

    @Test
    fun alternativesWithoutNativeCastAreListed() {
        assertTrue(ConnectModes.WITHOUT_NATIVE_CAST.size >= 5)
    }
}
