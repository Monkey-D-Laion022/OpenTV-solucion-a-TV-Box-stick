package com.opentv.app.bench

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build

/**
 * Consulta a Android qué codificador H.264 usaría para una configuración. Solo LEE las capacidades: no abre ni
 * configura ningún codificador. Si Android no lo admite devuelve `null` (no se ofrece ni se fuerza).
 */
class AndroidEncoderLookup : EncoderLookup {

    private val list: MediaCodecList by lazy { MediaCodecList(MediaCodecList.REGULAR_CODECS) }

    /** El mismo formato que luego usa la prueba (entrada por Surface, H.264). */
    fun format(width: Int, height: Int, fps: Int, bitrate: Int): MediaFormat {
        val f = MediaFormat.createVideoFormat(MIME, width, height)
        f.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        f.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
        f.setInteger(MediaFormat.KEY_FRAME_RATE, fps)
        f.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        return f
    }

    override fun find(width: Int, height: Int, fps: Int, bitrate: Int): EncoderFacts? {
        try {
            val name = list.findEncoderForFormat(format(width, height, fps, bitrate)) ?: return null
            val info = list.codecInfos.firstOrNull { it.name == name } ?: return null
            val caps = info.getCapabilitiesForType(MIME) ?: return null
            val video = caps.videoCapabilities ?: return null
            if (!video.isSizeSupported(width, height)) return null
            if (!video.areSizeAndRateSupported(width, height, fps.toDouble())) return null

            val hardware: Boolean? = if (Build.VERSION.SDK_INT >= 29) info.isHardwareAccelerated else null
            val rates = try {
                video.getSupportedFrameRatesFor(width, height)
            } catch (e: IllegalArgumentException) {
                null
            }
            val top = caps.profileLevels.maxByOrNull { it.level }
            return EncoderFacts(
                name = name,
                hardware = hardware,
                widthRange = "${video.supportedWidths.lower}-${video.supportedWidths.upper}",
                heightRange = "${video.supportedHeights.lower}-${video.supportedHeights.upper}",
                frameRateForSize = rates?.let { String.format(java.util.Locale.US, "%.0f-%.0f", it.lower, it.upper) },
                bitrateRange = "${video.bitrateRange.lower}-${video.bitrateRange.upper}",
                profileLevel = top?.let { "perfil ${it.profile}, nivel ${it.level} (códigos de Android)" },
            )
        } catch (e: RuntimeException) {
            return null
        }
    }

    companion object {
        const val MIME = MediaFormat.MIMETYPE_VIDEO_AVC
    }
}
