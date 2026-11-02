package com.opentv.app.link

import com.opentv.app.proto.OtSendGate
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Cola de salida del emisor hacia un receptor. Si el receptor va lento se descarta y se retoma en el próximo fotograma clave. */
class LinkOutQueue(maxBytes: Int = 4 * 1024 * 1024) {
    class Item(val data: ByteArray, val keyStart: Boolean)

    private val queue = LinkedBlockingQueue<Item>()
    private val queued = AtomicInteger(0)
    private val gate = OtSendGate(maxBytes)
    @Volatile var dropped = 0L
        private set

    /** Devuelve `false` si el bloque se descartó por lentitud del receptor (quien llama puede pedir un fotograma clave). */
    fun offer(data: ByteArray, keyStart: Boolean): Boolean {
        if (!gate.allow(queued.get(), keyStart)) { dropped++; return false }
        queue.add(Item(data, keyStart))
        queued.addAndGet(data.size)
        return true
    }

    fun poll(timeoutMs: Long): Item? =
        queue.poll(timeoutMs, TimeUnit.MILLISECONDS)?.also { queued.addAndGet(-it.data.size) }

    val queuedBytes: Int get() = queued.get()
    val skipping: Boolean get() = gate.isSkipping
}

/**
 * Tubería de bytes TS desde la red hacia el reproductor del receptor. Un solo hilo lee ([read]); otro escribe ([push]).
 * Si el reproductor se atrasa más de [maxBytes] se vacía todo y se avisa para pedir un fotograma clave: la imagen salta
 * pero el retraso no crece sin límite.
 */
class TsStreamSource(private val maxBytes: Int = 8 * 1024 * 1024) {
    private val queue = LinkedBlockingQueue<ByteArray>()
    private val queued = AtomicInteger(0)
    private var current: ByteArray? = null
    private var pos = 0
    @Volatile private var closed = false
    @Volatile var overflows = 0L
        private set
    @Volatile var receivedBytes = 0L
        private set

    val bufferedBytes: Int get() = queued.get() + ((current?.size ?: 0) - pos).coerceAtLeast(0)

    /** `false` si hubo desborde y se descartó lo acumulado. */
    fun push(data: ByteArray): Boolean {
        if (closed) return true
        receivedBytes += data.size
        if (queued.get() + data.size > maxBytes) {
            queue.clear(); queued.set(0); overflows++
            queue.add(data); queued.addAndGet(data.size)
            return false
        }
        queue.add(data)
        queued.addAndGet(data.size)
        return true
    }

    /** Copia hasta [len] bytes; bloquea hasta que haya datos. Devuelve -1 al cerrarse. */
    fun read(dst: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        var c = current
        if (c == null || pos >= c.size) {
            c = null
            while (c == null) {
                if (closed && queue.isEmpty()) return -1
                c = queue.poll(200, TimeUnit.MILLISECONDS)
            }
            queued.addAndGet(-c.size)
            current = c
            pos = 0
        }
        val n = minOf(len, c.size - pos)
        System.arraycopy(c, pos, dst, off, n)
        pos += n
        return n
    }

    fun close() { closed = true }
    val isClosed: Boolean get() = closed
}
