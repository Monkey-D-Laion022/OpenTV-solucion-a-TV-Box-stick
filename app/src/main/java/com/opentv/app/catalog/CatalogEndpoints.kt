package com.opentv.app.catalog

/** Tipo de credencial de TMDB, deducido de su forma (la clave en sí nunca se registra ni se muestra). */
enum class TmdbCredentialKind { API_KEY_V3, BEARER_TOKEN_V4, INVALID }

/** Una petición lista para enviar. [url] puede contener la clave (v3): nunca se escribe en registros. */
class CatalogRequest(val url: String, val headers: Map<String, String>) {
    override fun toString(): String = "CatalogRequest(…)" // evita que la clave salga por accidente en un registro
}

object TmdbEndpoints {
    const val HOST = "https://api.themoviedb.org/3"
    const val LANGUAGE = "es-AR"

    fun kindOf(raw: String): TmdbCredentialKind {
        val k = raw.trim()
        return when {
            k.startsWith("eyJ") && k.length >= 60 && k.count { it == '.' } == 2 && k.all { it.isLetterOrDigit() || it in "-_." } ->
                TmdbCredentialKind.BEARER_TOKEN_V4
            k.length in 20..64 && k.all { it.isLetterOrDigit() } -> TmdbCredentialKind.API_KEY_V3
            else -> TmdbCredentialKind.INVALID
        }
    }

    private fun build(path: String, query: String, credential: String): CatalogRequest? {
        val key = credential.trim()
        return when (kindOf(key)) {
            TmdbCredentialKind.BEARER_TOKEN_V4 -> CatalogRequest(
                url = "$HOST$path" + if (query.isEmpty()) "" else "?$query",
                headers = mapOf("Authorization" to "Bearer $key", "Accept" to "application/json"),
            )
            TmdbCredentialKind.API_KEY_V3 -> CatalogRequest(
                url = "$HOST$path?" + (if (query.isEmpty()) "" else "$query&") + "api_key=$key",
                headers = mapOf("Accept" to "application/json"),
            )
            TmdbCredentialKind.INVALID -> null
        }
    }

    fun configuration(credential: String): CatalogRequest? = build("/configuration", "", credential)

    fun list(row: CatalogRowKind, credential: String): CatalogRequest? =
        build(row.path, "language=$LANGUAGE&page=1", credential)
}

/** Direcciones de RPDB. La clave va en la ruta: la dirección completa NUNCA se registra ni se usa como nombre de archivo. */
object RpdbEndpoints {
    const val HOST = "https://api.ratingposterdb.com"

    /** Formato de identificador de TMDB en RPDB (película: `movie-<id>`, serie: `series-<id>`). Sin confirmar con la documentación oficial. */
    fun poster(key: String, item: CatalogItem): CatalogRequest =
        CatalogRequest("$HOST/${key.trim()}/tmdb/poster-default/${item.kind.rpdbPrefix}-${item.id}.jpg?fallback=true", emptyMap())

    /** Prueba de la clave con un título conocido, con el formato por IMDb que documentan los servicios compatibles. */
    fun probe(key: String): CatalogRequest =
        CatalogRequest("$HOST/${key.trim()}/imdb/poster-default/tt0111161.jpg", emptyMap())
}

/** Elige el tamaño de imagen de TMDB más chico que alcance para el ancho pedido. */
object ImageSizing {
    fun pick(sizes: List<String>, targetWidthPx: Int, fallback: String): String {
        val numeric = sizes.mapNotNull { s -> s.removePrefix("w").takeIf { s.startsWith("w") }?.toIntOrNull()?.let { s to it } }.sortedBy { it.second }
        if (numeric.isEmpty()) return fallback
        return (numeric.firstOrNull { it.second >= targetWidthPx } ?: numeric.last()).first
    }

    fun url(config: ImageConfig, path: String, size: String): String = config.baseUrl.trimEnd('/') + "/" + size + path

    /** Factor de reducción (potencia de 2) para decodificar sin pasarse del ancho pedido: el resultado nunca queda más angosto que [targetWidth]. */
    fun sampleSize(sourceWidth: Int, targetWidth: Int): Int {
        if (sourceWidth <= 0 || targetWidth <= 0) return 1
        var sample = 1
        while (sourceWidth / (sample * 2) >= targetWidth) sample *= 2
        return sample
    }

    /** Nombre de archivo de caché: depende del tipo de imagen y su identidad, nunca de una clave ni de una dirección con clave. */
    fun cacheKey(vararg parts: String): String {
        var h = -0x340d631b7bdddcdbL // FNV-1a 64 bits
        for (c in parts.joinToString("|")) {
            h = h xor c.code.toLong()
            h *= 0x100000001b3L
        }
        return java.lang.Long.toHexString(h)
    }
}
