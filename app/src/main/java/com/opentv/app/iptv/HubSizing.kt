package com.opentv.app.iptv

/**
 * Tamaño de la cola por cliente del reparto de video. La cola solo se llena cuando el televisor o el Wi-Fi se atrasan: es el colchón que
 * absorbe un microcorte sin tirar video. Con 1 MB fijos, a 10 Mbps (1080p60) eran 0,8 s: un corte algo más largo vaciaba la cola y
 * obligaba a esperar al siguiente fotograma clave (hasta 1 s de imagen saltada). Se dimensiona en tiempo, no en bytes: ~1,6 s del
 * bitrate pedido, con piso de 1 MB (nada cambia para los perfiles de hasta 5 Mbps) y techo de 3 MB (memoria acotada).
 * El retraso extra solo existe mientras haya un atasco; en régimen normal la cola está vacía. Lógica pura.
 */
object HubSizing {
    const val MIN_BYTES = 1024 * 1024
    const val MAX_BYTES = 3 * 1024 * 1024
    private const val SECONDS_OF_BUFFER = 1.6

    fun queueBytes(bitrateBps: Int): Int =
        (bitrateBps / 8.0 * SECONDS_OF_BUFFER).toInt().coerceIn(MIN_BYTES, MAX_BYTES)
}
