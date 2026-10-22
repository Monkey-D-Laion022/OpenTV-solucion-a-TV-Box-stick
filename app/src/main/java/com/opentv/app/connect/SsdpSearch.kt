package com.opentv.app.connect

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** Respuesta SSDP junto con la dirección IP desde la que llegó. */
data class SsdpHit(val senderHost: String, val response: SsdpResponse)

/** Envía la búsqueda SSDP y devuelve las respuestas. Se reemplaza por un doble en pruebas. */
interface SsdpSearch {
    suspend fun search(timeoutMs: Long): List<SsdpHit>
}

/**
 * Búsqueda SSDP por UDP multicast. Las respuestas llegan por unicast al puerto del emisor, así que
 * no hace falta MulticastLock. Se envía dos veces (UDP puede perder paquetes) y se escucha hasta
 * agotar el tiempo. Lanza [java.io.IOException] si no hay una red utilizable.
 */
class UdpSsdpSearch(private val target: String = Ssdp.MEDIA_RENDERER) : SsdpSearch {

    override suspend fun search(timeoutMs: Long): List<SsdpHit> = withContext(Dispatchers.IO) {
        val hits = ArrayList<SsdpHit>()
        val request = Ssdp.buildSearch(target)
        val group = InetAddress.getByName(Ssdp.MULTICAST_ADDRESS)
        val buffer = ByteArray(BUFFER_SIZE)
        val deadline = System.nanoTime() + timeoutMs * NANOS_PER_MS

        DatagramSocket().use { socket ->
            socket.soTimeout = RECEIVE_SLICE_MS
            var sent = 0
            var nextSendAt = 0L
            while (isActive && System.nanoTime() < deadline && hits.size < MAX_HITS) {
                val now = System.nanoTime()
                if (sent < SEND_COUNT && now >= nextSendAt) {
                    socket.send(DatagramPacket(request, request.size, group, Ssdp.PORT))
                    sent++
                    nextSendAt = now + RESEND_INTERVAL_MS * NANOS_PER_MS
                }
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val response = Ssdp.parseResponse(packet.data, packet.length)
                    val sender = packet.address?.hostAddress
                    if (response != null && sender != null) hits += SsdpHit(sender, response)
                } catch (e: SocketTimeoutException) {
                    // Nada recibido en este intervalo: se vuelve a comprobar el tiempo total.
                }
            }
        }
        hits
    }

    private companion object {
        const val BUFFER_SIZE = 4096
        const val RECEIVE_SLICE_MS = 400
        const val RESEND_INTERVAL_MS = 1_000L
        const val SEND_COUNT = 2
        const val MAX_HITS = 64
        const val NANOS_PER_MS = 1_000_000L
    }
}
