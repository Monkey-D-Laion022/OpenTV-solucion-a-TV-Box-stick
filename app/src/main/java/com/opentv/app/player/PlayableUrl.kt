package com.opentv.app.player

import java.net.URI
import java.net.URISyntaxException

sealed interface PlayableUrlCheck {
    data class Valid(val url: String) : PlayableUrlCheck
    data class Invalid(val reason: String) : PlayableUrlCheck
}

/**
 * Validación del enlace que el usuario pega en el reproductor. Lógica pura.
 * Solo http y https: nada de file://, content:// ni esquemas de otras apps.
 */
object PlayableUrl {

    private const val MAX_LENGTH = 2048

    fun check(raw: String): PlayableUrlCheck {
        val text = raw.trim()
        if (text.isEmpty()) return PlayableUrlCheck.Invalid("Pegá un enlace para reproducir.")
        if (text.length > MAX_LENGTH) return PlayableUrlCheck.Invalid("El enlace es demasiado largo.")
        if (text.any { it.isWhitespace() || it.isISOControl() }) {
            return PlayableUrlCheck.Invalid("El enlace no puede contener espacios ni saltos de línea.")
        }
        val uri = try {
            URI(text)
        } catch (e: URISyntaxException) {
            return PlayableUrlCheck.Invalid("El enlace no tiene un formato válido.")
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            return PlayableUrlCheck.Invalid("Solo se admiten enlaces que empiecen con http:// o https://.")
        }
        if (uri.host.isNullOrBlank()) {
            return PlayableUrlCheck.Invalid("Al enlace le falta la dirección del servidor.")
        }
        return PlayableUrlCheck.Valid(text)
    }
}
