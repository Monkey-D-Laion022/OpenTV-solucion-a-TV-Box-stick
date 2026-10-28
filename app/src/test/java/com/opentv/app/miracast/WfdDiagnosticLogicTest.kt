package com.opentv.app.miracast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Los mensajes RTSP de estos tests están CONSTRUIDOS según el formato WFD; no son capturas de ningún televisor. */
class WfdDiagnosticLogicTest {

    private fun parse(vararg chunks: String): List<RtspMessage> {
        val p = RtspParser()
        val out = mutableListOf<RtspMessage>()
        for (c in chunks) {
            val b = c.toByteArray(Charsets.ISO_8859_1)
            p.feed(b, b.size)
            while (true) out += p.next() ?: break
        }
        return out
    }

    @Test
    fun parsesAResponseWithBody() {
        val body = "wfd_video_formats: 00 00 02 04 00000100 00000000 00000000 00 0000 0000 00 none none\r\n"
        val msgs = parse("RTSP/1.0 200 OK\r\nCSeq: 2\r\nContent-Type: text/parameters\r\nContent-Length: ${body.length}\r\n\r\n$body")
        assertEquals(1, msgs.size)
        val m = msgs[0]
        assertFalse(m.isRequest)
        assertEquals(200, m.statusCode)
        assertEquals(2, m.cseq)
        assertTrue(WfdRtsp.parseParameters(m.body).containsKey("wfd_video_formats"))
    }

    @Test
    fun parsesARequestSplitInManyChunks() {
        val whole = "OPTIONS * RTSP/1.0\r\nCSeq: 7\r\nRequire: org.wfa.wfd1.0\r\n\r\n"
        val chunks = whole.chunked(5).toTypedArray()
        val msgs = parse(*chunks)
        assertEquals(1, msgs.size)
        assertTrue(msgs[0].isRequest)
        assertEquals("OPTIONS", msgs[0].method)
        assertEquals(7, msgs[0].cseq)
        assertEquals("org.wfa.wfd1.0", msgs[0].header("Require"))
    }

    @Test
    fun parsesTwoMessagesInOneChunk() {
        val a = "OPTIONS * RTSP/1.0\r\nCSeq: 1\r\n\r\n"
        val b = "RTSP/1.0 200 OK\r\nCSeq: 1\r\n\r\n"
        val msgs = parse(a + b)
        assertEquals(listOf("OPTIONS *", "RTSP 200 OK"), msgs.map { it.summary })
    }

    @Test
    fun incompleteBodyWaitsForTheRest() {
        val p = RtspParser()
        val head = "RTSP/1.0 200 OK\r\nCSeq: 2\r\nContent-Length: 10\r\n\r\nabc".toByteArray()
        p.feed(head, head.size)
        assertNull(p.next())
        val rest = "defghij".toByteArray()
        p.feed(rest, rest.size)
        assertEquals("abcdefghij", p.next()!!.body)
    }

    @Test
    fun oversizedInputIsDiscardedAndFlagged() {
        val p = RtspParser()
        val big = ByteArray(RtspParser.MAX_BYTES + 1) { 'a'.code.toByte() }
        p.feed(big, big.size)
        assertTrue(p.overflow)
        assertNull(p.next())
    }

    @Test
    fun garbageNeverProducesAMessage() {
        assertTrue(parse("no es rtsp sin terminador").isEmpty())
    }

    @Test
    fun builtMessagesHaveTheExpectedShape() {
        assertEquals("OPTIONS * RTSP/1.0\r\nCSeq: 1\r\nRequire: org.wfa.wfd1.0\r\n\r\n", WfdRtsp.m1(1))
        val m2 = parse(WfdRtsp.m2Response(3))[0]
        assertEquals(200, m2.statusCode)
        assertEquals(3, m2.cseq)
        assertTrue(m2.header("public")!!.contains("GET_PARAMETER"))
        val m3 = parse(WfdRtsp.m3(2))[0]
        assertEquals("GET_PARAMETER", m3.method)
        assertEquals(listOf("wfd_video_formats", "wfd_audio_codecs", "wfd_client_rtp_ports"), m3.body.trim().split("\r\n"))
        assertEquals(m3.body.length, m3.header("content-length")!!.toInt())
    }

    @Test
    fun m3NeverRequestsAnythingBeyondTheMandatoryReads() {
        assertFalse(WfdRtsp.m3(2).contains("SET_PARAMETER"))
        assertFalse(WfdRtsp.m3(2).contains("wfd_presentation_url"))
    }

    @Test
    fun parameterParsingKeepsValuesWithColons() {
        val p = WfdRtsp.parseParameters("wfd_client_rtp_ports: RTP/AVP/UDP;unicast 19000 0 mode=play\r\nwfd_audio_codecs: none\r\nsolo_nombre\r\n")
        assertEquals("RTP/AVP/UDP;unicast 19000 0 mode=play", p["wfd_client_rtp_ports"])
        assertEquals("none", p["wfd_audio_codecs"])
        assertEquals(2, p.size)
    }

