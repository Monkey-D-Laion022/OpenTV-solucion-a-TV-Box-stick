package com.opentv.app.catalog

/** Convierte las respuestas JSON de TMDB. PURO. Nada de lo que se lee acá incluye ni necesita las claves. */
object TmdbParser {
    private const val MAX_ITEMS = 20

    fun parseConfiguration(json: String): ImageConfig? = try {
        val images = MiniJson.asMap(MiniJson.asMap(MiniJson.parse(json))?.get("images"))
        val base = MiniJson.string(images, "secure_base_url")
        if (base.isNullOrBlank() || !base.startsWith("https://")) {
            null
        } else {
            ImageConfig(
                baseUrl = base,
                posterSizes = strings(images?.get("poster_sizes")),
                backdropSizes = strings(images?.get("backdrop_sizes")),
            )
        }
    } catch (e: JsonException) {
        null
    }

    private fun strings(v: Any?): List<String> = MiniJson.asList(v)?.filterIsInstance<String>() ?: emptyList()

    /** `null` = la respuesta no es una lista válida de TMDB; lista vacía = es válida pero sin resultados. */
    fun parseList(json: String, defaultKind: CatalogKind?): List<CatalogItem>? = try {
        val results = MiniJson.asList(MiniJson.asMap(MiniJson.parse(json))?.get("results"))
        results?.mapNotNull { item(MiniJson.asMap(it), defaultKind) }?.take(MAX_ITEMS)
    } catch (e: JsonException) {
        null
    }

    private fun item(m: Map<String, Any?>?, defaultKind: CatalogKind?): CatalogItem? {
        if (m == null) return null
        val kind = when (MiniJson.string(m, "media_type")) {
            "movie" -> CatalogKind.MOVIE
            "tv" -> CatalogKind.TV
            null -> defaultKind
            else -> null // «person» u otros: no son títulos
        } ?: return null
        val id = MiniJson.long(m, "id") ?: return null
        val title = (MiniJson.string(m, "title") ?: MiniJson.string(m, "name"))?.trim()
        if (title.isNullOrEmpty()) return null
        val rating = MiniJson.double(m, "vote_average")?.takeIf { it > 0.0 }
        val date = (MiniJson.string(m, "release_date") ?: MiniJson.string(m, "first_air_date"))?.takeIf { it.isNotBlank() }
        return CatalogItem(
            id = id,
            kind = kind,
            title = title,
            overview = MiniJson.string(m, "overview")?.trim().orEmpty(),
            posterPath = path(MiniJson.string(m, "poster_path")),
            backdropPath = path(MiniJson.string(m, "backdrop_path")),
            rating = rating,
            date = date,
        )
    }

    /** Solo se aceptan rutas del estilo `/abc.jpg` (se arman URLs con ellas). */
    private fun path(p: String?): String? = p?.takeIf { it.startsWith("/") && it.length > 1 && it.none { c -> c == ' ' || c == '?' || c == '#' } }
}
