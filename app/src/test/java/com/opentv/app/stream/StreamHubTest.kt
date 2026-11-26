package com.opentv.app.stream

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamHubTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun newSubscriberWaitsForKeyFrame() {
        val hub = StreamHub()
        val sub = hub.subscribe()
        hub.publish(bytes(1), keyStart = false)
        assertNull(sub.poll(10))
        hub.publish(bytes(2), keyStart = true)
        hub.publish(bytes(3), keyStart = false)
        assertArrayEquals(bytes(2), sub.poll(100))
        assertArrayEquals(bytes(3), sub.poll(100))
    }

    @Test
    fun slowSubscriberIsResyncedAtNextKeyFrameWithoutGrowingForever() {
        val hub = StreamHub(maxQueuedChunks = 3)
        val sub = hub.subscribe()
        hub.publish(bytes(0), true)
        repeat(5) { hub.publish(bytes(it + 10), false) } // desborda la cola de 3
        hub.publish(bytes(99), false) // inactivo hasta la próxima clave
        hub.publish(bytes(50), true)
        assertArrayEquals(bytes(50), sub.poll(100))
    }

    @Test
    fun closeAllEndsSubscribers() {
        val hub = StreamHub()
        val sub = hub.subscribe()
        hub.closeAll()
        assertTrue(sub.closed)
        assertNull(sub.poll(10))
        assertEquals(0, hub.subscriberCount)
    }

    @Test
    fun unsubscribeStopsDelivery() {
        val hub = StreamHub()
        val sub = hub.subscribe()
        hub.unsubscribe(sub)
        hub.publish(bytes(1), true)
        assertNull(sub.poll(10))
    }

    @Test
    fun replayGivesNewSubscriberTheLastGopImmediately() {
        val hub = StreamHub(replayBytes = 100)
        hub.publish(bytes(1), true)
        hub.publish(bytes(2), false)
        hub.publish(bytes(3), true) // GOP nuevo: reemplaza al anterior
        hub.publish(bytes(4), false)
        val sub = hub.subscribe()
        assertArrayEquals(bytes(3), sub.poll(50))
        assertArrayEquals(bytes(4), sub.poll(50))
        hub.publish(bytes(5), false)
        assertArrayEquals(bytes(5), sub.poll(50))
    }

    @Test
    fun replayOversizeGopInvalidatesCache() {
        val hub = StreamHub(replayBytes = 3)
        hub.publish(bytes(1, 1), true)
        hub.publish(bytes(2, 2), false) // 4 > 3: invalida
        val sub = hub.subscribe()
        assertNull(sub.poll(20))
        hub.publish(bytes(9), false)
        assertNull(sub.poll(20)) // sigue esperando clave
        hub.publish(bytes(7), true)
        assertArrayEquals(bytes(7), sub.poll(50))
    }

    @Test
    fun defaultHubHasNoReplay() {
        val hub = StreamHub()
        hub.publish(bytes(1), true)
        assertNull(hub.subscribe().poll(20))
    }
}
