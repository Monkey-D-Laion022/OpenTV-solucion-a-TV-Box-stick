package com.opentv.app.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayableUrlTest {

    private fun valid(raw: String) = PlayableUrl.check(raw) as PlayableUrlCheck.Valid
    private fun invalid(raw: String) = PlayableUrl.check(raw) as PlayableUrlCheck.Invalid

    @Test
    fun acceptsHttpAndHttpsAndTrims() {
        assertEquals("http://192.168.1.5:8080/v.mp4", valid("  http://192.168.1.5:8080/v.mp4 \n").url)
        assertEquals("https://ejemplo.com/a.m3u8?x=1", valid("https://ejemplo.com/a.m3u8?x=1").url)
        assertEquals("HTTPS://ejemplo.com/a", valid("HTTPS://ejemplo.com/a").url)
    }

    @Test
    fun rejectsEmptyAndBlank() {
        assertTrue(invalid("").reason.isNotBlank())
        assertTrue(invalid("   ").reason.isNotBlank())
    }

    @Test
    fun rejectsOtherSchemes() {
        listOf("file:///sdcard/a.mp4", "content://x/y", "ftp://h/a", "javascript:alert(1)", "intent://x#Intent;end")
            .forEach { invalid(it) }
    }

    @Test
    fun rejectsMissingHostAndNoScheme() {
        invalid("http://")
        invalid("ejemplo.com/video.mp4")
        invalid("http:///solo-ruta")
    }

    @Test
    fun rejectsInnerWhitespaceAndControlChars() {
        invalid("http://a.com/mi video.mp4")
        invalid("http://a.com/a\u0000b")
    }

    @Test
    fun rejectsTooLong() {
        invalid("http://a.com/" + "x".repeat(2100))
    }

    @Test
    fun fitCyclesThroughAllModes() {
        var f = VideoFit.FIT
        val seen = mutableSetOf<VideoFit>()
        repeat(VideoFit.values().size) { seen += f; f = f.next() }
        assertEquals(VideoFit.values().toSet(), seen)
        assertEquals(VideoFit.FIT, f)
    }
}
