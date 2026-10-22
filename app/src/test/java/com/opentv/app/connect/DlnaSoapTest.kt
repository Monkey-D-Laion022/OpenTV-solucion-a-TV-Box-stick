package com.opentv.app.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DlnaSoapTest {

    private val service = "urn:schemas-upnp-org:service:AVTransport:1"

    @Test
    fun setUriEscapesTheUrl() {
        val body = DlnaSoap.setUri(service, "https://x.com/v.mp4?a=1&b=<2>\"'")
        assertTrue(body.contains("<CurrentURI>https://x.com/v.mp4?a=1&amp;b=&lt;2&gt;&quot;&apos;</CurrentURI>"))
        assertTrue(body.contains("<u:SetAVTransportURI xmlns:u=\"$service\">"))
        assertTrue(body.contains("<InstanceID>0</InstanceID>"))
    }

    @Test
    fun playAndStopUseTheRightArguments() {
        assertTrue(DlnaSoap.play(service).contains("<Speed>1</Speed>"))
        assertTrue(DlnaSoap.play(service).contains("<u:Play xmlns:u=\"$service\">"))
        assertTrue(DlnaSoap.stop(service).contains("<u:Stop xmlns:u=\"$service\">"))
    }

    @Test
    fun soapActionIsQuoted() {
        assertEquals("\"$service#Play\"", DlnaSoap.soapAction(service, "Play"))
    }

    @Test
    fun rejectsServiceTypeThatCouldBreakTheXml() {
        assertTrue(runCatching { DlnaSoap.play("urn:x\" onload=\"y") }.isFailure)
        assertTrue(runCatching { DlnaSoap.stop("") }.isFailure)
    }

    @Test
    fun extractsUpnpErrors() {
        val fault = "<s:Envelope><s:Body><s:Fault><detail><UPnPError><errorCode>714</errorCode>" +
            "<errorDescription>Illegal MIME-Type</errorDescription></UPnPError></detail></s:Fault></s:Body></s:Envelope>"
        assertEquals("Illegal MIME-Type (código 714)", DlnaSoap.errorDetail(fault))
        assertEquals("código 501", DlnaSoap.errorDetail("<errorCode>501</errorCode>"))
        assertNull(DlnaSoap.errorDetail("<html>nada</html>"))
        assertNull(DlnaSoap.errorDetail(""))
    }

    @Test
    fun messagesAreReadableForEveryResult() {
        assertTrue(DlnaMessages.describe("TV", DlnaSoap.ACTION_PLAY, DlnaResult.Ok).contains("Enviado a TV"))
        assertTrue(DlnaMessages.describe("TV", DlnaSoap.ACTION_STOP, DlnaResult.Ok).contains("detuvo"))
        assertTrue(
            DlnaMessages.describe("TV", DlnaSoap.ACTION_PLAY, DlnaResult.Rejected(500, "Illegal MIME-Type (código 714)"))
                .contains("(HTTP 500): Illegal MIME-Type"),
        )
        assertTrue(DlnaMessages.describe("TV", DlnaSoap.ACTION_PLAY, DlnaResult.Rejected(500, null)).endsWith("."))
        assertTrue(
            DlnaMessages.describe("TV", DlnaSoap.ACTION_PLAY, DlnaResult.Unreachable("tiempo agotado"))
                .contains("tiempo agotado"),
        )
    }
}
