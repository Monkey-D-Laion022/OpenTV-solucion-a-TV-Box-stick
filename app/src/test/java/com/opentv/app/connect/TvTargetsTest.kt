package com.opentv.app.connect

import org.junit.Assert.assertEquals
import org.junit.Test

class TvTargetsTest {

    private fun upnp(udn: String, name: String) = UpnpDevice(
        udn = udn,
        friendlyName = name,
        manufacturer = "",
        modelName = "",
        descriptionLocation = "http://192.168.1.2/d.xml",
        avTransportServiceType = "urn:schemas-upnp-org:service:AVTransport:1",
        controlEndpoint = LanEndpoint("192.168.1.2", 80, "/c"),
    )

    @Test
    fun mergesAndSortsByNameThenProtocol() {
        val list = TvTargets.build(
            cast = listOf(CastDevice("c1", "sala"), CastDevice("c2", "Cuarto")),
            dlna = listOf(upnp("u1", "Sala"), upnp("u2", "Balcón")),
        )
        assertEquals(
            listOf("Balcón", "Cuarto", "Sala", "sala"),
            list.map { it.name },
        )
        // Mismo nombre sin importar mayúsculas: se desempata por protocolo (DLNA antes que Google Cast).
        assertEquals(listOf("DLNA", "DLNA", "Google Cast", "Google Cast").size, list.size)
        assertEquals("DLNA", list[2].protocol)
    }

    @Test
    fun sameNameInBothMethodsStaysAsTwoEntries() {
        val list = TvTargets.build(listOf(CastDevice("c1", "TV")), listOf(upnp("u1", "TV")))
        assertEquals(listOf("DLNA", "Google Cast"), list.map { it.protocol })
    }

    @Test
    fun removesDuplicateKeysAndHandlesEmpty() {
        assertEquals(0, TvTargets.build(emptyList(), emptyList()).size)
        assertEquals(1, TvTargets.build(listOf(CastDevice("c1", "A"), CastDevice("c1", "A")), emptyList()).size)
    }
}
