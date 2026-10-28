package com.opentv.app.miracast

/**
 * Etapa B: diagnóstico de la negociación Miracast/WFD. Lógica PURA: fases, observaciones y el texto del informe.
 *
 * Reglas del informe: (1) «Recibido del televisor» solo contiene lo que llegó por la red; (2) «Decodificado» es la
 * traducción mecánica de ese texto con la tabla de AOSP; (3) «Inferencias» son hipótesis de OpenTV y se rotulan así.
 * Un resultado negativo nunca se presenta como prueba sobre los 60 FPS.
 */
object WfdTimeouts {
    /** Espera de la formación del grupo Wi-Fi Direct (mismo valor que CONNECTION_TIMEOUT_SECONDS de AOSP). */
    const val GROUP_SECONDS = 30

    /** Espera de la conexión TCP del televisor y de la negociación RTSP (RTSP_TIMEOUT_SECONDS de AOSP). */
    const val RTSP_SECONDS = 30

    /** Puerto de control RTSP por defecto de WFD (DEFAULT_CONTROL_PORT de AOSP). */
    const val RTSP_PORT = 7236

    /** AOSP reintenta hasta 3 veces la solicitud cuando el sistema está ocupado, con 500 ms de pausa. */
    const val CONNECT_MAX_RETRIES = 3
    const val CONNECT_RETRY_DELAY_MS = 500L
}

enum class WfdPhase(val label: String) {
    CONNECT_REQUEST("Solicitud de conexión Wi-Fi Direct"),
    GROUP_FORMATION("Formación del grupo Wi-Fi Direct"),
    LISTENING("Escucha RTSP en el grupo"),
    TCP_FROM_TV("Conexión TCP del televisor"),
    M1("M1 OPTIONS (OpenTV → TV)"),
    M2("M2 OPTIONS (TV → OpenTV)"),
    M3("M3 GET_PARAMETER (capacidades)"),
    CLEANUP("Cierre y limpieza"),
}

enum class StepOutcome(val label: String) {
    OK("OK"),
    FAILED("FALLÓ"),
    TIMEOUT("TIEMPO AGOTADO"),
    SKIPPED("NO ALCANZADA"),
    CANCELLED("CANCELADA"),
}

data class WfdStep(val phase: WfdPhase, val outcome: StepOutcome, val atMs: Long, val detail: String)

/** Todo lo observado durante una prueba. Cada campo es `null` mientras no se haya observado. */
data class WfdObservation(
    val peerName: String = "",
    val connectAccepted: Boolean? = null,
    val connectFailureCode: Int? = null,
    val groupFormed: Boolean? = null,
    val weAreGroupOwner: Boolean? = null,
    val groupInterface: String? = null,
    val localAddress: String? = null,
    val groupOwnerAddress: String? = null,
    val listening: Boolean? = null,
    val tvRemoteAddress: String? = null,
    val m1Status: Int? = null,
    val m1Public: String? = null,
    val m2Method: String? = null,
    val m2Public: String? = null,
    val m2Require: String? = null,
    val m3Status: Int? = null,
    val m3Params: Map<String, String> = emptyMap(),
    val cancelled: Boolean = false,
    val finished: Boolean = false,
)

enum class WfdVerdict { NOT_FINISHED, CANCELLED, NO_REQUEST, NO_GROUP, NO_TCP, RTSP_INCOMPLETE, FORMATS_UNPARSED, FORMATS_WITHOUT_60, FORMATS_WITH_60 }

object WfdDiagnosticText {
    const val NA = "no disponible"

    fun videoFormats(obs: WfdObservation): WfdVideoFormats? =
        obs.m3Params["wfd_video_formats"]?.let { WfdVideoFormats.parse(it) }

    fun verdict(obs: WfdObservation): WfdVerdict = when {
        obs.cancelled -> WfdVerdict.CANCELLED
        !obs.finished -> WfdVerdict.NOT_FINISHED
        obs.connectAccepted != true -> WfdVerdict.NO_REQUEST
        obs.groupFormed != true -> WfdVerdict.NO_GROUP
        obs.tvRemoteAddress == null -> WfdVerdict.NO_TCP
        obs.m3Status != 200 || obs.m3Params["wfd_video_formats"] == null -> WfdVerdict.RTSP_INCOMPLETE
        videoFormats(obs) == null -> WfdVerdict.FORMATS_UNPARSED
        videoFormats(obs)?.has1080p60 == true -> WfdVerdict.FORMATS_WITH_60
        else -> WfdVerdict.FORMATS_WITHOUT_60
    }

