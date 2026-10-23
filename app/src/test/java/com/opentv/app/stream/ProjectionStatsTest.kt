package com.opentv.app.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectionStatsTest {
    @Test
    fun hintFollowsTheFirstFailingStage() {
        assertTrue(ProjectionStats().hint().contains("no pidió"))
        assertTrue(ProjectionStats(requests = 1).hint().contains("codificador no entrega"))
        assertTrue(ProjectionStats(requests = 1, encodedFrames = 5).hint().contains("no salen"))
        assertTrue(ProjectionStats(requests = 1, encodedFrames = 5, bytesSent = 9).hint().contains("está saliendo"))
    }

    @Test
    fun formatsBytes() {
        assertEquals("500 B", ProjectionStats.formatBytes(500))
        assertEquals("2 KB", ProjectionStats.formatBytes(2048))
        assertEquals("1.5 MB", ProjectionStats.formatBytes(1_572_864))
    }

    @Test
    fun describeMentionsMissingUi() {
        val text = ProjectionStats(profileLabel = "X", uiVisible = false, lastRequest = "GET").describe()
        assertTrue(text.contains("NO"))
        assertTrue(text.contains("Último pedido del TV: GET"))
    }
}
