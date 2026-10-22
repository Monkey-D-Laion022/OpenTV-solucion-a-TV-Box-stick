package com.opentv.app.connect

/**
 * Tipo de contenido que se declara al receptor Cast, deducido de la extensión del enlace.
 * El receptor predeterminado lo necesita; si no se reconoce se usa `video/mp4`. Lógica pura.
 */
object CastMime {

    private const val DEFAULT = "video/mp4"

    fun guess(url: String): String {
        val path = url.substringBefore('#').substringBefore('?').lowercase()
        return when (path.substringAfterLast('.', missingDelimiterValue = "")) {
            "m3u8" -> "application/x-mpegURL"
            "mpd" -> "application/dash+xml"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mp3" -> "audio/mpeg"
            "m4a", "aac" -> "audio/mp4"
            else -> DEFAULT
        }
    }
}
