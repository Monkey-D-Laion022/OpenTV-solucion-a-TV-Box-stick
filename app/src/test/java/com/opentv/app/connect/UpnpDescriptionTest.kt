package com.opentv.app.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class UpnpDescriptionTest {

    private val location = "http://192.168.1.50:49152/dmr/desc.xml"

    private fun description(
        deviceType: String = "urn:schemas-upnp-org:device:MediaRenderer:1",
        serviceType: String = "urn:schemas-upnp-org:service:AVTransport:1",
        controlUrl: String = "/upnp/control/AVTransport1",
        friendlyName: String = "TV Sala",
        extra: String = "",
    ) = """<?xml version="1.0"?>
<root xmlns="urn:schemas-upnp-org:device-1-0">
  <device>
    <deviceType>$deviceType</deviceType>
    <friendlyName>$friendlyName</friendlyName>
    <manufacturer>Acme</manufacturer>
    <modelName>X1</modelName>
    <UDN>uuid:1234</UDN>
    <serviceList>
      <service>
        <serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>
        <controlURL>/upnp/control/RenderingControl1</controlURL>
      </service>
      <service>
        <serviceType>$serviceType</serviceType>
        <controlURL>$controlUrl</controlURL>
      </service>
    </serviceList>
  </device>
  $extra
</root>"""

    @Test
    fun parsesAMediaRenderer() {
        val device = UpnpDescription.parse(description(), location)
        assertNotNull(device)
        device!!
        assertEquals("TV Sala", device.friendlyName)
        assertEquals("Acme", device.manufacturer)
        assertEquals("X1", device.modelName)
        assertEquals("uuid:1234", device.udn)
        assertEquals("urn:schemas-upnp-org:service:AVTransport:1", device.avTransportServiceType)
        assertEquals(LanEndpoint("192.168.1.50", 49152, "/upnp/control/AVTransport1"), device.controlEndpoint)
    }

    @Test
    fun rejectsDevicesThatAreNotMediaRenderers() {
        assertNull(UpnpDescription.parse(description(deviceType = "urn:schemas-upnp-org:device:MediaServer:1"), location))
    }

    @Test
    fun rejectsRenderersWithoutAvTransport() {
        assertNull(
            UpnpDescription.parse(
                description(serviceType = "urn:schemas-upnp-org:service:ConnectionManager:1"),
                location,
            ),
        )
    }

    @Test
    fun rejectsControlUrlsPointingToAnotherHost() {
        assertNull(UpnpDescription.parse(description(controlUrl = "http://192.168.1.99/ctl"), location))
        assertNull(UpnpDescription.parse(description(controlUrl = "http://8.8.8.8/ctl"), location))
    }

    @Test
    fun acceptsAbsoluteControlUrlOnTheSameHost() {
        val device = UpnpDescription.parse(description(controlUrl = "http://192.168.1.50:7000/ctl"), location)
        assertEquals(LanEndpoint("192.168.1.50", 7000, "/ctl"), device!!.controlEndpoint)
    }

    @Test
    fun rejectsXmlEntityAndDoctypeAttacks() {
        val xxe = "<?xml version=\"1.0\"?><!DOCTYPE r [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>" +
            description().substringAfter("?>")
        assertNull(UpnpDescription.parse(xxe, location))
        assertNull(UpnpDescription.parse(description(extra = "<!ENTITY a \"b\">"), location))
    }

    @Test
    fun rejectsMalformedOrOversizedXml() {
        assertNull(UpnpDescription.parse("<root><device>", location))
        assertNull(UpnpDescription.parse("", location))
        assertNull(UpnpDescription.parse("no es xml", location))
        assertNull(UpnpDescription.parse(description(extra = "<!-- " + "a".repeat(300_000) + " -->"), location))
    }

    @Test
    fun rejectsUntrustedLocation() {
        assertNull(UpnpDescription.parse(description(), "http://8.8.8.8/desc.xml"))
        assertNull(UpnpDescription.parse(description(), "not a url"))
    }

    @Test
    fun cleansAndLimitsTheDeviceName() {
        val long = "N".repeat(200)
        assertEquals(80, UpnpDescription.parse(description(friendlyName = long), location)!!.friendlyName.length)
        // El tabulador es un carácter de control válido en XML: se elimina del nombre.
        assertEquals("TV Sala", UpnpDescription.parse(description(friendlyName = "  TV Sala\t "), location)!!.friendlyName)
    }

    @Test
    fun descriptionWithControlCharactersInvalidInXmlIsDropped() {
        // U+0007 no existe en XML 1.0: el analizador rechaza el documento y el equipo se descarta.
        assertNull(UpnpDescription.parse(description(friendlyName = "TV\u0007Sala"), location))
    }

    @Test
    fun rejectsServiceTypesWithUnexpectedCharacters() {
        assertNull(
            UpnpDescription.parse(
                description(serviceType = "urn:schemas-upnp-org:service:AVTransport:1&amp;&lt;x"),
                location,
            ),
        )
    }
}
