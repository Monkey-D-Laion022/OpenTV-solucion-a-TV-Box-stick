package com.opentv.app.navigation

/** Rutas de navegación. Sin dependencias de Android para poder probarlas con JUnit simple. */
object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val DEVICE = "device"
    const val APPS = "apps"
    const val CONNECT = "connect"

    const val PENDING_ARG = "id"
    const val PENDING_PATTERN = "pending/{$PENDING_ARG}"

    fun pending(feature: PendingFeature): String = "pending/${feature.id}"
}

/**
 * Funciones del plan que todavía NO existen. La pantalla de inicio las marca como pendientes y su
 * destino lo dice con claridad; no simulan funcionar.
 */
enum class PendingFeature(
    val id: String,
    val title: String,
    val phase: Int,
    val plan: String,
) {
    PLAYER(
        id = "player",
        title = "Reproductor",
        phase = 4,
        plan = "Reproductor con Media3 y ExoPlayer, selector de documentos de Android y modos de " +
            "visualización que conservan la proporción original.",
    ),
    LOCAL_MEDIA(
        id = "local_media",
        title = "Multimedia local",
        phase = 4,
        plan = "Explorador de videos, fotos y música usando el selector de documentos de Android.",
    );

    companion object {
        fun fromId(id: String?): PendingFeature? = values().firstOrNull { it.id == id }
    }
}
