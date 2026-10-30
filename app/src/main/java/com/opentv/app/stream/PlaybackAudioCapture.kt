package com.opentv.app.stream

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

/**
 * Captura el audio que reproducen las apps del teléfono (API de captura de audio interno de Android 10+) para que viaje
 * junto con el video del espejo. Necesita el permiso de micrófono (Android lo exige para esta API; OpenTV NO usa el
 * micrófono, solo el audio interno) y la captura de pantalla ya concedida. Las apps que prohíben la captura de su
 * audio (algunas de video con protección) quedan en silencio: es una decisión de esas apps, no un fallo de OpenTV.
 */
class PlaybackAudioCapture private constructor(private val record: AudioRecord) : PcmSource {

    override fun read(dst: ByteArray, size: Int): Int = record.read(dst, 0, size, AudioRecord.READ_BLOCKING)

    override fun release() {
        runCatching { record.stop() }
        runCatching { record.release() }
    }

    sealed interface Result {
        class Ready(val source: PcmSource) : Result
        class Unavailable(val reason: String) : Result
    }

    companion object {
        const val MIN_SDK = 29

        fun supported(): Boolean = Build.VERSION.SDK_INT >= MIN_SDK

        fun hasPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        fun create(context: Context, projection: MediaProjection): Result {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                return Result.Unavailable("Este Android (anterior al 10) no permite capturar el audio interno.")
            }
            if (!hasPermission(context)) return Result.Unavailable("Falta el permiso de audio: el video sale sin sonido.")
            return createApi29(context, projection)
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        @SuppressLint("MissingPermission")
        private fun createApi29(context: Context, projection: MediaProjection): Result {
            return try {
                val config = AudioPlaybackCaptureConfiguration.Builder(projection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build()
                val format = AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(ScreenAudioEncoder.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                    .build()
                val minBuffer = AudioRecord.getMinBufferSize(
                    ScreenAudioEncoder.SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT,
                )
                val record = AudioRecord.Builder()
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(maxOf(minBuffer, 4 * 1024 * 4) * 2)
                    .setAudioPlaybackCaptureConfig(config)
                    .build()
                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    record.release()
                    return Result.Unavailable("Android no inicializó la captura de audio.")
                }
                record.startRecording()
                Result.Ready(PlaybackAudioCapture(record))
            } catch (e: RuntimeException) {
                Result.Unavailable("No se pudo capturar el audio (${e.javaClass.simpleName}:${e.message ?: "sin detalle"}).")
            }
        }
    }
}
