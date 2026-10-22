package com.opentv.app.connect

import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DlnaControllerTest {

    private val tvXml = "<?xml version=\"1.0\"?><root xmlns=\"urn:schemas-upnp-org:device-1-0\"><device>" +
        "<deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>" +
        "<friendlyName>TV Sala</friendlyName><manufacturer>ACME</manufacturer>" +
        "<modelName>X1</modelName><UDN>uuid:1234</UDN><serviceList><service>" +
        "<serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>" +
        "<controlURL>/ctl/AVT</controlURL></service></serviceList></device></root>"

    private val location = "http://192.168.1.20:49152/desc.xml"

    private fun hit(sender: String = "192.168.1.20", loc: String = location) = SsdpHit(
        sender,
        SsdpResponse(location = loc, usn = "uuid:1234", st = Ssdp.MEDIA_RENDERER),
    )

    private class FakeSearch(private val hits: List<SsdpHit>) : SsdpSearch {
        override suspend fun search(timeoutMs: Long) = hits
    }

    /** Guarda cada mensaje enviado y responde con lo que decida [reply]. */
    private class FakeHttp(val reply: (String) -> HttpResponse) : LanHttp {
        val sent = mutableListOf<String>()
        override suspend fun request(endpoint: LanEndpoint, message: ByteArray): HttpResponse {
            val text = String(message, Charsets.ISO_8859_1)
            sent += text
            return reply(text)
        }
    }

    private fun ok(body: String = "") = HttpResponse(200, emptyMap(), body)

    private fun device(): UpnpDevice = UpnpDescription.parse(tvXml, location)!!

    @Test
    fun discoversAValidRenderer() {
        runBlocking {
            val http = FakeHttp { ok(tvXml) }
            val found = DlnaController(FakeSearch(listOf(hit())), http).discover()
            assertEquals(1, found.size)
            assertEquals("TV Sala", found[0].friendlyName)
            assertEquals(LanEndpoint("192.168.1.20", 49152, "/ctl/AVT"), found[0].controlEndpoint)
            assertTrue(http.sent.single().startsWith("GET /desc.xml HTTP/1.1"))
        }
    }

    @Test
    fun spoofedSendersAreNeverContacted() {
        runBlocking {
            val http = FakeHttp { ok(tvXml) }
            val spoofed = hit(sender = "192.168.1.50")
            val found = DlnaController(FakeSearch(listOf(spoofed)), http).discover()
            assertTrue(found.isEmpty())
            assertTrue(http.sent.isEmpty())
        }
    }

    @Test
    fun failedOrInvalidDescriptionsAreDropped() {
        runBlocking {
            val notFound = FakeHttp { HttpResponse(404, emptyMap(), "") }
            assertTrue(DlnaController(FakeSearch(listOf(hit())), notFound).discover().isEmpty())

            val garbage = FakeHttp { ok("<root><device></device></root>") }
            assertTrue(DlnaController(FakeSearch(listOf(hit())), garbage).discover().isEmpty())

            val broken = FakeHttp { throw IOException("sin conexión") }
            assertTrue(DlnaController(FakeSearch(listOf(hit())), broken).discover().isEmpty())
        }
    }

    @Test
    fun duplicateAnswersGiveOneDevice() {
        runBlocking {
            val http = FakeHttp { ok(tvXml) }
            val found = DlnaController(FakeSearch(listOf(hit(), hit(), hit())), http).discover()
            assertEquals(1, found.size)
            assertEquals(1, http.sent.size)
        }
    }

    @Test
    fun playSendsTheLinkAndThenPlaysInThatOrder() {
        runBlocking {
            val http = FakeHttp { ok() }
            val result = DlnaController(FakeSearch(emptyList()), http)
                .play(device(), "http://192.168.1.5:8000/video.mp4")
            assertEquals(DlnaResult.Ok, result)
            assertEquals(2, http.sent.size)
            assertTrue(http.sent[0].contains("#SetAVTransportURI\""))
            assertTrue(http.sent[0].contains("<CurrentURI>http://192.168.1.5:8000/video.mp4</CurrentURI>"))
            assertTrue(http.sent[1].contains("#Play\""))
        }
    }

    @Test
    fun playStopsAtTheFirstStepWhenTheTvRejectsTheLink() {
        runBlocking {
            val http = FakeHttp {
                HttpResponse(500, emptyMap(), "<errorCode>716</errorCode>")
            }
            val result = DlnaController(FakeSearch(emptyList()), http)
                .play(device(), "http://192.168.1.5/video.mp4")
            assertEquals(DlnaResult.Rejected(500, "código 716"), result)
            assertEquals(1, http.sent.size)
        }
    }

    @Test
    fun invalidLinksAreRejectedWithoutTouchingTheNetwork() {
        runBlocking {
            val http = FakeHttp { ok() }
            val controller = DlnaController(FakeSearch(emptyList()), http)
            assertTrue(controller.play(device(), "") is DlnaResult.Rejected)
            assertTrue(controller.play(device(), "ftp://x/y.mp4") is DlnaResult.Rejected)
            assertTrue(controller.play(device(), "http://user:pw@host/v.mp4") is DlnaResult.Rejected)
            assertTrue(http.sent.isEmpty())
        }
    }

    @Test
    fun unreachableTvIsReportedAndStopWorks() {
        runBlocking {
            val down = FakeHttp { throw IOException("Connection refused") }
            val controller = DlnaController(FakeSearch(emptyList()), down)
            assertEquals(DlnaResult.Unreachable("Connection refused"), controller.stop(device()))

            val up = FakeHttp { ok() }
            assertEquals(DlnaResult.Ok, DlnaController(FakeSearch(emptyList()), up).stop(device()))
            assertTrue(up.sent.single().contains("#Stop\""))
        }
    }
}
