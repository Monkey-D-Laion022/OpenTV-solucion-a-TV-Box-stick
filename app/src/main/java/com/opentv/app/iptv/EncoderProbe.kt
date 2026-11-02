package com.opentv.app.iptv

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build

/**
 * Consulta los codificadores H.264 REALES del teléfono. Lo que declaran es una capacidad, no un rendimiento
 * sostenido: la medición real la hace [EncoderBenchmark]. Solo lee información del sistema (sin permisos).
 */
object EncoderProbe {
    class Encoder(val info: MediaCodecInfo, val hardware: Boolean)

    fun encoders(): List<Encoder> = try {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filter { it.isEncoder && it.supportedTypes.any { t -> t.equals(MediaFormat.MIMETYPE_VIDEO_AVC, true) } }
            .map { Encoder(it, isHardware(it)) }
    } catch (e: RuntimeException) {
        emptyList()
    }

    fun isHardware(info: MediaCodecInfo): Boolean =
        if (Build.VERSION.SDK_INT >= 29) info.isHardwareAccelerated
        else !(info.name.startsWith("OMX.google.") || info.name.startsWith("c2.android.") || info.name.contains(".sw.", true))

    private fun videoCaps(info: MediaCodecInfo): MediaCodecInfo.VideoCapabilities? = try {
        info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC).videoCapabilities
    } catch (e: RuntimeException) {
        null
    }

    private fun declares(e: Encoder, w: Int, h: Int, fps: Int): Boolean =
        videoCaps(e.info)?.let { runCatching { it.areSizeAndRateSupported(w, h, fps.toDouble()) }.getOrDefault(false) } == true

    fun supports(w: Int, h: Int, fps: Int): Boolean = encoders().any { declares(it, w, h, fps) }

    /** Nombre del mejor codificador que declara el perfil: hardware primero. `null` si ninguno (se usa el predeterminado). */
    fun pick(w: Int, h: Int, fps: Int): String? {
        val ok = encoders().filter { declares(it, w, h, fps) }
        return (ok.firstOrNull { it.hardware } ?: ok.firstOrNull())?.info?.name
    }

    /** Líneas legibles para la pantalla de diagnóstico: una por codificador, con lo que declara. */
    fun report(): List<String> {
        val list = encoders()
        if (list.isEmpty()) return listOf("No se pudo leer la lista de codificadores H.264.")
        return list.map { e ->
            val caps = videoCaps(e.info)
            val maxLevel = runCatching {
                e.info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC).profileLevels
                    .filter { it.profile == MediaCodecInfo.CodecProfileLevel.AVCProfileMain }
                    .maxOfOrNull { it.level }
            }.getOrNull()
            val fhd60 = if (caps != null) declares(e, 1920, 1080, 60) else false
            val hd60 = if (caps != null) declares(e, 1280, 720, 60) else false
            val achievable = caps?.let { c ->
                runCatching { if (Build.VERSION.SDK_INT >= 23) c.getAchievableFrameRatesFor(1920, 1080)?.upper else null }.getOrNull()
            }
            val bitrate = caps?.bitrateRange?.upper
            e.info.name + (if (e.hardware) " [hardware]" else " [software]") +
                " · nivel Main máx. " + (maxLevel?.let { AvcLevels.name(it) } ?: "?") +
                " · declara 1080p60: " + (if (fhd60) "sí" else "no") + " · 720p60: " + (if (hd60) "sí" else "no") +
                (achievable?.let { " · 1080p alcanzable ≈ ${"%.0f".format(it)} fps" } ?: "") +
                (bitrate?.let { " · bitrate máx. ${it / 1_000_000} Mbit/s" } ?: "")
        }
    }
}
