package com.opentv.app.stream

/**
 * Guarda SOLO el último fotograma JPEG y despierta a los clientes cuando hay uno nuevo. Un cliente lento se salta los
 * fotogramas viejos (nunca se acumulan), así el retraso no crece. Lógica pura.
 */
class MjpegHub {
    class Frame(val seq: Long, val jpeg: ByteArray)

    private val lock = Object()
    private var latest: Frame? = null
    private var closed = false

    val isClosed: Boolean get() = synchronized(lock) { closed }

    /** Último fotograma publicado, o `null` si todavía no hay ninguno. */
    fun latest(): Frame? = synchronized(lock) { latest }

    fun publish(jpeg: ByteArray) {
        synchronized(lock) {
            if (closed) return
            latest = Frame((latest?.seq ?: 0L) + 1, jpeg)
            lock.notifyAll()
        }
    }

    /**
     * Espera un fotograma más nuevo que [afterSeq]. Devuelve `null` si pasó [timeoutMs] sin novedades o si se cerró
     * (ver [isClosed]); en ese caso el servidor puede repetir el último para mantener viva la conexión.
     */
    fun awaitNewer(afterSeq: Long, timeoutMs: Long): Frame? {
        val deadline = System.currentTimeMillis() + timeoutMs
        synchronized(lock) {
            while (!closed) {
                val current = latest
                if (current != null && current.seq > afterSeq) return current
                val left = deadline - System.currentTimeMillis()
                if (left <= 0) return null
                lock.wait(left)
            }
            return null
        }
    }

    fun close() {
        synchronized(lock) {
            closed = true
            lock.notifyAll()
        }
    }
}
