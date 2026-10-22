package com.opentv.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrientationPolicyTest {

    @Test
    fun portraitOnlyValuesAreDetected() {
        listOf(1, 7, 9, 12).forEach { assertTrue("valor $it", OrientationPolicy.declaresPortraitOnly(it)) }
    }

    @Test
    fun landscapeAndFlexibleValuesAreNotPortraitOnly() {
        // UNSPECIFIED(-1), LANDSCAPE(0), USER(2), SENSOR(4), SENSOR_LANDSCAPE(6),
        // REVERSE_LANDSCAPE(8), FULL_SENSOR(10), USER_LANDSCAPE(11), FULL_USER(13)
        listOf(-1, 0, 2, 4, 6, 8, 10, 11, 13).forEach {
            assertFalse("valor $it", OrientationPolicy.declaresPortraitOnly(it))
        }
    }
}
