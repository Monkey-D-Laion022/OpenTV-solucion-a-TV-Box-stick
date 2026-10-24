package com.opentv.app.stream

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Reparte los paquetes TS que produce el codificador entre los clientes conectados. Un cliente nuevo
 * empieza a recibir recién en el próximo fotograma clave (si no, no podría decodificar). Si un
 * cliente es lento, se le vacía la cola y se resincroniza en el siguiente fotograma clave: nunca se
 * acumula memoria sin límite.
 */
class StreamHub(
    private val maxQueuedChunks: Int = 300,
    private val maxQueuedBytes: Int = Int.MAX_VALUE,
) {

    class Subscriber internal constructor(capacity: Int) {
        internal val queue = LinkedBlockingQueue<ByteArray>(capacity)
        internal val queuedBytes = java.util.concurrent.atomic.AtomicInteger(0)
        @Volatile internal var active = false
        @Volatile var closed = false
            internal set

        /** Siguiente bloque, o `null` si pasó el tiempo o el flujo se cerró. */
        fun poll(timeoutMs: Long): ByteArray? =
            if (closed && queue.isEmpty()) null else queue.poll(timeoutMs, TimeUnit.MILLISECONDS)?.also { queuedBytes.addAndGet(-it.size) }
    }

    private val subscribers = CopyOnWriteArrayList<Subscriber>()

    /** Bytes publicados en total (video, audio y relleno); lo usa el relleno de caudal. */
    val publishedBytes = java.util.concurrent.atomic.AtomicLong(0)

    /** Se invoca al conectarse un cliente: sirve para pedir al codificador un fotograma clave ya mismo. */
    @Volatile var onSubscribe: (() -> Unit)? = null

    val subscriberCount: Int get() = subscribers.size

    fun subscribe(): Subscriber = Subscriber(maxQueuedChunks).also {
        subscribers.add(it)
        onSubscribe?.invoke()
    }

    fun unsubscribe(subscriber: Subscriber) {
        subscribers.remove(subscriber)
        subscriber.closed = true
    }

    /** [keyStart] indica que el bloque empieza en un fotograma clave (lleva PAT/PMT al principio). */
    fun publish(chunk: ByteArray, keyStart: Boolean) {
        publishedBytes.addAndGet(chunk.size.toLong())
        for (sub in subscribers) {
            if (!sub.active) {
                if (!keyStart) continue
                sub.active = true
            }
            // Si el TV se atrasa (cola llena por cantidad o por bytes) se tira lo viejo y se retoma en el próximo
            // fotograma clave: la imagen salta pero el retraso no crece.
            if (sub.queuedBytes.get() + chunk.size > maxQueuedBytes || !sub.queue.offer(chunk)) {
                sub.queue.clear()
                sub.queuedBytes.set(0)
                sub.active = false
                if (keyStart && sub.queue.offer(chunk)) {
                    sub.queuedBytes.set(chunk.size)
                    sub.active = true
                }
            } else {
                sub.queuedBytes.addAndGet(chunk.size)
            }
        }
    }

    fun closeAll() {
        subscribers.forEach { it.closed = true }
        subscribers.clear()
    }
}
