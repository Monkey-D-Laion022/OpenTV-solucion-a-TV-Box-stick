package com.opentv.app.miracast

/**
 * Etapa B: mensajes RTSP mínimos del intercambio Wi-Fi Display (WFD) entre fuente y receptor. Lógica PURA (sin Android
 * ni red): arma los mensajes que OpenTV envía y descompone los que recibe.
 *
 * Alcance: solo M1, M2 y M3 (negociación de capacidades de LECTURA). No se envían M4 ni M5, no hay SETUP/PLAY y no se
 * transmite video. Los encabezados exactos de M1–M3 siguen el formato habitual de WFD/AOSP y NO se verificaron contra
 * un receptor real en esta sesión: si el televisor los rechaza, el diagnóstico lo registra tal cual.
 */
data class RtspMessage(
    val isRequest: Boolean,
    val method: String?,
    val uri: String?,
    val statusCode: Int?,
    val reason: String?,
    /** Encabezados con el nombre en minúsculas. */
    val headers: Map<String, String>,
    val body: String,
) {
    val cseq: Int? get() = headers["cseq"]?.trim()?.toIntOrNull()
    fun header(name: String): String? = headers[name.lowercase()]

    /** Primera línea resumida, para el registro. */
    val summary: String
        get() = if (isRequest) "${method ?: "?"} ${uri ?: ""}".trim() else "RTSP ${statusCode ?: "?"} ${reason ?: ""}".trim()
}

/** Parser incremental: se le entregan los bytes tal como llegan y devuelve cada mensaje completo. */
class RtspParser {
    private var buffer = ByteArray(0)

    /** `true` si el receptor mandó más de [MAX_BYTES] sin completar un mensaje: se descarta y se informa. */
    var overflow: Boolean = false
        private set

    fun feed(bytes: ByteArray, length: Int) {
        if (overflow || length <= 0) return
        if (buffer.size + length > MAX_BYTES) {
            overflow = true
            buffer = ByteArray(0)
            return
        }
        buffer += bytes.copyOfRange(0, length)
    }

    /** Siguiente mensaje completo, o `null` si todavía faltan datos (o el formato no es RTSP). */
    fun next(): RtspMessage? {
        val text = String(buffer, Charsets.ISO_8859_1)
        val crlf = text.indexOf("\r\n\r\n")
        val lf = text.indexOf("\n\n")
        val (headerEnd, sepLen) = when {
            crlf >= 0 && (lf < 0 || crlf <= lf) -> crlf to 4
            lf >= 0 -> lf to 2
            else -> return null
        }
        val head = text.substring(0, headerEnd)
        val lines = head.split(Regex("\r?\n"))
        val first = lines.firstOrNull()?.trim().orEmpty()
        if (first.isEmpty()) {
            buffer = buffer.copyOfRange(headerEnd + sepLen, buffer.size)
            return null
        }
        val headers = LinkedHashMap<String, String>()
        for (line in lines.drop(1)) {
            val i = line.indexOf(':')
            if (i > 0) headers[line.substring(0, i).trim().lowercase()] = line.substring(i + 1).trim()
        }
        val length = headers["content-length"]?.trim()?.toIntOrNull() ?: 0
        val total = headerEnd + sepLen + length
        if (length < 0 || total > MAX_BYTES) {
            overflow = true
            buffer = ByteArray(0)
            return null
        }
        if (buffer.size < total) return null
        val body = String(buffer, headerEnd + sepLen, length, Charsets.ISO_8859_1)
        buffer = buffer.copyOfRange(total, buffer.size)

        return if (first.startsWith("RTSP/")) {
            val parts = first.split(' ', limit = 3)
            RtspMessage(false, null, null, parts.getOrNull(1)?.toIntOrNull(), parts.getOrNull(2), headers, body)
        } else {
            val parts = first.split(' ', limit = 3)
            RtspMessage(true, parts[0].uppercase(), parts.getOrNull(1), null, null, headers, body)
        }
    }

    companion object {
        const val MAX_BYTES = 64 * 1024
    }
}

object WfdRtsp {
    const val REQUIRE = "org.wfa.wfd1.0"

    /** Parámetros de M3 marcados como obligatorios (M/M) en la tabla de la librería WDS: video, audio y puertos RTP. */
    val M3_PARAMS: List<String> = listOf("wfd_video_formats", "wfd_audio_codecs", "wfd_client_rtp_ports")

    /** M1: la fuente consulta qué métodos admite el receptor. */
    fun m1(cseq: Int): String = "OPTIONS * RTSP/1.0\r\nCSeq: $cseq\r\nRequire: $REQUIRE\r\n\r\n"

    /** Respuesta de la fuente al M2 (OPTIONS del receptor). */
    fun m2Response(cseq: Int): String =
        "RTSP/1.0 200 OK\r\nCSeq: $cseq\r\nPublic: $REQUIRE, SETUP, TEARDOWN, PLAY, PAUSE, GET_PARAMETER, SET_PARAMETER\r\n\r\n"

    /** M3: la fuente pide las capacidades del receptor (solo lectura). */
    fun m3(cseq: Int, params: List<String> = M3_PARAMS): String {
        val body = params.joinToString("") { it + "\r\n" }
        return "GET_PARAMETER rtsp://localhost/wfd1.0 RTSP/1.0\r\nCSeq: $cseq\r\nContent-Type: text/parameters\r\n" +
            "Content-Length: ${body.toByteArray(Charsets.ISO_8859_1).size}\r\n\r\n$body"
    }

    /** Respuesta genérica 200 OK (por ejemplo, a un GET_PARAMETER vacío de mantenimiento). */
    fun ok(cseq: Int): String = "RTSP/1.0 200 OK\r\nCSeq: $cseq\r\n\r\n"

    /** Respuesta de método no admitido: OpenTV no implementa SETUP/PLAY en esta etapa. */
    fun notAllowed(cseq: Int): String = "RTSP/1.0 405 Method Not Allowed\r\nCSeq: $cseq\r\n\r\n"

    /** Líneas «nombre: valor» del cuerpo de una respuesta GET_PARAMETER. Los nombres sin valor se ignoran. */
    fun parseParameters(body: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (line in body.split(Regex("\r?\n"))) {
            val i = line.indexOf(':')
            if (i > 0) out[line.substring(0, i).trim()] = line.substring(i + 1).trim()
        }
        return out
    }
}
