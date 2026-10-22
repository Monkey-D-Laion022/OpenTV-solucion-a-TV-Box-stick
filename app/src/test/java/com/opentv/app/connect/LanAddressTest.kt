package com.opentv.app.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanAddressTest {

    @Test
    fun privateRangesAreAccepted() {
        listOf("10.0.0.5", "172.16.0.1", "172.31.255.254", "192.168.1.20", "169.254.3.4")
            .forEach { assertTrue(it, LanAddress.isPrivateIpv4(it)) }
    }

    @Test
    fun publicAndMalformedAddressesAreRejected() {
        listOf(
            "8.8.8.8", "172.15.0.1", "172.32.0.1", "192.169.1.1", "11.0.0.1", "127.0.0.1",
            "192.168.1", "192.168.1.256", "192.168.01.1", "192.168.1.1.1", "tv.local", "", "::1",
            "192.168.1.-1", "0xC0.168.1.1",
        ).forEach { assertFalse(it, LanAddress.isPrivateIpv4(it)) }
    }

    @Test
    fun parsesHostPortAndPath() {
        assertEquals(
            LanEndpoint("192.168.1.50", 8080, "/desc.xml"),
            LanAddress.parseHttpUrl("http://192.168.1.50:8080/desc.xml"),
        )
        assertEquals(
            LanEndpoint("10.0.0.2", 80, "/"),
            LanAddress.parseHttpUrl("HTTP://10.0.0.2"),
        )
        assertEquals(
            LanEndpoint("10.0.0.2", 80, "/?a=1"),
            LanAddress.parseHttpUrl("http://10.0.0.2?a=1"),
        )
        assertEquals(
            LanEndpoint("10.0.0.2", 80, "/a"),
            LanAddress.parseHttpUrl("http://10.0.0.2/a#frag"),
        )
    }

    @Test
    fun rejectsUnsafeUrls() {
        listOf(
            "https://192.168.1.50/desc.xml",
            "http://8.8.8.8/desc.xml",
            "http://tv.local/desc.xml",
            "http://user:pw@192.168.1.5/",
            "http://192.168.1.5:0/",
            "http://192.168.1.5:70000/",
            "http://192.168.1.5:abc/",
            "http://192.168.1.5:/",
            "http://192.168.1.5/a b",
            "http://192.168.1.5/a\r\nHost: x",
            "http://",
            "ftp://192.168.1.5/",
            "http://192.168.1.5/" + "a".repeat(2100),
        ).forEach { assertNull(it, LanAddress.parseHttpUrl(it)) }
    }

    @Test
    fun resolvesControlUrlsOnlyOnTheSameDevice() {
        val base = LanEndpoint("192.168.1.50", 49152, "/dmr/desc.xml")
        assertEquals(LanEndpoint("192.168.1.50", 49152, "/ctl/av"), LanAddress.resolveAgainst(base, "/ctl/av"))
        assertEquals(LanEndpoint("192.168.1.50", 49152, "/dmr/ctl"), LanAddress.resolveAgainst(base, "ctl"))
        assertEquals(
            LanEndpoint("192.168.1.50", 9000, "/x"),
            LanAddress.resolveAgainst(base, "http://192.168.1.50:9000/x"),
        )
        assertNull(LanAddress.resolveAgainst(base, "http://192.168.1.99/x"))
        assertNull(LanAddress.resolveAgainst(base, "http://8.8.8.8/x"))
        assertNull(LanAddress.resolveAgainst(base, "https://192.168.1.50/x"))
        assertNull(LanAddress.resolveAgainst(base, "/a b"))
        assertNull(LanAddress.resolveAgainst(base, ""))
    }

    @Test
    fun mediaUrlAcceptsHttpAndHttps() {
        assertEquals(
            MediaUrlCheck.Valid("https://ejemplo.com/video.mp4"),
            MediaUrlPolicy.check("  https://ejemplo.com/video.mp4 "),
        )
        assertTrue(MediaUrlPolicy.check("http://192.168.1.3:8000/a.mkv") is MediaUrlCheck.Valid)
    }

    @Test
    fun mediaUrlRejectsBadInput() {
        listOf(
            "", "   ", "ejemplo.com/video.mp4", "file:///sdcard/a.mp4", "content://x/y",
            "javascript:alert(1)", "https://", "https://user:pw@host/a.mp4", "https://a b/c.mp4",
            "https://host/" + "a".repeat(2100), "ftp://host/a.mp4",
        ).forEach { assertTrue(it, MediaUrlPolicy.check(it) is MediaUrlCheck.Invalid) }
    }
}
