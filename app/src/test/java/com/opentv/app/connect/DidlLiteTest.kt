package com.opentv.app.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DidlLiteTest {

    private val service = "urn:schemas-upnp-org:service:AVTransport:1"

    @Test
    fun streamUrlGetsLiveTsProtocolInfo() {
        assertEquals(DidlLite.TS_LIVE_PROTOCOL_INFO, DidlLite.protocolInfoFor("http://192.168.1.5:8000/screen-abc.ts"))
        assertEquals("http-get:*:video/mp4:*", DidlLite.protocolInfoFor("http://a/b.MP4?x=1"))
        assertEquals("http-get:*:*:*", DidlLite.protocolInfoFor("http://a/stream"))
    }

    @Test
    fun metadataIsWellFormedAndEscaped() {
        val xml = DidlLite.forUrl("http://192.168.1.5/v.mp4?a=1&b=2", "Mi <video> & más")
        assertTrue(xml.startsWith("<DIDL-Lite"))
        assertTrue(xml.contains("Mi &lt;video&gt; &amp; más"))
        assertTrue(xml.contains("?a=1&amp;b=2"))
        assertTrue(xml.contains("object.item.videoItem"))
        assertTrue(DidlLite.forUrl("http://a/x.mp3", "t").contains("object.item.audioItem"))
        // Debe poder leerse como XML.
        javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(xml.byteInputStream())
    }

    @Test
    fun setUriEmbedsEscapedMetadataOnlyWhenGiven() {
        val without = DlnaSoap.setUri(service, "http://192.168.1.5/v.mp4")
        assertTrue(without.contains("<CurrentURIMetaData></CurrentURIMetaData>"))
        val didl = DidlLite.forUrl("http://192.168.1.5/v.mp4", "t")
        val with = DlnaSoap.setUri(service, "http://192.168.1.5/v.mp4", didl)
        assertTrue(with.contains("<CurrentURIMetaData>&lt;DIDL-Lite"))
        assertFalse(with.contains("<DIDL-Lite"))
    }
}
