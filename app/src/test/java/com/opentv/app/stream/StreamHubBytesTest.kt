package com.opentv.app.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamHubBytesTest {
    @Test
    fun slowClientIsResyncedAtNextKeyFrameInsteadOfGrowingDelay() {
        val hub = StreamHub(maxQueuedChunks = 1000, maxQueuedBytes = 1000)
        val sub = hub.subscribe()
        hub.publish(ByteArray(400) { 1 }, keyStart = true)
        hub.publish(ByteArray(400) { 2 }, keyStart = false)
        // Supera el límite: se tira lo viejo y se espera un fotograma clave.
        hub.publish(ByteArray(400) { 3 }, keyStart = false)
        assertNull(sub.poll(10))
        hub.publish(ByteArray(300) { 4 }, keyStart = true)
        val first = sub.poll(100)!!
        assertEquals(4, first[0].toInt())
        assertEquals(300, first.size)
    }

    @Test
    fun pollingFreesTheByteBudget() {
        val hub = StreamHub(maxQueuedChunks = 1000, maxQueuedBytes = 1000)
        val sub = hub.subscribe()
        hub.publish(ByteArray(600), true)
        assertTrue(sub.poll(100) != null)
        hub.publish(ByteArray(600), false)
        assertTrue(sub.poll(100) != null) // no se descartó: la cola ya estaba vacía
    }
}
