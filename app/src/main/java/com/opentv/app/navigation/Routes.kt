package com.opentv.app.navigation

/** Rutas de navegación. Sin dependencias de Android para poder probarlas con JUnit simple. */
object Routes {
    const val HOME = "home"
    const val BENCH = "bench"
    const val P2P_DISCOVERY = "p2p-discovery"
    const val WFD_DIAGNOSTIC = "wfd-diagnostic"
    const val MEDIA = "media"
    const val CONNECTIONS = "connections"
    const val SETTINGS = "settings"
    const val SETTINGS_CATALOG = "settings-catalog"
    const val DEVICE = "device"
    const val APPS = "apps"
    const val CONNECT = "connect"
    const val PLAYER = "player"
    const val TOOLS = "tools"
    const val REMOTE = "remote"
    const val MIRROR = "mirror"
    const val BROWSER = "browser"
    const val IPTV = "iptv"
    const val OPENTV_PROTOCOL = "opentv-protocol"
    const val SHIZUKU_GUIDE = "shizuku-guide"

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
    LOCAL_MEDIA(
        id = "local_media",
        title = "Multimedia local",
        phase = 4,
        plan = "Explorador de videos, fotos y música usando el selector de documentos de Android.",
    ),
    OPENTV_LINK(
        id = "opentv_link",
        title = "Transmisión OpenTV (protocolo propio)",
        phase = 6,
        plan = "OpenTV Link v1 ya está definido, documentado y probado entre dos implementaciones (emisor Kotlin y receptor de referencia en Python), " +
            "pero todavía no hay emisor ni receptor dentro de la app Android. Faltan: el servicio emisor (reutiliza la captura del canal IPTV), el " +
            "receptor para Android TV y la prueba con dos equipos reales. Mientras tanto, para ver el canal en una PC podés usar el receptor de referencia (carpeta tools/receiver-py).",
    ),
    STREAM_ADDON(
        id = "stream_addon",
        title = "Add-on de catálogo",
        phase = 6,
        plan = "Conectar un add-on de catálogo de streaming para poder reproducir los títulos del catálogo. Hoy el catálogo (TMDB) es solo informativo y no hay ninguna fuente configurada.",
    );

    companion object {
        fun fromId(id: String?): PendingFeature? = values().firstOrNull { it.id == id }
    }
}
