package com.opentv.app.connect

data class HttpResponse(val status: Int, val headers: Map<String, String>, val body: String)

/**
 * Mensajes HTTP/1.1 mínimos para hablar con equipos UPnP en la red local. Se arman a mano sobre
 * un socket para no habilitar tráfico HTTP sin cifrar en toda la app (la mayoría de los TV solo
 * publica HTTP simple): solo se usa con las IPv4 privadas validadas en [LanAddress].
 */
object HttpMessages {

    private const val USER_AGENT = "OpenTV/0.1 UPnP/1.0"
    private val CRLF = "\r\n"

    fun buildGet(endpoint: LanEndpoint): ByteArray {
        requireSafePath(endpoint)
        val text = "GET ${endpoint.path} HTTP/1.1$CRLF" +
            "Host: ${endpoint.host}:${endpoint.port}$CRLF" +
            "User-Agent: $USER_AGENT$CRLF" +
            "Connection: close$CRLF$CRLF"
        return text.toByteArray(Charsets.ISO_8859_1)
    }

    fun buildPost(endpoint: LanEndpoint, soapAction: String, body: String): ByteArray {
        requireSafePath(endpoint)
        require(soapAction.none { it.code < 0x20 || it.code == 0x7F }) { "SOAPAction inválida" }
        val payload = body.toByteArray(Charsets.UTF_8)
        val head = "POST ${endpoint.path} HTTP/1.1$CRLF" +
            "Host: ${endpoint.host}:${endpoint.port}$CRLF" +
            "User-Agent: $USER_AGENT$CRLF" +
            "Content-Type: text/xml; charset=\"utf-8\"$CRLF" +
            "SOAPAction: $soapAction$CRLF" +
            "Content-Length: ${payload.size}$CRLF" +
            "Connection: close$CRLF$CRLF"
        return head.toByteArray(Charsets.ISO_8859_1) + payload
    }

    /** Lee una respuesta completa. Devuelve `null` si no se puede interpretar. */
    fun parseResponse(raw: ByteArray): HttpResponse? {
        val headerEnd = indexOf(raw, "\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
        if (headerEnd < 0) return null

        val headLines = String(raw, 0, headerEnd, Charsets.ISO_8859_1).split("\r\n")
        val statusParts = headLines.first().split(" ", limit = 3)
        if (statusParts.size < 2 || !statusParts[0].startsWith("HTTP/")) return null
        val status = statusParts[1].toIntOrNull() ?: return null

        val headers = LinkedHashMap<String, String>()
        for (line in headLines.drop(1)) {
            val colon = line.indexOf(':')
            if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }

        var body = raw.copyOfRange(headerEnd + 4, raw.size)
        if (headers["transfer-encoding"]?.contains("chunked", ignoreCase = true) == true) {
            body = decodeChunked(body)
        } else {
            val length = headers["content-length"]?.toIntOrNull()
            if (length != null && length >= 0 && length < body.size) body = body.copyOf(length)
        }
        return HttpResponse(status, headers, String(body, Charsets.UTF_8))
    }

    private fun decodeChunked(data: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        var pos = 0
        while (pos < data.size) {
            val lineEnd = indexOf(data, "\r\n".toByteArray(Charsets.ISO_8859_1), pos)
            if (lineEnd < 0) break
            val sizeText = String(data, pos, lineEnd - pos, Charsets.ISO_8859_1).substringBefore(';').trim()
            val size = sizeText.toIntOrNull(16) ?: break
            if (size == 0) break
            val start = lineEnd + 2
            val end = minOf(start + size, data.size)
            out.write(data, start, end - start)
            pos = end + 2
        }
        return out.toByteArray()
    }

    private fun requireSafePath(endpoint: LanEndpoint) {
        require(endpoint.path.startsWith("/") && endpoint.path.none { it.code <= 0x20 || it.code == 0x7F }) {
            "Ruta HTTP inválida"
        }
    }

    private fun indexOf(data: ByteArray, pattern: ByteArray, from: Int = 0): Int {
        if (pattern.isEmpty()) return from
        var i = from
        while (i <= data.size - pattern.size) {
            var match = true
            for (j in pattern.indices) {
                if (data[i + j] != pattern[j]) {
                    match = false
                    break
                }
            }
            if (match) return i
            i++
        }
        return -1
    }
}
