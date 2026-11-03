package com.opentv.app.iptv

/**
 * Combinaciones de calidad del canal IPTV. Son valores PEDIDOS al codificador: lo que el teléfono entrega de verdad
 * depende de su hardware y de lo que cambie en pantalla, y lo que el televisor acepte no está comprobado. Lógica pura.
 */
enum class IptvPreset(
    val label: String,
    val shortLabel: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val bitrateBps: Int,
    val segmentMs: Long,
) {
    SAFE("720p · 15 fps · 2 Mbps · segmentos de 4 s (más estable)", "720p · 15 fps", 1280, 720, 15, 2_000_000, 4_000),
    BALANCED("720p · 30 fps · 3 Mbps · segmentos de 2 s", "720p · 30 fps", 1280, 720, 30, 3_000_000, 2_000),
    FULL_HD("1080p · 30 fps · 6 Mbps · segmentos de 2 s (exige más)", "1080p · 30 fps", 1920, 1080, 30, 6_000_000, 2_000),
    HD_60("720p · 60 fps · 6 Mbps · segmentos de 4 s", "720p · 60 fps", 1280, 720, 60, 6_000_000, 4_000),
    FULL_HD_60("1080p · 60 fps · 10 Mbps · segmentos de 4 s (exige mucho)", "1080p · 60 fps", 1920, 1080, 60, 10_000_000, 4_000),
    COMPAT_480("480p · 30 fps · 1,5 Mbps · segmentos de 4 s (equipos viejos)", "480p · 30 fps", 854, 480, 30, 1_500_000, 4_000),
    COMPAT_360("360p · 30 fps · 0,8 Mbps · segmentos de 4 s (equipos muy limitados)", "360p · 30 fps", 640, 360, 30, 800_000, 4_000);

    /**
     * Retraso teórico de HLS: los reproductores empiezan unos 3 segmentos antes del final de la playlist.
     * No es una medición; cada app IPTV puede guardar más búfer.
     */
    val theoreticalLatencySeconds: Int get() = (segmentMs * 3 / 1000).toInt()

    fun next(): IptvPreset = values()[(ordinal + 1) % values().size]

    /** Los perfiles para equipos viejos piden H.264 Baseline (lo más abierto); los demás, Main. */
    val baselineProfile: Boolean get() = this == COMPAT_480 || this == COMPAT_360

    fun hlsConfig(): HlsConfig = HlsConfig(targetSegmentMs = segmentMs, windowSegments = 4)
}

/** Cómo se arma la dirección: corta (fácil de teclear con el control del TV) o con código aleatorio (más privada). */
enum class IptvAccess(val label: String) {
    CODE("con código aleatorio"),
    SHORT("corta (/tv)");

    fun next(): IptvAccess = values()[(ordinal + 1) % values().size]

    /** Ruta fija de la variante corta; `null` = generar un código. */
    val fixedToken: String? get() = if (this == SHORT) "tv" else null
}

/** Qué se está emitiendo de audio. Nunca se informa «con sonido» sin haber medido muestras no nulas. */
enum class IptvAudio(val label: String) {
    SILENT_TRACK("pista en silencio de respaldo (no se captura el audio del teléfono)"),
    NO_PERMISSION("permiso de audio no concedido: pista en silencio"),
    UNAVAILABLE("captura no disponible en este teléfono: pista en silencio"),
    CAPTURING_ZEROS("captura activa pero solo llegan ceros: nada suena o la app de origen bloquea la captura"),
    CAPTURING_NO_SOUND("capturando, sin sonido en los últimos segundos"),
    CAPTURING_SOUND("capturando y se detecta sonido"),
}

/** Puertos que se prueban en orden para que la dirección sea siempre la misma y fácil de teclear. */
object IptvPorts {
    val PREFERRED = listOf(8090, 8091, 8092)
}
