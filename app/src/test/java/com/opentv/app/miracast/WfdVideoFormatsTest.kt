package com.opentv.app.miracast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Los textos de prueba están CONSTRUIDOS según el formato verificado de AOSP (VideoFormats::getFormatSpec:
 * "%02x 00 %02x %02x %08x %08x %08x 00 0000 0000 00 none none"). NO son capturas de ningún televisor real.
 */
class WfdVideoFormatsTest {
    private fun spec(profile: String, level: String, cea: String, vesa: String = "00000000", hh: String = "00000000") =
        "00 00 $profile $level $cea $vesa $hh 00 0000 0000 00 none none"

    @Test
    fun aospDefaultIsOnly720p30() {
        val f = WfdVideoFormats.parse(spec("01", "01", "00000020"))!!
        assertEquals(listOf("1280x720p30"), f.modes.map { it.label })
        assertFalse(f.has1080p60)
        assertEquals(30, f.maxProgressiveFps)
    }

    @Test
    fun bit8IsExactly1080p60() {
        val f = WfdVideoFormats.parse(spec("02", "04", "00000100"))!!
        assertTrue(f.has1080p60)
        assertEquals(60, f.maxFpsAt(1920, 1080))
    }

    @Test
    fun bits7and8Together() {
        val f = WfdVideoFormats.parse(spec("02", "04", "00000180"))!!
        assertEquals(setOf("1920x1080p30", "1920x1080p60"), f.modes.map { it.label }.toSet())
        assertTrue(f.supports(1920, 1080, 30))
        assertTrue(f.supports(1920, 1080, 60))
    }

    @Test
    fun only1080p30MeansNo60At1080() {
        val f = WfdVideoFormats.parse(spec("02", "04", "000000A0"))!! // bits 5 y 7
        assertFalse(f.has1080p60)
        assertEquals(30, f.maxFpsAt(1920, 1080))
        assertEquals(30, f.maxFpsAt(1280, 720))
        assertEquals(30, f.maxProgressiveFps)
    }

    @Test
    fun interlacedIsNotCountedAs60Progressive() {
        val f = WfdVideoFormats.parse(spec("02", "04", "00000200"))!! // bit 9 = 1080i60
        assertFalse(f.has1080p60)
        assertNull(f.maxFpsAt(1920, 1080))
        assertTrue(f.supports(1920, 1080, 60, allowInterlaced = true))
    }

    @Test
    fun vesaBit29Is1920x1200p60() {
        val f = WfdVideoFormats.parse(spec("01", "01", "00000000", vesa = "20000000"))!!
        assertEquals(listOf("1920x1200p60"), f.modes.map { it.label })
    }

    @Test
    fun hhBit9Is540p60() {
        val f = WfdVideoFormats.parse(spec("01", "01", "00000000", hh = "00000200"))!!
        assertEquals(listOf("960x540p60"), f.modes.map { it.label })
    }

    @Test
    fun multipleCodecsAreMerged() {
        val two = spec("01", "01", "00000020") + ", " + spec("02", "04", "00000100").substringAfter("00 00 ")
        val f = WfdVideoFormats.parse(two)!!
        assertTrue(f.supports(1280, 720, 30))
        assertTrue(f.has1080p60)
        assertEquals(3, f.profiles)
        assertEquals(5, f.levels)
    }

    @Test
    fun nativeModeIsDecoded() {
        // 0x28 = índice 5 (<<3) en tabla 0 (CEA)
        val f = WfdVideoFormats.parse("28 00 01 01 00000020 00000000 00000000 00 0000 0000 00 none none")!!
        assertEquals(WfdTable.CEA to 5, f.native)
    }

    @Test
    fun noneAndGarbageAreHonest() {
        assertTrue(WfdVideoFormats.parse("none")!!.noVideo)
        assertNull(WfdVideoFormats.parse(""))
        assertNull(WfdVideoFormats.parse("hola mundo"))
        assertNull(WfdVideoFormats.parse("00 00 01 01 00000020")) // truncado: no se adivina
        assertNull(WfdVideoFormats.parse(spec("zz", "01", "00000020")))
    }

    @Test
    fun pickBestPrefers1080p30Over720p60AndBothAt60Wins() {
        val sink30 = WfdVideoFormats.parse(spec("02", "04", "000000E0"))!! // 720p30, 720p60 (bit 6), 1080p30
        val src = WfdVideoFormats.parse(spec("02", "04", "000001E0"))!! // + 1080p60 (bit 8)
        assertEquals("1920x1080p30", WfdVideoFormats.pickBest(sink30, src)!!.label)
        val sink60 = WfdVideoFormats.parse(spec("02", "04", "000001E0"))!!
        assertEquals("1920x1080p60", WfdVideoFormats.pickBest(sink60, src)!!.label)
    }

    @Test
    fun scoresMatchTheValuesQuotedInTheReport() {
        val f = WfdVideoFormats.parse(spec("02", "04", "000001C0"))!! // 720p60, 1080p30, 1080p60
        val s = f.modes.associate { it.label to it.score }
        assertEquals(55_296_000L, s["1280x720p60"])
        assertEquals(62_208_000L, s["1920x1080p30"])
        assertEquals(124_416_000L, s["1920x1080p60"])
        assertNotNull(f.native.let { it ?: WfdTable.CEA })
    }
}
