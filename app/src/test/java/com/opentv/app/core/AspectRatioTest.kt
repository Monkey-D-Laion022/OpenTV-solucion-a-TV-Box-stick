package com.opentv.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AspectRatioTest {

    @Test
    fun fullHdAndHdAreSixteenByNine() {
        assertEquals("16:9", AspectRatio.describe(1920, 1080))
        assertEquals("16:9", AspectRatio.describe(1280, 720))
        assertTrue(AspectRatio.isApproximately16by9(1920, 1080))
    }

    @Test
    fun orientationDoesNotChangeTheResult() {
        assertEquals(AspectRatio.describe(1920, 1080), AspectRatio.describe(1080, 1920))
    }

    @Test
    fun commonPhoneRatiosAreRecognised() {
        assertEquals("19.5:9", AspectRatio.describe(2340, 1080))
        assertEquals("20:9", AspectRatio.describe(1600, 720))
        assertEquals("18:9", AspectRatio.describe(2160, 1080))
        assertEquals("4:3", AspectRatio.describe(1024, 768))
        assertEquals("16:10", AspectRatio.describe(1280, 800))
    }

    @Test
    fun unknownRatioIsShownNumerically() {
        assertEquals("2.11:1", AspectRatio.describe(1520, 720))
        assertFalse(AspectRatio.isApproximately16by9(2340, 1080))
    }

    @Test
    fun invalidSizesAreHandledWithoutCrashing() {
        assertNull(AspectRatio.ratioOf(0, 1080))
        assertEquals("desconocida", AspectRatio.describe(-1, 720))
        assertFalse(AspectRatio.isApproximately16by9(0, 0))
    }
}
