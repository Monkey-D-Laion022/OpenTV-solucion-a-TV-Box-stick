package com.opentv.app.connect

/** Modo de transmisión que se ofrece para un dispositivo de la red. */
enum class ConnectMode(val title: String, val detail: String) {
    DLNA_MIRROR(
        "Espejo de pantalla + sonido (DLNA)",
        "Duplica la pantalla del teléfono; con demora de unos segundos (búfer del TV).",
    ),
    DLNA_PROJECT(
        "Proyectar la interfaz de OpenTV (DLNA)",
        "Muestra el inicio de OpenTV en 16:9 y lo manejás desde el teléfono.",
    ),
    CAST_OUTPUT(
        "Enviar videos con Google Cast",
        "Elegí este TV como salida del reproductor. No duplica la pantalla: para eso usá «Emisión nativa».",
    ),
}

/** Qué ofrecer según lo que el dispositivo realmente es. Lógica pura. */
object ConnectModes {
    fun forTarget(target: TvTarget): List<ConnectMode> = when (target) {
        is TvTarget.DlnaTarget -> listOf(ConnectMode.DLNA_MIRROR, ConnectMode.DLNA_PROJECT)
        is TvTarget.CastTarget -> listOf(ConnectMode.CAST_OUTPUT)
    }

    /** Una línea que describe al dispositivo: protocolo y, si se conoce, fabricante y modelo. */
    fun summary(target: TvTarget): String = when (target) {
        is TvTarget.CastTarget -> "Google Cast"
        is TvTarget.DlnaTarget -> {
            val extra = listOf(target.device.manufacturer, target.device.modelName).filter { it.isNotBlank() }.joinToString(" ")
            if (extra.isBlank()) "DLNA" else "DLNA · $extra"
        }
    }

    /** Aviso sobre el sonido del espejo según la versión de Android, o `null` si no hay nada que avisar. */
    fun audioNote(sdkInt: Int): String? =
        if (sdkInt >= 29) null else "Este Android (anterior al 10) no puede capturar el sonido: el espejo saldría sin audio."

    /** Opciones para teléfonos o TV sin emisión nativa, en el orden en que conviene probarlas. */
    val WITHOUT_NATIVE_CAST: List<String> = listOf(
        "Espejo DLNA con sonido: si tu TV aparece arriba como DLNA.",
        "Espejo por navegador: si el TV tiene navegador web.",
        "Canal local: si el TV (o un TV Box) tiene VLC o una app de IPTV, abre la dirección o la lista M3U que muestra OpenTV; lleva video y sonido.",
        "Receptor externo (Chromecast, Fire TV Stick o un adaptador Miracast): enchufado al HDMI del TV; es la opción más fluida si nada de lo anterior sirve.",
        "Cable USB‑C a HDMI: solo en teléfonos con salida de video por USB‑C (la mayoría de gama baja no la trae).",
    )
}
