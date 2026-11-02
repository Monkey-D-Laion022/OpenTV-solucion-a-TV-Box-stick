package com.opentv.app.iptv

import com.opentv.app.stream.StreamHub

/**
 * Fuente del MP4 fragmentado en vivo: el segmento de inicio se guarda aparte (cada cliente nuevo lo necesita primero) y los
 * fragmentos, que siempre empiezan en un fotograma clave, se reparten por un [StreamHub] con tope de memoria.
 */
class Mp4Feed(val hub: StreamHub = StreamHub(maxQueuedChunks = 30, maxQueuedBytes = 8 * 1024 * 1024)) {
    @Volatile var init: ByteArray? = null
        private set

    fun onInit(bytes: ByteArray) { init = bytes }

    fun onFragment(bytes: ByteArray) { hub.publish(bytes, true) }

    fun close() { hub.closeAll() }
}
