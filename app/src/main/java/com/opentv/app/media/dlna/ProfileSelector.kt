package com.opentv.app.media.dlna

/**
 * Una forma concreta de ofrecerle el flujo al televisor.
 * [presetName] es el nombre del perfil de IPTV recomendado (texto, para no acoplar esta capa al motor).
 */
data class Offer(
    val label: String,
    val protocolInfo: String,
    val profileName: String?,
    val width: Int,
    val height: Int,
    val fps: Int,
    val presetName: String,
    /** `true` si el televisor LO LISTÓ; `false` si es el perfil genérico de respaldo. */
    val declaredByRenderer: Boolean,
    val note: String,
)

data class Selection(val attempts: List<Offer>, val reasons: List<String>)

/**
 * Elige qué formato ofrecerle al televisor según lo que declaró en `GetProtocolInfo`. Lógica pura.
 *
 * Reglas (todas con su motivo en [Selection.reasons]):
 * - Solo MPEG‑TS por HTTP: es lo que el motor produce con H.264 y AAC en un mismo flujo (una sola base de tiempos).
 * - Perfiles `AVC_TS_*` primero (H.264); entre ellos, los que declaran AAC. Los que exigen AC3, LPCM o MP3 se descartan: el motor solo emite AAC.
 * - Después, formatos TS sin perfil (`*`). Los perfiles `MPEG_TS_*` (video MPEG‑2) van al final: no prometen H.264.
 * - Siempre se agrega al final el perfil genérico que ya reprodujo H.264 en un LG en pruebas anteriores (con audio en silencio).
 * - Resolución: perfiles HD → 1080p30; SD → 480p; CIF o menores se descartan. Ningún perfil AVC_TS de DLNA declara 1080p60, así que
 *   por DLNA no se ofrece 60 fps (a confirmar con el LG real).
 */
object ProfileSelector {
    const val GENERIC_PROTOCOL_INFO =
        "http-get:*:video/mpeg:DLNA.ORG_PN=MPEG_TS_HD_NA_ISO;DLNA.ORG_OP=00;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"

    private const val LIVE_FLAGS = "DLNA.ORG_OP=00;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"

    private enum class Kind(val rank: Int) { AVC_AAC(0), AVC_OTHER(1), WILDCARD(2), MPEG2_PROFILE(3) }

    private fun isTsFormat(f: String): Boolean {
        val x = f.lowercase()
        return x.contains("mpeg") || x.contains("mp2t") || x.contains("m2ts")
    }

    private fun classify(p: ProtocolInfo): Kind? {
        if (!p.isHttp || !isTsFormat(p.contentFormat)) return null
        val pn = p.profileName?.uppercase()
        return when {
            pn == null -> Kind.WILDCARD
            pn.startsWith("AVC_TS") -> {
                val audioOk = pn.contains("AAC") || pn.contains("HEAAC")
                val wrongAudio = pn.contains("AC3") || pn.contains("LPCM") || pn.contains("MP3") || pn.contains("DTS")
                when {
                    pn.contains("CIF") -> null
                    audioOk -> Kind.AVC_AAC
                    wrongAudio -> null
                    else -> Kind.AVC_OTHER
                }
            }
            pn.startsWith("MPEG_TS") -> Kind.MPEG2_PROFILE
            else -> null
        }
    }

    private fun sizeFor(p: ProtocolInfo): Triple<Int, Int, String> {
        val pn = p.profileName?.uppercase().orEmpty()
        return if (pn.contains("_SD_")) Triple(854, 480, "COMPAT_480") else Triple(1920, 1080, "FULL_HD")
    }

    fun select(sink: List<ProtocolInfo>?): Selection {
        val reasons = mutableListOf<String>()
        val attempts = mutableListOf<Offer>()
        if (sink.isNullOrEmpty()) {
            reasons += "No se pudo leer la lista de formatos del televisor (GetProtocolInfo): se usa solo el perfil genérico, primero 1080p y luego 480p."
        } else {
            reasons += "El televisor declara ${sink.size} formato(s); ${sink.count { classify(it) != null }} sirven para MPEG‑TS con H.264 y AAC."
            val ranked = sink.mapNotNull { p -> classify(p)?.let { p to it } }.sortedWith(compareBy({ it.second.rank }, { sizeFor(it.first).second * -1 }))
            for ((p, kind) in ranked) {
                val (w, h, preset) = sizeFor(p)
                val note = when (kind) {
                    Kind.AVC_AAC -> "perfil H.264 con AAC declarado por el televisor"
                    Kind.AVC_OTHER -> "perfil H.264 declarado; el audio AAC no figura en su nombre"
                    Kind.WILDCARD -> "formato TS sin perfil: el televisor decide al reproducir"
                    Kind.MPEG2_PROFILE -> "perfil de video MPEG‑2: puede rechazar H.264"
                }
                val info = if (p.additional.contains("DLNA.ORG_OP", true)) p.literal else "${p.protocol}:${p.network}:${p.contentFormat}:" +
                    (if (p.additional == "*" || p.additional.isBlank()) "DLNA.ORG_OP=00;DLNA.ORG_CI=0" else "${p.additional};$LIVE_FLAGS")
                attempts += Offer("${p.contentFormat} · ${p.profileName ?: "sin perfil"} · ${h}p30", info, p.profileName, w, h, 30, preset, true, note)
            }
            if (attempts.isEmpty()) reasons += "Ningún formato declarado sirve para MPEG‑TS con H.264 y AAC; se prueba el genérico por si el televisor es más permisivo de lo que declara."
        }
        attempts += Offer("genérico MPEG‑TS · 1080p30", GENERIC_PROTOCOL_INFO, "MPEG_TS_HD_NA_ISO", 1920, 1080, 30, "FULL_HD", false, "perfil genérico que ya reprodujo H.264 en un LG (con audio en silencio)")
        attempts += Offer("genérico MPEG‑TS · 480p30", GENERIC_PROTOCOL_INFO.replace("_HD_", "_SD_"), "MPEG_TS_SD_NA_ISO", 854, 480, 30, "COMPAT_480", false, "respaldo de menor exigencia")
        reasons += "Se probarán ${attempts.size} variante(s), de mayor a menor preferencia, y se avanza a la siguiente si el televisor no llega a reproducir."
        return Selection(attempts.distinctBy { it.protocolInfo + it.height }, reasons)
    }
}
