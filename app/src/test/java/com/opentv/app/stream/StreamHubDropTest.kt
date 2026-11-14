package com.opentv.app.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamHubDropTest {
    @Test fun sinAtrasoNoSeCuentaPerdida() {
        val hub = StreamHub(maxQueuedBytes = 1000); val s = hub.subscribe()
        hub.publish(ByteArray(300), true); hub.publish(ByteArray(300), false); s.poll(0); s.poll(0)
        hub.publish(ByteArray(300), false)
        assertEquals(0L, hub.droppedBytes.get())
    }
    @Test fun colaLlenaCuentaLoDescartadoYLoOmitidoHastaElFotogramaClave() {
        val hub = StreamHub(maxQueuedBytes = 1000); hub.subscribe()
        hub.publish(ByteArray(400), true); hub.publish(ByteArray(400), false)
        hub.publish(ByteArray(400), false)           // desborda: se tiran 800 en cola y este de 400
        assertEquals(1200L, hub.droppedBytes.get())
        hub.publish(ByteArray(100), false)           // sigue omitido hasta la próxima clave
        assertEquals(1300L, hub.droppedBytes.get())
        hub.publish(ByteArray(100), true)            // retoma
        hub.publish(ByteArray(100), false)
        assertEquals(1300L, hub.droppedBytes.get())
    }
    @Test fun unClienteNuevoEsperandoSuPrimerFotogramaClaveNoEsPerdida() {
        val hub = StreamHub(maxQueuedBytes = 1000); hub.subscribe()
        hub.publish(ByteArray(100), false); hub.publish(ByteArray(100), false)
        assertEquals(0L, hub.droppedBytes.get())
    }
    @Test fun elTotalPublicadoNoCambia() {
        val hub = StreamHub(maxQueuedBytes = 1000); hub.subscribe()
        hub.publish(ByteArray(900), true); hub.publish(ByteArray(900), false)
        assertEquals(1800L, hub.publishedBytes.get())
    }
}
