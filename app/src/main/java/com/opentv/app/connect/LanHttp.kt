package com.opentv.app.connect

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Envía un mensaje HTTP ya armado a un equipo de la red local. Se reemplaza por un doble en pruebas. */
interface LanHttp {
    /** Lanza [IOException] si no se puede conectar o la respuesta no es HTTP válida. */
    suspend fun request(endpoint: LanEndpoint, message: ByteArray): HttpResponse
}

/**
 * Cliente HTTP sobre un socket TCP común. No usa HttpURLConnection a propósito: así no hace falta
 * permitir tráfico HTTP sin cifrar en toda la app (los TV publican HTTP simple en la red local).
 * Solo recibe [LanEndpoint], cuyo host ya es una IPv4 privada literal (sin resolución de nombres).
 *
 * Límites: tiempo de conexión y de cada lectura, tiempo total y tamaño máximo de la respuesta.
 */
class SocketLanHttp(
    private val timeoutMs: Int = 3_000,
    private val totalTimeoutMs: Long = 8_000,
    private val maxResponseBytes: Int = 300_000,
) : LanHttp {

    override suspend fun request(endpoint: LanEndpoint, message: ByteArray): HttpResponse =
        withContext(Dispatchers.IO) {
            Socket().use { socket ->
                socket.soTimeout = timeoutMs
                socket.connect(InetSocketAddress(endpoint.host, endpoint.port), timeoutMs)
                val output = socket.getOutputStream()
                output.write(message)
                output.flush()
                val raw = readAll(socket.getInputStream())
                HttpMessages.parseResponse(raw) ?: throw IOException("Respuesta HTTP inválida")
            }
        }

    private fun readAll(input: InputStream): ByteArray {
        val deadline = System.nanoTime() + totalTimeoutMs * NANOS_PER_MS
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (out.size() < maxResponseBytes) {
            if (System.nanoTime() > deadline) throw IOException("Tiempo de espera agotado")
            val read = input.read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private companion object {
        const val NANOS_PER_MS = 1_000_000L
    }
}
