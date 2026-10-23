package com.opentv.app.connect

import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Busca televisores DLNA y les envía un enlace de video directo. Solo toma como válidos los
 * equipos que: responden desde la misma dirección que anuncian, publican una IPv4 privada, son un
 * MediaRenderer y ofrecen AVTransport (ver [UpnpDescription]). Todo lo demás se descarta.
 *
 * DLNA envía un ENLACE que el TV descarga por su cuenta: no duplica la pantalla del teléfono.
 */
class DlnaController(
    private val search: SsdpSearch,
    private val http: LanHttp,
    private val searchTimeoutMs: Long = 4_000,
) {
    suspend fun discover(): List<UpnpDevice> {
        val hits = search.search(searchTimeoutMs)
            .filter { Ssdp.isTrustedSource(it.response, it.senderHost) }
            .distinctBy { it.response.location }
            .take(MAX_CANDIDATES)

        return coroutineScope {
            hits.map { hit -> async { describe(hit) } }.awaitAll()
        }.filterNotNull().distinctBy { it.udn }
    }

    /** Envía el enlace y ordena reproducir. Si el primer paso falla, no se intenta el segundo. */
    suspend fun play(device: UpnpDevice, mediaUrl: String, metadata: String? = null): DlnaResult {
        val url = (MediaUrlPolicy.check(mediaUrl) as? MediaUrlCheck.Valid)?.url
            ?: return DlnaResult.Rejected(status = 0, detail = "enlace no válido")

        val type = device.avTransportServiceType
        val setUri = send(device, DlnaSoap.ACTION_SET_URI, DlnaSoap.setUri(type, url, metadata))
        if (setUri != DlnaResult.Ok) return setUri
        return send(device, DlnaSoap.ACTION_PLAY, DlnaSoap.play(type))
    }

    suspend fun stop(device: UpnpDevice): DlnaResult =
        send(device, DlnaSoap.ACTION_STOP, DlnaSoap.stop(device.avTransportServiceType))

    private suspend fun describe(hit: SsdpHit): UpnpDevice? {
        val endpoint = LanAddress.parseHttpUrl(hit.response.location) ?: return null
        val response = try {
            http.request(endpoint, HttpMessages.buildGet(endpoint))
        } catch (e: IOException) {
            return null
        } catch (e: IllegalArgumentException) {
            return null
        }
        if (response.status != HTTP_OK) return null
        return UpnpDescription.parse(response.body, hit.response.location)
    }

    private suspend fun send(device: UpnpDevice, action: String, body: String): DlnaResult {
        val endpoint = device.controlEndpoint
        return try {
            val soapAction = DlnaSoap.soapAction(device.avTransportServiceType, action)
            val response = http.request(endpoint, HttpMessages.buildPost(endpoint, soapAction, body))
            if (response.status in HTTP_OK..HTTP_LAST_SUCCESS) {
                DlnaResult.Ok
            } else {
                DlnaResult.Rejected(response.status, DlnaSoap.errorDetail(response.body))
            }
        } catch (e: IOException) {
            DlnaResult.Unreachable(e.message ?: "sin respuesta")
        } catch (e: IllegalArgumentException) {
            DlnaResult.Unreachable("dirección o mensaje inválido")
        }
    }

    private companion object {
        const val MAX_CANDIDATES = 16
        const val HTTP_OK = 200
        const val HTTP_LAST_SUCCESS = 299
    }
}
