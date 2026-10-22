package com.opentv.app.connect

enum class ConnectionMethod(val title: String) {
    MIRACAST("Transmisión de pantalla (Miracast)"),
    GOOGLE_CAST("Google Cast"),
    DLNA("DLNA: enviar un enlace al TV"),
    WIFI_DIRECT("Wi‑Fi Direct"),
}

/** Estado honesto de cada método. «Conectado» solo se usa cuando hay una conexión comprobada. */
enum class MethodState(val label: String) {
    CONNECTED("Conectado"),
    AVAILABLE("Disponible"),
    UNVERIFIABLE("Sin verificar"),
    DETECTED_ONLY("Solo detectado"),
    UNAVAILABLE("No disponible"),
}

data class MethodStatus(
    val method: ConnectionMethod,
    val state: MethodState,
    val summary: String,
)

/** Pantalla secundaria que Android informa como «de presentación» (HDMI o inalámbrica). */
data class ExternalDisplay(val name: String)

/** Lo que se pudo leer del equipo, sin interpretar. Lo llena `CapabilityDetector`. */
data class RawCapabilities(
    val hasCastSettingsScreen: Boolean,
    val hasWifiDirectFeature: Boolean,
    /** Hay una red Wi‑Fi o Ethernet activa (necesaria para Cast y DLNA). */
    val onLocalNetwork: Boolean,
    val playServicesAvailable: Boolean,
    val externalDisplay: ExternalDisplay?,
)

/**
 * Convierte las lecturas del equipo en estados y mensajes. Lógica pura: se prueba sin Android.
 * Regla de oro: nunca afirmar más de lo que Android permite comprobar.
 */
object CapabilityEvaluator {

    fun evaluate(raw: RawCapabilities): List<MethodStatus> = listOf(
        miracast(raw),
        googleCast(raw),
        dlna(raw),
        wifiDirect(raw),
    )

    private fun miracast(raw: RawCapabilities): MethodStatus {
        val display = raw.externalDisplay
        return when {
            display != null -> MethodStatus(
                ConnectionMethod.MIRACAST,
                MethodState.CONNECTED,
                "Hay una pantalla externa conectada: ${display.name}. Android no indica si es HDMI " +
                    "o inalámbrica.",
            )
            raw.hasCastSettingsScreen -> MethodStatus(
                ConnectionMethod.MIRACAST,
                MethodState.UNVERIFIABLE,
                "Tu equipo tiene transmisión de pantalla en los ajustes. Android no permite a una " +
                    "app comprobar si el hardware soporta Miracast ni listar los TV cercanos: " +
                    "abrí los ajustes del sistema y probá desde ahí.",
            )
            else -> MethodStatus(
                ConnectionMethod.MIRACAST,
                MethodState.UNAVAILABLE,
                "Este equipo no ofrece una pantalla de transmisión en sus ajustes, así que OpenTV " +
                    "no puede iniciar la duplicación de pantalla. Usá DLNA o Google Cast.",
            )
        }
    }

    private fun googleCast(raw: RawCapabilities): MethodStatus = when {
        !raw.playServicesAvailable -> MethodStatus(
            ConnectionMethod.GOOGLE_CAST,
            MethodState.UNAVAILABLE,
            "Google Cast necesita Google Play Services y no está disponible en este equipo.",
        )
        !raw.onLocalNetwork -> MethodStatus(
            ConnectionMethod.GOOGLE_CAST,
            MethodState.UNAVAILABLE,
            "Conectá el teléfono a la misma red Wi‑Fi que el TV para buscar dispositivos Cast.",
        )
        else -> MethodStatus(
            ConnectionMethod.GOOGLE_CAST,
            MethodState.AVAILABLE,
            "Se pueden detectar dispositivos Cast. Cast envía contenido, no duplica la pantalla. " +
                "Tocá el dispositivo en «Conectar TV» para enviarle un enlace.",
        )
    }

    private fun dlna(raw: RawCapabilities): MethodStatus =
        if (!raw.onLocalNetwork) {
            MethodStatus(
                ConnectionMethod.DLNA,
                MethodState.UNAVAILABLE,
                "Conectá el teléfono a la misma red Wi‑Fi o Ethernet que el TV para buscarlo.",
            )
        } else {
            MethodStatus(
                ConnectionMethod.DLNA,
                MethodState.AVAILABLE,
                "Podés buscar TV compatibles y enviarles un enlace de video directo. DLNA envía " +
                    "contenido, no duplica la pantalla.",
            )
        }

    private fun wifiDirect(raw: RawCapabilities): MethodStatus =
        if (raw.hasWifiDirectFeature) {
            MethodStatus(
                ConnectionMethod.WIFI_DIRECT,
                MethodState.DETECTED_ONLY,
                "El hardware lo soporta. Es solo un canal de conexión entre dispositivos y no " +
                    "transmite video por sí solo. OpenTV todavía no lo usa.",
            )
        } else {
            MethodStatus(
                ConnectionMethod.WIFI_DIRECT,
                MethodState.UNAVAILABLE,
                "Este equipo no informa soporte de Wi‑Fi Direct.",
            )
        }
}
