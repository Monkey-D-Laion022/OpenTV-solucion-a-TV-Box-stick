package com.opentv.app.catalog

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Resultado de una petición HTTP. Ninguna variante contiene la dirección pedida (puede llevar una clave). */
sealed class FetchResult {
    class Ok(val bytes: ByteArray) : FetchResult()
    class HttpError(val code: Int, val retryAfterSeconds: Int?) : FetchResult()
    class NetworkError(val reason: String) : FetchResult()
}

/** Cliente HTTP mínimo con `HttpURLConnection` (sin dependencias nuevas). Bloqueante: llamarlo fuera del hilo principal. */
object HttpFetcher {
    const val DEFAULT_TIMEOUT_MS = 10_000

    fun get(request: CatalogRequest, maxBytes: Int, timeoutMs: Int = DEFAULT_TIMEOUT_MS): FetchResult {
        var connection: HttpURLConnection? = null
        try {
            val c = URL(request.url).openConnection() as HttpURLConnection
            connection = c
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            c.requestMethod = "GET"
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", "OpenTV")
            for ((name, value) in request.headers) c.setRequestProperty(name, value)

            val code = c.responseCode
            if (code !in 200..299) {
                return FetchResult.HttpError(code, c.getHeaderField("Retry-After")?.trim()?.toIntOrNull())
            }
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            c.inputStream.use { input ->
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    if (out.size() > maxBytes) return FetchResult.NetworkError("respuesta demasiado grande")
                }
            }
            return FetchResult.Ok(out.toByteArray())
        } catch (e: IOException) {
            return FetchResult.NetworkError(e.javaClass.simpleName)
        } catch (e: RuntimeException) {
            // URL mal formada u otro error inesperado: se informa sin detalles que puedan incluir la dirección.
            return FetchResult.NetworkError(e.javaClass.simpleName)
        } finally {
            connection?.disconnect()
        }
    }
}
