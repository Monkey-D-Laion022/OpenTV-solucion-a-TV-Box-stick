package com.opentv.app.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SsdpTest {

    private fun datagram(vararg lines: String) =
        (lines.joinToString("\r\n") + "\r\n\r\n").toByteArray(Charsets.ISO_8859_1)

    @Test
    fun searchMessageIsAValidMSearch() {
        val text = String(Ssdp.buildSearch(), Charsets.ISO_8859_1)
        assertTrue(text.startsWith("M-SEARCH * HTTP/1.1\r\n"))
        assertTrue(text.contains("HOST: 239.255.255.250:1900\r\n"))
        assertTrue(text.contains("MAN: \"ssdp:discover\"\r\n"))
        assertTrue(text.contains("ST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n"))
        assertTrue(text.endsWith("\r\n\r\n"))
    }

    @Test
    fun searchRejectsInjectedTarget() {
        assertTrue(runCatching { Ssdp.buildSearch("x\r\nY: z") }.isFailure)
    }

    @Test
    fun parsesAValidResponseCaseInsensitively() {
        val response = Ssdp.parseResponse(
            datagram(
                "HTTP/1.1 200 OK",
                "CACHE-CONTROL: max-age=1800",
                "Location: http://192.168.1.50:49152/desc.xml",
                "ST: urn:schemas-upnp-org:device:MediaRenderer:1",
                "USN: uuid:abc::urn:schemas-upnp-org:device:MediaRenderer:1",
            ),
        )
        assertNotNull(response)
        assertEquals("http://192.168.1.50:49152/desc.xml", response!!.location)
        assertTrue(response.st.contains("MediaRenderer"))
        assertTrue(response.usn.startsWith("uuid:abc"))
    }

    @Test
    fun ignoresResponsesThatAreNotUsable() {
        assertNull(Ssdp.parseResponse(datagram("HTTP/1.1 404 Not Found", "LOCATION: http://192.168.1.5/d.xml")))
        assertNull(Ssdp.parseResponse(datagram("HTTP/1.1 200 OK", "ST: x")))
        assertNull(Ssdp.parseResponse(datagram("HTTP/1.1 200 OK", "LOCATION: http://8.8.8.8/d.xml")))
        assertNull(Ssdp.parseResponse(datagram("HTTP/1.1 200 OK", "LOCATION: https://192.168.1.5/d.xml")))
        assertNull(Ssdp.parseResponse(datagram("NOTIFY * HTTP/1.1", "LOCATION: http://192.168.1.5/d.xml")))
        assertNull(Ssdp.parseResponse(ByteArray(0)))
        assertNull(Ssdp.parseResponse(ByteArray(5000) { 'a'.code.toByte() }))
    }

    @Test
    fun respectsTheDatagramLength() {
        val raw = datagram("HTTP/1.1 200 OK", "LOCATION: http://192.168.1.5/d.xml")
        assertNotNull(Ssdp.parseResponse(raw, raw.size))
        assertNull(Ssdp.parseResponse(raw, raw.size + 10))
        assertNull(Ssdp.parseResponse(raw, 0))
    }

    @Test
    fun locationMustPointToTheSenderItself() {
        val response = SsdpResponse("http://192.168.1.50:49152/d.xml", "", "")
        assertTrue(Ssdp.isTrustedSource(response, "192.168.1.50"))
        assertFalse(Ssdp.isTrustedSource(response, "192.168.1.99"))
    }
}
