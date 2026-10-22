package com.opentv.app.connect

import org.junit.Assert.assertEquals
import org.junit.Test

class CastMimeTest {

    @Test
    fun recognizesCommonExtensionsIgnoringQueryAndCase() {
        assertEquals("application/x-mpegURL", CastMime.guess("https://a.com/x/Lista.M3U8?token=a.b"))
        assertEquals("application/dash+xml", CastMime.guess("http://a.com/v.mpd#t=1"))
        assertEquals("video/x-matroska", CastMime.guess("http://a.com/p.mkv"))
        assertEquals("video/webm", CastMime.guess("http://a.com/p.webm"))
        assertEquals("audio/mpeg", CastMime.guess("http://a.com/p.mp3"))
    }

    @Test
    fun fallsBackToMp4() {
        assertEquals("video/mp4", CastMime.guess("http://a.com/p.mp4"))
        assertEquals("video/mp4", CastMime.guess("http://a.com/stream"))
        assertEquals("video/mp4", CastMime.guess("http://a.com/dir.x/stream"))
    }
}
