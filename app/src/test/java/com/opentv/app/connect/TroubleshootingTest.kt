package com.opentv.app.connect

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TroubleshootingTest {

    @Test
    fun generalTipsAlwaysAppear() {
        val tips = Troubleshooting.tips(onLocalNetwork = true, dlnaScannedWithoutResults = false)
        assertTrue(tips.size >= 4)
        assertTrue(tips.any { it.contains("proporción") })
        assertFalse(tips.any { it.contains("Conectá el teléfono a Wi‑Fi") })
        assertFalse(tips.any { it.contains("DLNA") })
    }

    @Test
    fun offlineTipComesFirst() {
        val tips = Troubleshooting.tips(onLocalNetwork = false, dlnaScannedWithoutResults = false)
        assertTrue(tips.first().contains("Conectá el teléfono a Wi‑Fi"))
    }

    @Test
    fun dlnaTipOnlyAfterAnEmptySearch() {
        val tips = Troubleshooting.tips(onLocalNetwork = true, dlnaScannedWithoutResults = true)
        assertTrue(tips.first().contains("DLNA"))
        assertTrue(tips.first().contains("aísle"))
    }
}
