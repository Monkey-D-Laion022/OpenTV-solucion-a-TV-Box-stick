package com.opentv.app.catalog

/** Tipo de título. [rpdbPrefix] es el prefijo que usa RPDB para identificar un título de TMDB. */
enum class CatalogKind(val tmdbPath: String, val rpdbPrefix: String, val label: String) {
    MOVIE("movie", "movie", "Película"),
    TV("tv", "series", "Serie"),
}

/** Un título del catálogo (información de TMDB). Que figure acá NO significa que OpenTV pueda reproducirlo. */
data class CatalogItem(
    val id: Long,
    val kind: CatalogKind,
    val title: String,
    val overview: String,
    val posterPath: String?,
    val backdropPath: String?,
    val rating: Double?,
    val date: String?,
) {
    val year: String? get() = date?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) }
}

/** Las filas del Inicio. */
enum class CatalogRowKind(val id: String, val title: String, val path: String, val defaultKind: CatalogKind?) {
    TRENDING("trending", "Tendencias", "/trending/all/week", null),
    MOVIES("movies", "Películas populares", "/movie/popular", CatalogKind.MOVIE),
    SERIES("series", "Series populares", "/tv/popular", CatalogKind.TV),
    UPCOMING("upcoming", "Próximos estrenos", "/movie/upcoming", CatalogKind.MOVIE),
}

data class CatalogRow(val kind: CatalogRowKind, val items: List<CatalogItem>)

/** Dirección base y tamaños de imagen que informa TMDB en `/configuration`. */
data class ImageConfig(val baseUrl: String, val posterSizes: List<String>, val backdropSizes: List<String>)

/** Por qué el catálogo no está completo. */
enum class CatalogProblem { NO_CREDENTIAL, REJECTED, RATE_LIMITED, NETWORK, SERVER, PARSE }

/** Resultado de una prueba de conexión desde Ajustes. [ok] `null` = no se probó. */
data class TestResult(val ok: Boolean?, val message: String, val atMillis: Long = 0L)

/** Estado que consumen las pantallas. Nunca contiene las claves. */
data class CatalogUiState(
    val tmdbConfigured: Boolean = false,
    val rpdbConfigured: Boolean = false,
    val loading: Boolean = false,
    val rows: List<CatalogRow> = emptyList(),
    val imageConfig: ImageConfig? = null,
    val problem: CatalogProblem? = null,
    val problemDetail: String? = null,
    val updatedAtMillis: Long? = null,
    val rpdbActive: Boolean = false,
    val rpdbNote: String? = null,
    val tmdbTest: TestResult? = null,
    val rpdbTest: TestResult? = null,
    val storageNote: String = "",
    val testing: Boolean = false,
) {
    val hasContent: Boolean get() = rows.any { it.items.isNotEmpty() }
}
