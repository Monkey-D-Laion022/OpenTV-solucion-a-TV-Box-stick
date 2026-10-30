package com.opentv.app.ui.shell

import com.opentv.app.navigation.PendingFeature
import com.opentv.app.navigation.Routes

/**
 * Lógica PURA de la navegación del rediseño: las cinco secciones de la barra superior y el catálogo de destinos.
 * El catálogo reúne los accesos que ya existían (con sus mismos destinos) para que Inicio, Conexiones y Ajustes los
 * muestren sin duplicarlos ni perderlos. Nada de acá toca resolución, densidad ni rotación.
 */
enum class TopSection(val route: String, val label: String) {
    HOME(Routes.HOME, "Inicio"),
    APPS(Routes.APPS, "Aplicaciones"),
    MEDIA(Routes.MEDIA, "Multimedia"),
    CONNECTIONS(Routes.CONNECTIONS, "Conexiones"),
    SETTINGS(Routes.SETTINGS, "Ajustes"),
    ;

    companion object {
        fun forRoute(route: String?): TopSection? = values().firstOrNull { it.route == route }
    }
}

/** Acción especial (no es una ruta): hoy solo abrir el panel de transmisión del sistema. */
enum class EntryAction { NATIVE_CAST }

/** Un acceso: o navega a [route], o ejecuta [action]. [hero] es el texto que el inicio muestra al enfocarlo. */
data class NavEntry(
    val id: String,
    val title: String,
    val subtitle: String,
    val hero: String,
    val route: String? = null,
    val action: EntryAction? = null,
    val badge: String? = null,
) {
    init {
        require((route != null) != (action != null)) { "Un acceso tiene ruta o acción, no ambas ni ninguna: $id" }
    }
}

object NavCatalog {
    /** Duplicar la pantalla del teléfono en el TV. */
    val mirroring: List<NavEntry> = listOf(
        NavEntry(
            "mirror", "Espejo nativo 16:9", "Miracast del sistema + Shizuku",
            "Abre la pantalla donde vos forzás el 16:9 y abrís la transmisión del sistema. Acá no se cambia nada.",
            route = Routes.MIRROR,
        ),
        NavEntry(
            "native", "Emisión del sistema", "Cast / Smart View / Miracast",
            "Abre el panel de transmisión de Android, si tu teléfono lo tiene. No cambia la resolución.",
            action = EntryAction.NATIVE_CAST,
        ),
        NavEntry(
            "browser", "Espejo por navegador", "Sin Miracast: el TV abre una web",
            "El TV abre una dirección corta en su navegador y ve la pantalla del teléfono (imagen sin sonido).",
            route = Routes.BROWSER,
        ),
    )

    /** Enviar contenido al TV (no es duplicar la pantalla). */
    val playback: List<NavEntry> = listOf(
        NavEntry(
            "hub", "Transmitir a TV", "Elegí un Smart TV de tu red (DLNA)",
            "Busca los TV de tu red y les envía una dirección o la pantalla del teléfono por DLNA.",
            route = Routes.CONNECT,
        ),
        NavEntry(
            "own", "Proyectar OpenTV en el TV", "Pantalla propia 16:9 por DLNA",
            "Dibuja la interfaz de OpenTV en una pantalla virtual 16:9 y la envía al TV por DLNA.",
            route = Routes.REMOTE,
        ),
    )

    /** Pruebas de conexión: descubrir un dispositivo no es conectarse ni transmitir. */
    /** Canal de televisión en vivo generado por el teléfono (M3U + HLS). */
    val iptv: List<NavEntry> = listOf(
        NavEntry(
            "iptv", "Emisión IPTV", "El teléfono como canal en vivo (M3U + HLS)",
            "Publica la pantalla del teléfono como un canal en vivo y una lista M3U para cargar en una app IPTV del televisor. Sin probar todavía con tu LG ni con SmartOne.",
            route = Routes.IPTV, badge = "Nuevo",
        ),
    )

    /** Protocolo propio de OpenTV y sus receptores: definido y probado, sin emisor/receptor Android todavía. */
    val opentvLink: List<NavEntry> = listOf(
        NavEntry(
            "link", "Transmisión OpenTV", "Protocolo propio cifrado · pendiente en Android",
            "El protocolo OpenTV Link está documentado y probado con un receptor de referencia. El emisor y el receptor dentro de la app todavía no existen.",
            route = Routes.pending(PendingFeature.OPENTV_LINK), badge = "Pendiente",
        ),
        NavEntry(
            "receivers", "Receptores OpenTV", "Ninguno disponible todavía",
            "Un receptor OpenTV para Android TV aún no está desarrollado. Hay un receptor de referencia para PC en tools/receiver-py.",
            route = Routes.pending(PendingFeature.OPENTV_LINK), badge = "Pendiente",
        ),
    )

