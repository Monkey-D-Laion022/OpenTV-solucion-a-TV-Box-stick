package com.opentv.app.stream

/**
 * Cómo se presenta el flujo al TV. Cada TV es distinto, así que hay varias combinaciones para probar
 * desde la app: tipo MIME, perfil DLNA, si se envían metadatos y la resolución. Lógica pura.
 */
enum class StreamProfile(
    val label: String,
    val width: Int,
    val height: Int,
    val mime: String,
    /** Perfil DLNA (`DLNA.ORG_PN`), o `null` para no declarar ninguno. */
    val pn: String?,
    val sendMetadata: Boolean = true,
) {
    AVC_TS("H.264 en TS con perfil DLNA (recomendado)", 1920, 1080, "video/mpeg", "AVC_TS_HD_NA_ISO"),
    GENERIC("TS genérico, sin perfil DLNA", 1920, 1080, "video/mpeg", null),
    DLNA_TTS("TS con tipo video/vnd.dlna.mpeg-tts", 1920, 1080, "video/vnd.dlna.mpeg-tts", "AVC_TS_HD_NA_ISO"),
    NO_META("Sin metadatos DIDL", 1920, 1080, "video/mpeg", null, sendMetadata = false),
    HD720("720p genérico", 1280, 720, "video/mpeg", null);

    /** Valor de `DLNA.ORG_FLAGS`: streaming en vivo, sin saltos ni pausa. */
    private fun features(pn: String) =
        "DLNA.ORG_PN=$pn;DLNA.ORG_OP=00;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"

    /** Cuarto campo de `protocolInfo` y base del DIDL-Lite. */
    fun protocolInfo(): String = "http-get:*:$mime:" + (pn?.let { features(it) } ?: "*")

    /** Cabecera HTTP `contentFeatures.dlna.org`, o `null` si el perfil no declara ninguna. */
    fun contentFeatures(): String? = pn?.let { features(it) }

    fun next(): StreamProfile = values()[(ordinal + 1) % values().size]
}
