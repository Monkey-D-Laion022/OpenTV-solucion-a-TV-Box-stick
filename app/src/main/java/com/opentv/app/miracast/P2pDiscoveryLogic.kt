package com.opentv.app.miracast

/**
 * Etapa A de Miracast: SOLO descubrimiento Wi-Fi Direct. Lógica PURA (sin Android) de la prueba.
 *
 * Alcance cerrado: esta etapa busca dispositivos cercanos y muestra lo que la API pública de Android permite leer.
 * No conecta, no forma grupos, no abre sockets, no negocia RTSP y no toca resolución, densidad ni rotación.
 */
object P2pPolicy {
    /** Permiso en tiempo de ejecución de Android 13+ (se declara con neverForLocation: no se pide ubicación). */
    const val NEARBY_PERMISSION = "android.permission.NEARBY_WIFI_DEVICES"

    /** Primera versión de Android (API 33) en la que el descubrimiento NO necesita el permiso de ubicación. */
    const val FIRST_SDK_WITHOUT_LOCATION = 33

    /** Duración máxima de la búsqueda; al cumplirse se detiene sola. */
    const val DISCOVERY_SECONDS = 30

    /**
     * En Android 12 o anterior la guía oficial exige el permiso de ubicación para Wi-Fi Direct. OpenTV NO lo declara
     * (requiere aprobación expresa), así que en esas versiones la prueba no se ejecuta.
     */
    fun canRun(sdkInt: Int): Boolean = sdkInt >= FIRST_SDK_WITHOUT_LOCATION
}

enum class P2pPhase { IDLE, BLOCKED_OLD_ANDROID, UNSUPPORTED, NEED_PERMISSION, PERMISSION_DENIED, SEARCHING, DONE, FAILED }

/** Lo que la API pública informa de un dispositivo. La dirección MAC no se muestra ni se guarda. */
data class P2pPeer(
    val name: String,
    val primaryType: String?,
    val secondaryType: String?,
    val statusCode: Int,
    val isGroupOwner: Boolean,
    val serviceDiscoveryCapable: Boolean,
    /** `true` si estaba en la última lista entregada por Android; `false` si se vio antes y ya no aparece. */
    val inLatestScan: Boolean,
)

data class P2pUi(
    val phase: P2pPhase = P2pPhase.IDLE,
    val peers: List<P2pPeer> = emptyList(),
    val log: List<String> = emptyList(),
    /** Estado de Wi-Fi Direct informado por el sistema (`null` = todavía no informó). */
    val p2pEnabled: Boolean? = null,
    /** Interruptor de ubicación del sistema (`null` = no consultado). */
    val locationOn: Boolean? = null,
    val failureCode: Int? = null,
)

object P2pText {
    const val NA = "no disponible"

    /** Valores de WifiP2pDevice (CONNECTED=0, INVITED=1, FAILED=2, AVAILABLE=3, UNAVAILABLE=4). */
    fun status(code: Int): String = when (code) {
        0 -> "conectado (0)"
        1 -> "invitado (1)"
        2 -> "falló (2)"
        3 -> "disponible (3)"
        4 -> "no disponible (4)"
        else -> "valor desconocido ($code)"
    }

    /** Códigos de WifiP2pManager.ActionListener.onFailure (ERROR=0, P2P_UNSUPPORTED=1, BUSY=2, NO_SERVICE_REQUESTS=3). */
    fun failure(code: Int): String = when (code) {
        0 -> "error interno de Android (código 0)"
        1 -> "este equipo no admite Wi-Fi Direct (código 1)"
        2 -> "el sistema está ocupado, por ejemplo con otra sesión Wi-Fi Direct o de Pantalla inalámbrica (código 2)"
        3 -> "no hay solicitudes de servicio (código 3)"
        else -> "código de error desconocido ($code)"
    }

    private val LG_NAME = Regex("(?i)\\bLG\\b|lgtv|webos|netcast")

    /** Solo por el nombre: el televisor puede anunciarse con otro. Nunca se presenta como confirmación. */
    fun possibleLg(name: String): Boolean = LG_NAME.containsMatchIn(name)

    fun peerLines(p: P2pPeer): List<String> = listOf(
        (p.name.ifBlank { "(sin nombre)" }) + if (possibleLg(p.name)) "  ← el nombre sugiere LG (no confirmado)" else "",
        "Tipo declarado (primario): ${p.primaryType?.ifBlank { null } ?: NA}",
        "Tipo declarado (secundario): ${p.secondaryType?.ifBlank { null } ?: NA}",
        "Estado según Android: ${status(p.statusCode)}",
        "Propietario de grupo: ${if (p.isGroupOwner) "sí" else "no"} · descubrimiento de servicios: ${if (p.serviceDiscoveryCapable) "sí" else "no"}",
        if (p.inLatestScan) "En la última lista entregada" else "Se vio antes y ya no aparece",
    )

    /** Qué quedó comprobado y qué no. Un resultado negativo nunca se traduce en una conclusión sobre los 60 FPS. */
    fun conclusion(ui: P2pUi): String = when (ui.phase) {
        P2pPhase.IDLE -> "Todavía no se buscó nada."
        P2pPhase.BLOCKED_OLD_ANDROID ->
            "No ejecutada: en Android 12 o anterior, Wi-Fi Direct exige el permiso de ubicación y OpenTV no lo declara sin tu aprobación. " +
                "Resultado: inconcluso."
        P2pPhase.UNSUPPORTED -> "No ejecutada: este equipo informa que no tiene Wi-Fi Direct. Resultado: inconcluso para este teléfono."
        P2pPhase.NEED_PERMISSION -> "Falta el permiso de dispositivos Wi-Fi cercanos. Sin él Android no deja buscar."
        P2pPhase.PERMISSION_DENIED -> "Permiso denegado: la búsqueda no se ejecutó. Resultado: inconcluso."
        P2pPhase.SEARCHING -> "Buscando… los dispositivos aparecen a medida que Android los informa."
        P2pPhase.FAILED ->
            "Android rechazó la búsqueda" + (ui.failureCode?.let { ": ${failure(it)}" } ?: "") +
                ". Resultado: inconcluso. No dice nada sobre el televisor ni sobre los 60 FPS."
        P2pPhase.DONE -> doneText(ui.peers)
    }

    private fun doneText(peers: List<P2pPeer>): String {
        val lg = peers.count { possibleLg(it.name) }
        val base = when {
            peers.isEmpty() ->
                "No apareció ningún dispositivo en la búsqueda. Resultado: INCONCLUSO. No prueba que el televisor no admita Miracast ni 60 FPS: " +
                    "pudo estar apagado, sin Miracast activo, fuera de alcance, con la ubicación del teléfono desactivada o filtrado por Android."
            lg > 0 ->
                "Se vio $lg dispositivo(s) cuyo nombre sugiere LG. Confirmá en el televisor que es el tuyo. " +
                    "Esto solo muestra que una app normal puede VER ese dispositivo."
            else ->
                "Se vieron ${peers.size} dispositivo(s), ninguno con nombre de LG (el televisor puede anunciarse con otro nombre). " +
                    "Respecto del LG: INCONCLUSO."
        }
        return base + " Esta etapa NO comprueba que se pueda conectar, que se pueda leer su lista de formatos ni que admita 1080p60. " +
            "OpenTV no intenta leer la información Wi-Fi Display (WFD) del dispositivo: no usa APIs ocultas ni reflexión."
    }
}