    val connectionDiagnostics: List<NavEntry> = listOf(
        NavEntry(
            "p2p", "Descubrimiento Wi-Fi Direct", "Etapa A · ¿se ve el televisor? (no conecta)",
            "Busca dispositivos Wi-Fi Direct cercanos y muestra lo que Android informa. No conecta, no transmite y no cambia ningún ajuste. Solo arranca cuando pulsás Buscar.",
            route = Routes.P2P_DISCOVERY,
        ),
        NavEntry(
            "wfd", "Diagnóstico Wi-Fi Display (WFD)", "Etapa B · negociación con confirmación tuya",
            "Intenta formar un grupo con el dispositivo que elijas y confirmes, y lee lo que el televisor anuncia. No transmite video ni prueba los 60 FPS.",
            route = Routes.WFD_DIAGNOSTIC,
        ),
    )

    /** Diagnósticos de Ajustes: los tres accesos movidos desde el inicio, más los que ya estaban. */
    val diagnostics: List<NavEntry> = listOf(
        NavEntry(
            "bench", "Medición de captura", "Prueba local de FPS (no transmite)",
            "Mide los FPS de captura y codificación de este teléfono. Solo arranca cuando la iniciás y aceptás el permiso; no transmite ni cambia la resolución.",
            route = Routes.BENCH,
        ),
        connectionDiagnostics[0],
        connectionDiagnostics[1],
        NavEntry(
            "shizuku", "Asistente de Shizuku", "Estado y pasos guiados",
            "Comprueba si Shizuku está lista y te guía paso a paso. No cambia la resolución.",
            route = Routes.SHIZUKU_GUIDE,
        ),
        NavEntry(
            "device", "Diagnóstico del dispositivo", "Pantalla, memoria y formato",
            "Datos reales de tu pantalla, memoria y formato.",
            route = Routes.DEVICE,
        ),
    )

    /** Lo que HOY se puede reproducir de verdad (distinto del catálogo informativo). */
    val sources: List<NavEntry> = listOf(
        NavEntry(
            "source-link", "Enlace directo", "Reproducir la dirección de un video",
            "Pegás la dirección (http o https) de un video y se reproduce en OpenTV; si elegiste un TV como salida, se envía allí.",
            route = Routes.PLAYER,
        ),
        NavEntry(
            "source-addon", "Add-on de catálogo", "Sin fuente configurada",
            "Todavía no hay un add-on conectado: los títulos del catálogo son solo información y no se pueden reproducir desde OpenTV.",
            route = Routes.pending(PendingFeature.STREAM_ADDON), badge = "Pendiente",
        ),
    )

    /** Otros accesos del inicio. */
    val explore: List<NavEntry> = listOf(
        NavEntry("all-apps", "Todas las apps", "Lista completa de tu teléfono", "La lista completa de tu teléfono; desde ahí podés fijar favoritas en la fila principal.", route = Routes.APPS),
        NavEntry("media", "Multimedia", "Enlace directo y fuentes", "Reproducí un enlace directo y mirá qué fuentes de contenido existen y cuáles están pendientes.", route = Routes.MEDIA),
        NavEntry(
            "local-media", "Multimedia local", "Pendiente (no implementado)", "Todavía no existe: la pantalla explica qué falta.",
            route = Routes.pending(PendingFeature.LOCAL_MEDIA), badge = "Pendiente",
        ),
    )

    /** Fila «Transmitir» del inicio: mismas cinco tarjetas que antes, en el mismo orden. */
    val homeTransmit: List<NavEntry> = listOf(playback[0], mirroring[0], mirroring[2], playback[1], mirroring[1])

    /** Todos los accesos que existían antes del rediseño y que NO pueden perderse. */
    val everyPreservedRoute: Set<String> = setOf(
        Routes.CONNECT, Routes.MIRROR, Routes.BROWSER, Routes.REMOTE, Routes.APPS, Routes.BENCH,
        Routes.SHIZUKU_GUIDE, Routes.DEVICE, Routes.P2P_DISCOVERY, Routes.TOOLS, Routes.PLAYER, Routes.IPTV,
    )
}

/** Categorías del panel izquierdo de Ajustes. */
enum class SettingsCategory(val id: String, val title: String) {
    NETWORK("network", "Red e Internet"),
    DISPLAY("display", "Pantalla"),
    SOUND("sound", "Sonido"),
    APPS("apps", "Aplicaciones"),
    CATALOG("catalog", "Catálogo"),
    SYSTEM("system", "Sistema e información"),
    DIAGNOSTICS("diagnostics", "Diagnósticos"),
    ;

    companion object {
        fun fromId(id: String?): SettingsCategory = values().firstOrNull { it.id == id } ?: NETWORK
    }
}
