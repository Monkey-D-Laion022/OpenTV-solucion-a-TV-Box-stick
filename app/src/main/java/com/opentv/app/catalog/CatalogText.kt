package com.opentv.app.catalog

/** Textos del catálogo. PURO. */
object CatalogText {
    const val ATTRIBUTION =
        "Este producto usa la API de TMDB pero no está avalado ni certificado por TMDB. Metadatos e imágenes: TMDB."
    const val RPDB_NOTE = "Pósteres con calificaciones: RPDB (si hay clave y responde); si no, los de TMDB."
    const val INFO_ONLY = "Solo información"
    const val NOT_PLAYABLE =
        "Información del catálogo. OpenTV todavía no tiene una fuente de reproducción configurada para este título."

    fun httpError(code: Int): String = when {
        code == 401 -> "TMDB rechazó la clave (401). Revisá que sea la clave v3 completa o el token v4 completo."
        code == 403 -> "TMDB denegó el acceso (403)."
        code == 404 -> "TMDB no encontró el recurso (404)."
        code == 429 -> "TMDB pidió esperar (429): demasiadas peticiones. Probá de nuevo en unos minutos."
        code in 500..599 -> "El servidor de TMDB tuvo un problema ($code). Probá más tarde."
        else -> "TMDB respondió con el código $code."
    }

    fun problem(p: CatalogProblem, detail: String?): String = when (p) {
        CatalogProblem.NO_CREDENTIAL -> "Catálogo sin configurar."
        CatalogProblem.REJECTED -> detail ?: "TMDB rechazó la clave."
        CatalogProblem.RATE_LIMITED -> detail ?: "TMDB pidió esperar."
        CatalogProblem.NETWORK -> "No se pudo conectar con TMDB. Revisá la conexión a Internet."
        CatalogProblem.SERVER -> detail ?: "TMDB tuvo un problema."
        CatalogProblem.PARSE -> "TMDB respondió algo que OpenTV no pudo leer."
    }

    fun ratingLabel(r: Double?): String? = r?.let { String.format(java.util.Locale.US, "★ %.1f", it) }

    fun metaLine(item: CatalogItem): String =
        listOfNotNull(item.kind.label, item.year, ratingLabel(item.rating)).joinToString(" · ")

    fun overviewOrNote(item: CatalogItem): String =
        item.overview.ifBlank { "TMDB no tiene sinopsis en español para este título." }
}
