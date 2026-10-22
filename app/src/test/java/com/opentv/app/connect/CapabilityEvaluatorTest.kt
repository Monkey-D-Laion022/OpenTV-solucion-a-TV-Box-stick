package com.opentv.app.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityEvaluatorTest {

    private fun raw(
        castSettings: Boolean = true,
        wifiDirect: Boolean = true,
        network: Boolean = true,
        playServices: Boolean = true,
        display: ExternalDisplay? = null,
    ) = RawCapabilities(castSettings, wifiDirect, network, playServices, display)

    private fun List<MethodStatus>.of(method: ConnectionMethod) = first { it.method == method }

    @Test
    fun returnsOneStatusPerMethod() {
        val statuses = CapabilityEvaluator.evaluate(raw())
        assertEquals(ConnectionMethod.values().toList(), statuses.map { it.method })
    }

    @Test
    fun miracastIsNeverClaimedAvailableWithoutAConnection() {
        val status = CapabilityEvaluator.evaluate(raw()).of(ConnectionMethod.MIRACAST)
        assertEquals(MethodState.UNVERIFIABLE, status.state)
        assertTrue(status.summary.contains("no permite"))
    }

    @Test
    fun miracastIsConnectedOnlyWithAnExternalDisplay() {
        val status = CapabilityEvaluator.evaluate(raw(display = ExternalDisplay("Sala"))).of(ConnectionMethod.MIRACAST)
        assertEquals(MethodState.CONNECTED, status.state)
        assertTrue(status.summary.contains("Sala"))
    }

    @Test
    fun miracastIsUnavailableWithoutSystemCastScreen() {
        val status = CapabilityEvaluator.evaluate(raw(castSettings = false)).of(ConnectionMethod.MIRACAST)
        assertEquals(MethodState.UNAVAILABLE, status.state)
    }

    @Test
    fun castNeedsPlayServicesAndNetwork() {
        assertEquals(
            MethodState.UNAVAILABLE,
            CapabilityEvaluator.evaluate(raw(playServices = false)).of(ConnectionMethod.GOOGLE_CAST).state,
        )
        assertEquals(
            MethodState.UNAVAILABLE,
            CapabilityEvaluator.evaluate(raw(network = false)).of(ConnectionMethod.GOOGLE_CAST).state,
        )
        assertEquals(
            MethodState.AVAILABLE,
            CapabilityEvaluator.evaluate(raw()).of(ConnectionMethod.GOOGLE_CAST).state,
        )
    }

    @Test
    fun dlnaNeedsALocalNetwork() {
        assertEquals(
            MethodState.UNAVAILABLE,
            CapabilityEvaluator.evaluate(raw(network = false)).of(ConnectionMethod.DLNA).state,
        )
        assertEquals(
            MethodState.AVAILABLE,
            CapabilityEvaluator.evaluate(raw()).of(ConnectionMethod.DLNA).state,
        )
    }

    @Test
    fun wifiDirectIsOnlyDetectedNeverAvailable() {
        assertEquals(
            MethodState.DETECTED_ONLY,
            CapabilityEvaluator.evaluate(raw()).of(ConnectionMethod.WIFI_DIRECT).state,
        )
        assertEquals(
            MethodState.UNAVAILABLE,
            CapabilityEvaluator.evaluate(raw(wifiDirect = false)).of(ConnectionMethod.WIFI_DIRECT).state,
        )
    }

    @Test
    fun deviceWithoutAnySupportReportsEverythingUnavailable() {
        val statuses = CapabilityEvaluator.evaluate(
            raw(castSettings = false, wifiDirect = false, network = false, playServices = false),
        )
        assertTrue(statuses.all { it.state == MethodState.UNAVAILABLE })
    }
}
