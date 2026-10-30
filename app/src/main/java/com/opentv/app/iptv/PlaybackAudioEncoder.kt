package com.opentv.app.iptv

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import androidx.annotation.RequiresApi
import com.opentv.app.stream.Adts

/**
 * Captura el AUDIO QUE REPRODUCE el teléfono (AudioPlaybackCapture, Android 10+) y lo codifica en AAC-LC con
 * cabecera ADTS, listo para el flujo TS. Requiere el permiso RECORD_AUDIO (se pide antes) y la misma
 * [MediaProjection] que captura la pantalla. Limitaciones reales de Android, no de OpenTV: las apps pueden
 * excluirse de la captura (`allowAudioPlaybackCapture=false`; p. ej. muchas de streaming con contenido protegido) y
 * entonces solo llegan ceros; las llamadas y las alarmas no se capturan. Por eso se mide el nivel de lo capturado:
 * [lastSoundAtMs] solo avanza cuando hay muestras que no son silencio. NO verificado en hardware.
 */
@RequiresApi(29)
class PlaybackAudioEncoder(
    private val projection: MediaProjection,
    private val originUs: Long,
    private val onFrame: (adts: ByteArray, ptsUs: Long) -> Unit,
) {
    private var record: AudioRecord? = null
    private var codec: MediaCodec? = null
    private var thread: Thread? = null
    @Volatile private var running = false

    /** Última vez (reloj del sistema, ms) que se oyó sonido real; 0 si todavía no. */
    @Volatile var lastSoundAtMs: Long = 0
        private set

    /** Muestras de audio entregadas al codificador (para comprobar que de verdad se capturan). */
    @Volatile var capturedSamples: Long = 0
        private set

    /** Lanza una excepción si el teléfono no puede capturar audio: quien llama sigue con la pista en silencio. */
    @SuppressLint("MissingPermission") // el permiso se comprueba antes de llamar
    fun start() {
        val config = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
            .build()
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord.Builder()
            .setAudioPlaybackCaptureConfig(config)
            .setAudioFormat(format)
            .setBufferSizeInBytes(maxOf(minBuffer, FRAME_BYTES * 8))
            .build()
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            throw IllegalStateException("Android no inició la captura de audio del sistema.")
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        try {
            val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, CHANNELS).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, FRAME_BYTES * 2)
            }
            encoder.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            rec.startRecording()
        } catch (e: RuntimeException) {
            runCatching { rec.release() }
            runCatching { encoder.release() }
            throw e
        }
        record = rec
        codec = encoder
        running = true
        thread = Thread({ loop(rec, encoder) }, "opentv-playback-audio").apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        thread?.join(1_000)
        thread = null
        record?.let { runCatching { it.stop() }; it.release() }
        record = null
        codec?.let { runCatching { it.stop() }; it.release() }
        codec = null
    }

    private fun loop(rec: AudioRecord, encoder: MediaCodec) {
        val info = MediaCodec.BufferInfo()
        val pcm = ByteArray(FRAME_BYTES)
        val startUs = System.nanoTime() / 1000 - originUs
        var samples = 0L
        try {
            while (running) {
                var filled = 0
                while (running && filled < FRAME_BYTES) {
                    val n = rec.read(pcm, filled, FRAME_BYTES - filled)
                    if (n < 0) return // error de lectura: la captura terminó
                    filled += n
                }
                if (!running) break
                if (AudioActivityMeter.isSound(pcm)) lastSoundAtMs = System.currentTimeMillis()
                val index = encoder.dequeueInputBuffer(20_000L)
                if (index >= 0) {
                    val input = encoder.getInputBuffer(index)
                    input?.clear()
                    input?.put(pcm, 0, FRAME_BYTES)
                    encoder.queueInputBuffer(index, 0, FRAME_BYTES, startUs + samples * 1_000_000L / SAMPLE_RATE, 0)
                    samples += SAMPLES_PER_FRAME
                    capturedSamples = samples
                }
                while (true) {
                    val out = encoder.dequeueOutputBuffer(info, 0)
                    if (out < 0) break
                    val buffer = encoder.getOutputBuffer(out)
                    if (buffer != null && info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        val aac = ByteArray(info.size)
                        buffer.position(info.offset)
                        buffer.get(aac)
                        onFrame(Adts.header(aac.size) + aac, info.presentationTimeUs)
                    }
                    encoder.releaseOutputBuffer(out, false)
                }
            }
        } catch (e: IllegalStateException) {
            // cierre normal
        }
    }

    companion object {
        const val SAMPLE_RATE = 48_000
        const val CHANNELS = 2
        const val SAMPLES_PER_FRAME = 1024
        const val FRAME_BYTES = SAMPLES_PER_FRAME * CHANNELS * 2
    }
}
