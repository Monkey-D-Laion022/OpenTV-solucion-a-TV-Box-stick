package com.opentv.app.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpMessagesTest {

    private val endpoint = LanEndpoint("192.168.1.50", 49152, "/ctl/av")

    private fun bytes(text: String) = text.toByteArray(Charsets.UTF_8)

    @Test
    fun getRequestHasHostAndClosesTheConnection() {
        val text = String(HttpMessages.buildGet(endpoint), Charsets.ISO_8859_1)
        assertTrue(text.startsWith("GET /ctl/av HTTP/1.1\r\n"))
        assertTrue(text.contains("Host: 192.168.1.50:49152\r\n"))
        assertTrue(text.contains("Connection: close\r\n"))
        assertTrue(text.endsWith("\r\n\r\n"))
    }

    @Test
    fun postRequestHasCorrectContentLengthForNonAscii() {
        val body = "<a>ñ</a>"
        val raw = HttpMessages.buildPost(endpoint, "\"urn:x#Play\"", body)
        val text = String(raw, Charsets.ISO_8859_1)
        val expectedLength = body.toByteArray(Charsets.UTF_8).size
        assertTrue(text.contains("Content-Length: $expectedLength\r\n"))
        assertTrue(text.contains("SOAPAction: \"urn:x#Play\"\r\n"))
        assertTrue(raw.size == text.indexOf("\r\n\r\n") + 4 + expectedLength)
    }

    @Test
    fun rejectsHeaderInjection() {
        val bad = endpoint.copy(path = "/a HTTP/1.1\r\nX: y")
        assertTrue(runCatching { HttpMessages.buildGet(bad) }.isFailure)
        assertTrue(runCatching { HttpMessages.buildPost(endpoint, "x\r\nY: z", "") }.isFailure)
    }

    @Test
    fun parsesPlainResponseAndHonoursContentLength() {
        val response = HttpMessages.parseResponse(
            bytes("HTTP/1.1 200 OK\r\nContent-Length: 5\r\nX-A: b\r\n\r\nhello-extra"),
        )
        assertNotNull(response)
        assertEquals(200, response!!.status)
        assertEquals("hello", response.body)
        assertEquals("b", response.headers["x-a"])
    }

    @Test
    fun parsesChunkedResponse() {
        val raw = "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n" +
            "5\r\nhello\r\n6;ext=1\r\n world\r\n0\r\n\r\n"
        assertEquals("hello world", HttpMessages.parseResponse(bytes(raw))!!.body)
    }

    @Test
    fun parsesErrorStatusAndMissingBody() {
        val response = HttpMessages.parseResponse(bytes("HTTP/1.1 500 Internal Server Error\r\n\r\n"))
        assertEquals(500, response!!.status)
        assertEquals("", response.body)
    }

    @Test
    fun malformedResponsesReturnNull() {
        assertNull(HttpMessages.parseResponse(bytes("")))
        assertNull(HttpMessages.parseResponse(bytes("garbage")))
        assertNull(HttpMessages.parseResponse(bytes("HTTP/1.1 200 OK\r\nno-terminator")))
        assertNull(HttpMessages.parseResponse(bytes("NOTHTTP 200 OK\r\n\r\n")))
        assertNull(HttpMessages.parseResponse(bytes("HTTP/1.1 abc OK\r\n\r\n")))
    }

    @Test
    fun truncatedChunkedBodyDoesNotCrash() {
        val raw = "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\nFFFF\r\nabc"
        assertEquals("abc", HttpMessages.parseResponse(bytes(raw))!!.body)
        val garbage = "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\nzz\r\nabc"
        assertEquals("", HttpMessages.parseResponse(bytes(garbage))!!.body)
    }
}
