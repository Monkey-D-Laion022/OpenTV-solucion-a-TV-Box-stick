package com.opentv.app.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MirroringGuideTest {

    @Test
    fun brandCyclesThroughAllValuesAndReturnsToStart() {
        var brand = TvBrand.SAMSUNG
        val seen = mutableSetOf<TvBrand>()
        repeat(TvBrand.values().size) {
            seen += brand
            brand = brand.next()
        }
        assertEquals(TvBrand.values().toSet(), seen)
        assertEquals(TvBrand.SAMSUNG, brand)
    }

    @Test
    fun everyBrandHasStepsToFollow() {
        TvBrand.values().forEach { assertTrue(it.label, it.steps.isNotEmpty()) }
    }

    @Test
    fun samsungPhonesUseSmartView() {
        assertEquals("Smart View", PhoneGuides.forManufacturer("samsung").featureName)
        assertEquals("Smart View", PhoneGuides.forManufacturer("  Samsung ").featureName)
    }

    @Test
    fun manufacturerMatchingIsCaseInsensitiveAndHasADefault() {
        assertTrue(PhoneGuides.forManufacturer("XIAOMI").featureName.contains("Cast"))
        assertTrue(PhoneGuides.forManufacturer("Redmi").featureName.contains("Cast"))
        assertEquals("Transmitir / Cast / Duplicar pantalla", PhoneGuides.forManufacturer("").featureName)
        assertEquals(
            "Transmitir / Cast / Duplicar pantalla",
            PhoneGuides.forManufacturer("MarcaDesconocida").featureName,
        )
    }

    @Test
    fun phoneStepsMentionTheFeatureName() {
        val guide = PhoneGuides.forManufacturer("huawei")
        assertTrue(guide.steps.any { it.contains(guide.featureName) })
    }
}
