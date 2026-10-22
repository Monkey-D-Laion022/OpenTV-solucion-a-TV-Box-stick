package com.opentv.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutesTest {

    @Test
    fun pendingFeatureIdsAreUniqueAndRoundTrip() {
        val ids = PendingFeature.values().map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        PendingFeature.values().forEach { assertEquals(it, PendingFeature.fromId(it.id)) }
    }

    @Test
    fun unknownOrMissingIdReturnsNull() {
        assertNull(PendingFeature.fromId("no_existe"))
        assertNull(PendingFeature.fromId(null))
    }

    @Test
    fun pendingRouteMatchesThePattern() {
        val prefix = Routes.PENDING_PATTERN.substringBefore("{")
        PendingFeature.values().forEach {
            assertTrue(Routes.pending(it).startsWith(prefix))
            assertEquals(it.id, Routes.pending(it).removePrefix(prefix))
        }
    }

    @Test
    fun everyPendingFeatureBelongsToAFuturePhase() {
        assertTrue(PendingFeature.values().all { it.phase >= 2 })
    }
}
