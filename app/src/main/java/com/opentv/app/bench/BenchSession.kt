package com.opentv.app.bench

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.MediaCodec
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Una autorización de captura = UNA pantalla virtual (Android 14 no deja crear otra con la misma autorización); entre
 * pruebas se le cambia el tamaño y el destino. Para cada configuración mide, por separado:
 *  1. solo la captura (la pantalla virtual entrega a un ImageReader; cada cuadro se cuenta y se descarta sin leerlo);
 *  2. captura + codificación (la pantalla virtual entrega al codificador por hardware; se cuentan sus salidas).
 * Ningún cuadro se guarda ni se envía a ningún lado. No toca la resolución ni la rotación del teléfono.
 */
class BenchSession(
    context: Context,
    private val projection: MediaProjection,
    private val lookup: AndroidEncoderLookup,
) {
    private val dpi: Int = context.applicationContext.resources.displayMetrics.densityDpi
    private var display: VirtualDisplay? = null

    /** Crea la pantalla virtual (sin destino todavía). */
    fun open(width: Int, height: Int) {
        val vd = projection.createVirtualDisplay(
            "OpenTV-Medicion", width, height, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, null, null, null,
        ) ?: throw IllegalStateException("Android no creó la pantalla de captura.")
        display = vd
    }

    fun close() {
        val vd = display
        display = null
        if (vd != null) {
            try {
                vd.setSurface(null)
            } catch (e: RuntimeException) {
                // ya liberada
            }
            try {
                vd.release()
            } catch (e: RuntimeException) {
                // ya liberada
            }
        }
    }

    suspend fun measure(option: BenchPlan.Option, phaseMillis: Long): ConfigResult {
        val cfg = option.config
        var capture: PhaseStats? = null
        var encode: PhaseStats? = null
        var error: String? = null
        try {
            capture = captureStats(cfg, phaseMillis)
            encode = encodeStats(option, phaseMillis)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "${e.javaClass.simpleName}: ${e.message ?: "sin detalle"}"
        }
        return ConfigResult(cfg, option.encoder, capture, encode, error)
    }

    private suspend fun captureStats(cfg: BenchConfig, ms: Long): PhaseStats {
        val vd = display ?: throw IllegalStateException("La pantalla de captura no está abierta.")
        val thread = HandlerThread("opentv-bench-capture")
        thread.start()
        val reader = ImageReader.newInstance(cfg.width, cfg.height, PixelFormat.RGBA_8888, 3)
        val meter = FpsMeter()
        try {
            reader.setOnImageAvailableListener(
                { r ->
                    try {
                        while (true) {
                            val image = r.acquireNextImage() ?: break
                            image.close() // se descarta sin leer los píxeles
                            meter.tick(System.nanoTime())
                        }
                    } catch (e: RuntimeException) {
                        // un cuadro que falla se salta
                    }
                },
                Handler(thread.looper),
            )
            vd.resize(cfg.width, cfg.height, dpi)
            vd.setSurface(reader.surface)
            val start = System.nanoTime()
            delay(ms)
            val end = System.nanoTime()
            vd.setSurface(null)
            return statsFrom(meter, start + WARMUP_NANOS, end, bytes = null, runNanos = end - start)
        } finally {
            try {
                vd.setSurface(null)
            } catch (e: RuntimeException) {
                // ignorado
            }
            try {
                reader.setOnImageAvailableListener(null, null)
                reader.close()
            } catch (e: RuntimeException) {
                // ignorado
            }
            thread.quitSafely()
        }
    }

    private suspend fun encodeStats(option: BenchPlan.Option, ms: Long): PhaseStats = coroutineScope {
        val cfg = option.config
        val vd = display ?: throw IllegalStateException("La pantalla de captura no está abierta.")
        val meter = FpsMeter()
        val bytes = AtomicLong(0)
        var codec: MediaCodec? = null
        var input: Surface? = null
        try {
            val c = MediaCodec.createByCodecName(option.encoder.name)
            codec = c
            c.configure(lookup.format(cfg.width, cfg.height, cfg.fps, cfg.bitrate), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = c.createInputSurface()
            input = surface
            c.start()
            vd.resize(cfg.width, cfg.height, dpi)
            vd.setSurface(surface)

            val drain = launch(Dispatchers.IO) {
                val info = MediaCodec.BufferInfo()
                var ended = false
                while (isActive && !ended) {
                    val index = try {
                        c.dequeueOutputBuffer(info, 20_000)
                    } catch (e: IllegalStateException) {
                        break
                    }
                    if (index >= 0) {
                        val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        if (!isConfig && info.size > 0) {
                            meter.tick(System.nanoTime())
                            bytes.addAndGet(info.size.toLong())
                        }
                        ended = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        c.releaseOutputBuffer(index, false) // el contenido se descarta
                    }
                }
            }
            val start = System.nanoTime()
            delay(ms)
            val end = System.nanoTime()
            vd.setSurface(null)
            try {
                c.signalEndOfInputStream()
            } catch (e: IllegalStateException) {
                // ya detenido
            }
            withTimeoutOrNull(2_000) { drain.join() }
            drain.cancel()
            statsFrom(meter, start + WARMUP_NANOS, end, bytes = bytes.get(), runNanos = end - start)
        } finally {
            try {
                vd.setSurface(null)
            } catch (e: RuntimeException) {
                // ignorado
            }
            val c = codec
            if (c != null) {
                try {
                    c.stop()
                } catch (e: RuntimeException) {
                    // ignorado
                }
                try {
                    c.release()
                } catch (e: RuntimeException) {
                    // ignorado
                }
            }
            input?.release()
        }
    }

    private fun statsFrom(meter: FpsMeter, fromNanos: Long, toNanos: Long, bytes: Long?, runNanos: Long): PhaseStats {
        val perSecond = meter.perSecond(fromNanos, toNanos)
        val kbps = if (bytes != null && runNanos > 0) (bytes * 8 * 1_000_000_000L / runNanos / 1000).toInt() else null
        return PhaseStats(
            avgFps = meter.fpsIn(fromNanos, toNanos),
            minFpsPerSecond = perSecond.minOrNull(),
            maxFpsPerSecond = perSecond.maxOrNull(),
            frames = meter.total(),
            bytes = bytes,
            measuredKbps = kbps,
        )
    }

    private companion object {
        /** El primer segundo (arranque del codificador y de la pantalla virtual) no entra en el promedio. */
        const val WARMUP_NANOS = 1_000_000_000L
    }
}
