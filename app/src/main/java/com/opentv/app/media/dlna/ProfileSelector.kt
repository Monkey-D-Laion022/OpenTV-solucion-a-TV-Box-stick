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
    /** `true` = se entrega la lista HLS (`index.m3u8`) en vez del TS continuo. Solo se usa si el televisor DECLARA formato HLS. */
    val hls: Boolean = false,
)

/** Clave estable de una oferta, para recordar cuál funcionó en un televisor. */
val Offer.key: String get() = "${if (hls) "hls" else "ts"}|${width}x$height|$fps|${protocolInfo.hashCode()}"

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

    /** Algunos LG antiguos solo reconocen este tipo MIME para HLS. */
    const val HLS_PROTOCOL_INFO_ALT = "http-get:*:application/x-mpegURL:DLNA.ORG_OP=00;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"

    /** Pone primero la oferta que ya funcionó en este televisor (si sigue en la lista); el resto conserva su orden. */
    fun preferRemembered(sel: Selection, rememberedKey: String?): Selection {
        val i = sel.attempts.indexOfFirst { it.key == rememberedKey }
        if (rememberedKey == null || i <= 0) return sel
        return Selection(listOf(sel.attempts[i]) + sel.attempts.filterIndexed { j, _ -> j != i },
            sel.reasons + "Se prueba primero el formato que ya funcionó en este televisor: ${sel.attempts[i].label}.")
    }

    const val HLS_PROTOCOL_INFO = "http-get:*:application/vnd.apple.mpegurl:DLNA.ORG_OP=00;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"

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

    /** Quita los formatos que ESTE televisor ya rechazó antes (HTTP 500 / error 704), sin dejar la lista vacía. */
    fun dropRejected(sel: Selection, rejected: Set<String>): Selection {
        if (rejected.isEmpty()) return sel
        val kept = sel.attempts.filter { it.key !in rejected }
        if (kept.isEmpty() || kept.size == sel.attempts.size) return sel
        return Selection(kept, sel.reasons + "Se omiten ${sel.attempts.size - kept.size} formato(s) que este televisor ya rechazó (error 704 / no soportado).")
    }

    /** Perfil con el que el usuario (o el modo automático) arrancó el canal. */
    data class UserProfile(val width: Int, val height: Int, val fps: Int, val presetName: String, val label: String)

    fun select(sink: List<ProtocolInfo>?, user: UserProfile? = null): Selection {
        val reasons = mutableListOf<String>()
        val attempts = mutableListOf<Offer>()
        if (sink.isNullOrEmpty()) {
            reasons += "No se pudo leer la lista de formatos del televisor (GetProtocolInfo): no se supone nada sobre sus capacidades."
            // Evidencia física (no una suposición sobre el receptor): en este LG, HLS 1080p60 de OpenTV se reprodujo bien con el reproductor nativo
            // (por Web Video Caster y directo). Por eso, SOLO cuando el televisor no declaró nada, se intenta primero HLS 1080p60; si no reproduce,
            // se cae al TS genérico 1080p30 que ya funcionaba.
            // Evidencia de la ronda 5: el LG rechazó HLS por DLNA (HTTP 500, error 704 «Format not supported») y solo reprodujo TS. Por eso el primer
            // intento es TS con el perfil del usuario (antes se ignoraba y arrancaba siempre en 1080p30); HLS queda después como alternativa.
            if (user != null && user.height >= 480) {
                attempts += Offer("MPEG‑TS · tu perfil ${user.label}", GENERIC_PROTOCOL_INFO, "MPEG_TS_HD_NA_ISO", user.width, user.height, user.fps, user.presetName, false,
                    "TS con el perfil con el que arrancó el canal; el televisor decide si lo reproduce")
                reasons += "Primer intento: el perfil del canal (${user.label}) por TS, que es lo único que este LG aceptó por DLNA."
            }
            attempts += Offer("HLS · 1080p60 (el televisor no declaró formatos; HLS ya funcionó en este LG)", HLS_PROTOCOL_INFO, null, 1920, 1080, 60, "FULL_HD_60", false,
                "intento a 60 fps por HLS basado en una prueba física previa, no en lo que el televisor declara", hls = true)
            attempts += Offer("HLS · 1080p60 · MIME x-mpegURL", HLS_PROTOCOL_INFO_ALT, null, 1920, 1080, 60, "FULL_HD_60", false,
                "mismo intento con el otro tipo MIME de HLS que usan algunos LG", hls = true)
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
        // HLS solo si el televisor lo declaró (no se supone nada): los reproductores nativos de LG antiguos suelen manejar HLS mejor que TS continuo,
        // pero con más retraso, por eso va DESPUÉS de los intentos TS a 1080p y ANTES de bajar a 480p.
        sink.orEmpty().firstOrNull { it.isHttp && it.contentFormat.lowercase().let { f -> f.contains("mpegurl") } }?.let { p ->
            attempts += Offer("HLS · ${p.contentFormat} · 1080p30", p.literal, p.profileName, 1920, 1080, 30, "FULL_HD", true,
                "HLS declarado por el televisor: se entrega la lista m3u8 (más retraso que TS, pero lo reproduce el reproductor nativo)", hls = true)
            reasons += "El televisor declara HLS (${p.contentFormat}): se agrega como intento antes de bajar a 480p."
        }
        attempts += Offer("genérico MPEG‑TS · 480p30", GENERIC_PROTOCOL_INFO.replace("_HD_", "_SD_"), "MPEG_TS_SD_NA_ISO", 854, 480, 30, "COMPAT_480", false, "respaldo de menor exigencia")
        reasons += "Se probarán ${attempts.size} variante(s), de mayor a menor preferencia, y se avanza a la siguiente si el televisor no llega a reproducir."
        return Selection(attempts.distinctBy { it.protocolInfo + it.height + it.fps + it.hls }, reasons)
    }
}
