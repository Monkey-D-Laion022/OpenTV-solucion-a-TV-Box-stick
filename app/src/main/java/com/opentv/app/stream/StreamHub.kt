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
    /**
     * > 0 = se guarda el último GOP (desde el último fotograma clave, hasta este tamaño) y un cliente nuevo lo recibe de inmediato en vez de esperar
     * al próximo clave. Ronda 7: el LG pedía /live.ts y no recibía ni un byte hasta que el codificador (bajo carga) entregaba otro clave; su
     * sondeo vencía (SocketTimeoutException) y la sesión DLNA reintentaba al arrancar. 0 = comportamiento anterior.
     */
    private val replayBytes: Int = 0,
) {
    private val lock = Any()
    private val replay = ArrayDeque<ByteArray>()
    private var replaySize = 0
    private var replayValid = false


    class Subscriber internal constructor(capacity: Int) {
        internal val queue = LinkedBlockingQueue<ByteArray>(capacity)
        internal val queuedBytes = java.util.concurrent.atomic.AtomicInteger(0)
        @Volatile internal var active = false
        /** `true` desde que a este cliente se le descartó video por atraso hasta que retoma en un fotograma clave (esos bytes son pérdida real). */
        @Volatile internal var resyncing = false
        @Volatile var closed = false
            internal set

        /** Siguiente bloque, o `null` si pasó el tiempo o el flujo se cerró. */
        fun poll(timeoutMs: Long): ByteArray? =
            if (closed && queue.isEmpty()) null else queue.poll(timeoutMs, TimeUnit.MILLISECONDS)?.also { queuedBytes.addAndGet(-it.size) }
    }

    private val subscribers = CopyOnWriteArrayList<Subscriber>()

    /** Bytes publicados en total (video, audio y relleno); lo usa el relleno de caudal. */
    val publishedBytes = java.util.concurrent.atomic.AtomicLong(0)

    /** Bytes de video descartados por clientes lentos (cola llena + lo omitido hasta el siguiente fotograma clave). Señal directa de enlace insuficiente. */
    val droppedBytes = java.util.concurrent.atomic.AtomicLong(0)

    /** Se invoca al conectarse un cliente: sirve para pedir al codificador un fotograma clave ya mismo. */
    @Volatile var onSubscribe: (() -> Unit)? = null

    val subscriberCount: Int get() = subscribers.size

    fun subscribe(): Subscriber = synchronized(lock) {
        val s = Subscriber(maxQueuedChunks)
        if (replayBytes > 0 && replayValid && replay.isNotEmpty() && replay.size < maxQueuedChunks) {
            for (c in replay) s.queue.offer(c)
            s.queuedBytes.set(replaySize); s.active = true
        }
        subscribers.add(s)
        onSubscribe?.invoke()
        s
    }

    fun unsubscribe(subscriber: Subscriber) {
        subscribers.remove(subscriber)
        subscriber.closed = true
    }

    /** [keyStart] indica que el bloque empieza en un fotograma clave (lleva PAT/PMT al principio). */
    fun publish(chunk: ByteArray, keyStart: Boolean) = synchronized(lock) {
        publishedBytes.addAndGet(chunk.size.toLong())
        if (replayBytes > 0) {
            if (keyStart) { replay.clear(); replaySize = 0; replayValid = true }
            if (replayValid) {
                if (replaySize + chunk.size > replayBytes) { replayValid = false; replay.clear(); replaySize = 0 }
                else { replay.addLast(chunk); replaySize += chunk.size }
            }
        }
        for (sub in subscribers) {
            if (!sub.active) {
                if (!keyStart) { if (sub.resyncing) droppedBytes.addAndGet(chunk.size.toLong()); continue }
                sub.active = true; sub.resyncing = false
            }
            // Si el TV se atrasa (cola llena por cantidad o por bytes) se tira lo viejo y se retoma en el próximo
            // fotograma clave: la imagen salta pero el retraso no crece.
            if (sub.queuedBytes.get() + chunk.size > maxQueuedBytes || !sub.queue.offer(chunk)) {
                droppedBytes.addAndGet(sub.queuedBytes.get().toLong())
                sub.queue.clear()
                sub.queuedBytes.set(0)
                sub.active = false; sub.resyncing = true
                if (keyStart && sub.queue.offer(chunk)) {
                    sub.queuedBytes.set(chunk.size)
                    sub.active = true; sub.resyncing = false
                } else droppedBytes.addAndGet(chunk.size.toLong())
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