    // ---- veredictos ----

    private val formats60 = "00 00 02 04 00000180 00000000 00000000 00 0000 0000 00 none none"
    private val formats30 = "00 00 02 04 000000A0 00000000 00000000 00 0000 0000 00 none none"

    private fun done(o: WfdObservation = WfdObservation()) = o.copy(finished = true)

    @Test
    fun verdictFollowsTheFirstFailedPhase() {
        assertEquals(WfdVerdict.NOT_FINISHED, WfdDiagnosticText.verdict(WfdObservation()))
        assertEquals(WfdVerdict.CANCELLED, WfdDiagnosticText.verdict(WfdObservation(cancelled = true)))
        assertEquals(WfdVerdict.NO_REQUEST, WfdDiagnosticText.verdict(done(WfdObservation(connectAccepted = false, connectFailureCode = 2))))
        assertEquals(WfdVerdict.NO_GROUP, WfdDiagnosticText.verdict(done(WfdObservation(connectAccepted = true, groupFormed = false))))
        assertEquals(WfdVerdict.NO_TCP, WfdDiagnosticText.verdict(done(WfdObservation(connectAccepted = true, groupFormed = true))))
        assertEquals(
            WfdVerdict.RTSP_INCOMPLETE,
            WfdDiagnosticText.verdict(done(WfdObservation(connectAccepted = true, groupFormed = true, tvRemoteAddress = "192.168.49.2", m3Status = 404))),
        )
    }

    private fun full(formats: String) = done(
        WfdObservation(
            connectAccepted = true, groupFormed = true, tvRemoteAddress = "192.168.49.2", m3Status = 200,
            m3Params = mapOf("wfd_video_formats" to formats),
        ),
    )

    @Test
    fun verdictWithAndWithout60() {
        assertEquals(WfdVerdict.FORMATS_WITH_60, WfdDiagnosticText.verdict(full(formats60)))
        assertEquals(WfdVerdict.FORMATS_WITHOUT_60, WfdDiagnosticText.verdict(full(formats30)))
        assertEquals(WfdVerdict.FORMATS_UNPARSED, WfdDiagnosticText.verdict(full("texto raro")))
    }

    @Test
    fun noTcpIsNeverPresentedAsProofAbout60Fps() {
        val text = WfdDiagnosticText.inferences(done(WfdObservation(connectAccepted = true, groupFormed = true))).joinToString(" ")
        assertTrue(text.contains("INCONCLUSO"))
        assertTrue(text.contains("Hipótesis"))
        assertTrue(text.contains("ni demuestra que se pueda lograr 60 FPS"))
    }

    @Test
    fun receivedSectionOnlyHoldsReceivedData() {
        val lines = WfdDiagnosticText.received(full(formats30))
        assertTrue(lines.any { it.startsWith("wfd_video_formats (texto crudo): ") })
        assertFalse(lines.any { it.contains("Hipótesis") || it.contains("INCONCLUSO") })
    }

    @Test
    fun decodedSectionReportsOnlyWhatTheTextSays() {
        val d60 = WfdDiagnosticText.decoded(full(formats60)).joinToString("\n")
        assertTrue(d60.contains("1920x1080p60"))
        assertTrue(d60.contains("1920×1080 a 60 (progresivo) declarado: SÍ"))
        val d30 = WfdDiagnosticText.decoded(full(formats30)).joinToString("\n")
        assertTrue(d30.contains("1920×1080 a 60 (progresivo) declarado: no"))
        assertTrue(d30.contains("Mayor frecuencia progresiva declarada: 30 Hz"))
        assertNotNull(WfdDiagnosticText.decoded(full("xx")).firstOrNull())
    }

    @Test
    fun timeoutsMatchTheValuesVerifiedInAosp() {
        assertEquals(30, WfdTimeouts.GROUP_SECONDS)
        assertEquals(30, WfdTimeouts.RTSP_SECONDS)
        assertEquals(7236, WfdTimeouts.RTSP_PORT)
        assertEquals(3, WfdTimeouts.CONNECT_MAX_RETRIES)
        assertEquals(500L, WfdTimeouts.CONNECT_RETRY_DELAY_MS)
    }

    @Test
    fun stepLinesCarryOutcomeAndTime() {
        val line = WfdDiagnosticText.stepLine(WfdStep(WfdPhase.GROUP_FORMATION, StepOutcome.TIMEOUT, 30_250, "sin grupo"))
        assertEquals("Formación del grupo Wi-Fi Direct: TIEMPO AGOTADO (+30.3 s) — sin grupo", line)
    }
}
