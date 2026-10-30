package com.opentv.app.iptv

/** Respuesta ya decidida; el servidor solo la escribe en el socket. */
sealed interface IptvResponse {
    class Body(
        val status: Int,
        val contentType: String,
        val body: ByteArray,
        val headers: Map<String, String> = emptyMap(),
    ) : IptvResponse

    /** Flujo TS continuo (sin fin), que el servidor alimenta desde el [com.opentv.app.stream.StreamHub]. */
    data object LiveTs : IptvResponse
}

/** Qué sabe el router del estado de la emisión; lo implementa el controlador (y las pruebas con datos falsos). */
interface IptvStatusSource {
    val channelName: String
    val width: Int
    val height: Int
    val fps: Int
    val bitrateBps: Int
    val withAudio: Boolean
    val clients: Int
    val startedAtMs: Long
}

/**
 * Rutas del servidor IPTV. Todas cuelgan de `/<token>/`: el código aleatorio evita que otro equipo de la red
 * adivine la dirección. Lógica pura: no abre sockets, así se prueba sin red.
 */
class IptvRouter(
    private val token: String,
    private val segmenter: HlsSegmenter,
    private val status: IptvStatusSource,
    private val nowMs: () -> Long,
) {
    /** [hostHeader]: valor de `Host` del pedido (sirve para armar direcciones que el cliente pueda reutilizar). */
    fun route(method: String, rawPath: String, hostHeader: String?, range: String? = null): IptvResponse {
        if (method != "GET" && method != "HEAD") return text(405, "Método no permitido", mapOf("Allow" to "GET, HEAD"))
        val path = rawPath.substringBefore('?')
        val prefix = "/$token/"
        if (!path.startsWith(prefix)) return text(404, "No encontrado")
        val name = path.removePrefix(prefix)
        val base = "http://${hostHeader?.takeIf { it.isNotBlank() && !it.contains('\n') } ?: "localhost"}/$token"
        return when {
            name == "index.m3u8" -> playlist()
            name == "channel.m3u" -> m3u(base, false)
            name == "compat.m3u" -> m3u(base, true)
            name == "live.ts" -> IptvResponse.LiveTs
            name == "status.json" -> statusJson()
            else -> {
                val seq = HlsNames.parseSegment(name) ?: return text(404, "No encontrado")
                val seg = segmenter.segment(seq) ?: return text(404, "Segmento fuera de la ventana")
                segment(seg, range)
            }
        }
    }

    private fun playlist(): IptvResponse {
        val body = segmenter.playlist()
            ?: return text(503, "La emisión está arrancando; reintentá en unos segundos", mapOf("Retry-After" to "2"))
        return IptvResponse.Body(200, "application/vnd.apple.mpegurl", body.toByteArray(Charsets.UTF_8), NO_CACHE + CORS)
    }

    private fun segment(seg: HlsSegment, range: String?): IptvResponse {
        val data = seg.data
        val r = range?.let { parseRange(it, data.size) }
        if (range != null && r == null) {
            return IptvResponse.Body(416, "text/plain", ByteArray(0), mapOf("Content-Range" to "bytes */${data.size}") + CORS)
        }
        if (r == null) return IptvResponse.Body(200, "video/MP2T", data, CORS + mapOf("Accept-Ranges" to "bytes"))
        val part = data.copyOfRange(r.first, r.last + 1)
        return IptvResponse.Body(
            206, "video/MP2T", part,
            CORS + mapOf("Accept-Ranges" to "bytes", "Content-Range" to "bytes ${r.first}-${r.last}/${data.size}"),
        )
    }

    private fun m3u(base: String, compat: Boolean): IptvResponse {
        val name = status.channelName
        val channels = if (compat) {
            listOf(
                M3uChannel("opentv-hls", "$name · prueba 1: HLS (m3u8)", "$base/index.m3u8", "Pruebas OpenTV"),
                M3uChannel("opentv-ts", "$name · prueba 2: TS directo", "$base/live.ts", "Pruebas OpenTV"),
            )
        } else {
            listOf(M3uChannel("opentv", name, "$base/index.m3u8"))
        }
        return IptvResponse.Body(200, "audio/x-mpegurl", M3uBuilder.build(channels).toByteArray(Charsets.UTF_8), NO_CACHE + CORS)
    }

    private fun statusJson(): IptvResponse {
        val up = ((nowMs() - status.startedAtMs) / 1000).coerceAtLeast(0)
        val json = "{\"channel\":\"${M3uBuilder.clean(status.channelName)}\",\"width\":${status.width},\"height\":${status.height}," +
            "\"fps\":${status.fps},\"bitrate\":${status.bitrateBps},\"audio\":${status.withAudio}," +
            "\"clients\":${status.clients},\"segments\":${segmenter.publishedSegments},\"uptimeSeconds\":$up}"
        return IptvResponse.Body(200, "application/json", json.toByteArray(Charsets.UTF_8), NO_CACHE + CORS)
    }

    private fun text(status: Int, message: String, extra: Map<String, String> = emptyMap()) =
        IptvResponse.Body(status, "text/plain; charset=utf-8", message.toByteArray(Charsets.UTF_8), extra)

    companion object {
        private val NO_CACHE = mapOf("Cache-Control" to "no-cache, no-store")
        private val CORS = mapOf("Access-Control-Allow-Origin" to "*")

        /** `bytes=a-b`, `bytes=a-` o `bytes=-n`. Devuelve el rango incluyente o `null` si no es satisfacible. */
        fun parseRange(header: String, size: Int): IntRange? {
            val m = Regex("^bytes=(\\d*)-(\\d*)$").find(header.trim()) ?: return null
            val a = m.groupValues[1]
            val b = m.groupValues[2]
            if (a.isEmpty() && b.isEmpty()) return null
            val first: Int
            val last: Int
            if (a.isEmpty()) {
                val n = b.toIntOrNull() ?: return null
                if (n <= 0) return null
                first = (size - n).coerceAtLeast(0); last = size - 1
            } else {
                first = a.toIntOrNull() ?: return null
                last = if (b.isEmpty()) size - 1 else minOf(b.toIntOrNull() ?: return null, size - 1)
            }
            if (first >= size || first > last) return null
            return first..last
        }
    }
}