    /** SOLO lo que llegó del televisor (o de Android sobre el grupo). Sin interpretación. */
    fun received(obs: WfdObservation): List<String> {
        val out = mutableListOf<String>()
        if (obs.peerName.isNotBlank()) out += "Nombre anunciado por Wi-Fi Direct: ${obs.peerName}"
        if (obs.groupFormed == true) {
            out += "Grupo Wi-Fi Direct formado. Propietario del grupo: ${if (obs.weAreGroupOwner == true) "este teléfono" else "el otro dispositivo"}" +
                " · interfaz: ${obs.groupInterface ?: NA} · dirección propia: ${obs.localAddress ?: NA} · dirección del propietario: ${obs.groupOwnerAddress ?: NA}"
        }
        obs.tvRemoteAddress?.let { out += "El televisor abrió una conexión TCP desde: $it" }
        obs.m1Status?.let { out += "Respuesta al M1: RTSP $it" + (obs.m1Public?.let { p -> " · Public: $p" } ?: "") }
        obs.m2Method?.let {
            out += "M2 recibido: $it" + (obs.m2Public?.let { p -> " · Public: $p" } ?: "") + (obs.m2Require?.let { r -> " · Require: $r" } ?: "")
        }
        obs.m3Status?.let { out += "Respuesta al M3: RTSP $it" }
        for ((k, v) in obs.m3Params) out += "$k (texto crudo): $v"
        return out
    }

    /** Traducción mecánica del texto de formatos de video con la tabla de AOSP. */
    fun decoded(obs: WfdObservation): List<String> {
        val raw = obs.m3Params["wfd_video_formats"] ?: return emptyList()
        val f = WfdVideoFormats.parse(raw)
            ?: return listOf("No se pudo decodificar wfd_video_formats: el texto no tiene la forma esperada (ver el texto crudo).")
        if (f.noVideo) return listOf("El receptor declaró «none»: sin formatos de video.")
        val out = mutableListOf<String>()
        out += "Modos declarados (${f.modes.size}): " + f.modes.joinToString(", ") { it.label }
        out += "Mayor frecuencia progresiva declarada: ${f.maxProgressiveFps?.let { "$it Hz" } ?: NA}"
        out += "1920×1080 a 60 (progresivo) declarado: ${if (f.has1080p60) "SÍ" else "no"}"
        out += "1920×1080 a 30 (progresivo) declarado: ${if (f.supports(1920, 1080, 30)) "sí" else "no"}"
        out += "Perfiles H.264 (máscara): ${f.profiles} · niveles (máscara): ${f.levels}"
        return out
    }

    /** Hipótesis de OpenTV. Cada línea dice qué se observó y qué NO se pudo comprobar. */
    fun inferences(obs: WfdObservation): List<String> {
        val v = verdict(obs)
        val out = mutableListOf<String>()
        when (v) {
            WfdVerdict.NOT_FINISHED -> out += "La prueba todavía no terminó."
            WfdVerdict.CANCELLED -> out += "Prueba cancelada por el usuario: no hay conclusión."
            WfdVerdict.NO_REQUEST ->
                out += "Android no aceptó la solicitud de conexión" + (obs.connectFailureCode?.let { " (código $it)" } ?: "") +
                    ". INCONCLUSO: no se llegó a hablar con el televisor."
            WfdVerdict.NO_GROUP ->
                out += "La solicitud se envió pero no se formó el grupo en ${WfdTimeouts.GROUP_SECONDS} s. Puede que el televisor no la aceptó, " +
                    "que no era un receptor conectable o que el pedido en su pantalla no se respondió. INCONCLUSO."
            WfdVerdict.NO_TCP ->
                out += "El grupo se formó pero el televisor no abrió conexión TCP en ${WfdTimeouts.RTSP_SECONDS} s. " +
                    "Hipótesis (no verificada): sin el anuncio Wi-Fi Display de fuente, que Android reserva a apps del sistema, " +
                    "el televisor puede no saber que OpenTV es una fuente ni en qué puerto escucha. " +
                    "INCONCLUSO sobre formatos y 60 FPS: no se pudo preguntar."
            WfdVerdict.RTSP_INCOMPLETE ->
                out += "El televisor se conectó pero la negociación no llegó a entregar wfd_video_formats (ver las fases). INCONCLUSO."
            WfdVerdict.FORMATS_UNPARSED ->
                out += "El televisor entregó wfd_video_formats pero OpenTV no pudo decodificarlo. Mirá el texto crudo: no se sacan conclusiones."
            WfdVerdict.FORMATS_WITHOUT_60 ->
                out += "El texto del televisor NO declara 1080p60. Hipótesis: con esta lista, un emisor propio tampoco podría negociar 60 Hz a 1080p. " +
                    "Salvedad: el televisor pudo ofrecer una lista distinta a una fuente que no se anunció como Wi-Fi Display (no verificado)."
            WfdVerdict.FORMATS_WITH_60 ->
                out += "El texto del televisor SÍ declara 1080p60. Declarar no es garantía: falta transmitir de verdad y medir. " +
                    "Habría una vía para estudiar un emisor propio (fase siguiente, con autorización)."
        }
        out += "Esta prueba no transmite video ni demuestra que se pueda lograr 60 FPS."
        return out
    }

    fun stepLine(s: WfdStep): String =
        String.format(java.util.Locale.US, "%s: %s (+%.1f s)", s.phase.label, s.outcome.label, s.atMs / 1000.0) +
            if (s.detail.isNotBlank()) " — ${s.detail}" else ""
}
