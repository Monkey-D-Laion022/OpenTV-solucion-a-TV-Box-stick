package com.opentv.app.iptv

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.view.Surface
import com.opentv.app.stream.ScreenEncoder
import com.opentv.app.stream.StreamProfile
import com.opentv.app.stream.TsMuxer

/**
 * Mide cuántos cuadros por segundo SOSTIENE de verdad el codificador del teléfono con un perfil: dibuja cuadros
 * sintéticos en movimiento a la frecuencia pedida y cuenta los que salen codificados. No necesita permiso de captura
 * ni audio. La pantalla real suele ser más fácil de codificar que este patrón, así que el resultado es conservador.
 * Bloquea el hilo que lo llama (unos [seconds] segundos): usarlo fuera del hilo principal.
 */
object EncoderBenchmark {
    fun run(preset: IptvPreset, seconds: Int = 5): PerfResult {
        val muxer = TsMuxer(false)
        val origin = System.nanoTime() / 1000
        val level = AvcLevels.forStream(preset.width, preset.height, preset.fps)
        val profile = if (preset.height >= 1080) StreamProfile.AVC_TS else StreamProfile.HD720
        val encoder = ScreenEncoder(
            profile, muxer, origin, fps = preset.fps, bitrate = preset.bitrateBps, iFrameSeconds = 1, avcLevel = level,
            width = preset.width, height = preset.height, codecName = EncoderProbe.pick(preset.width, preset.height, preset.fps),
        ) { _, _ -> }
        val surface: Surface = encoder.start()
        var drawn = 0L
        var elapsed = 1.0
        var encoded = 0L
        val startNs = System.nanoTime()
        try {
            val paint = Paint()
            val frameNs = 1_000_000_000L / preset.fps
            var next = startNs
            var n = 0
            while (System.nanoTime() - startNs < seconds * 1_000_000_000L) {
                val canvas: Canvas? = try {
                    if (Build.VERSION.SDK_INT >= 23) surface.lockHardwareCanvas() else surface.lockCanvas(null)
                } catch (e: RuntimeException) {
                    null
                }
                if (canvas != null) {
                    canvas.drawColor(Color.rgb((n * 3) % 255, 40, 90))
                    for (k in 0 until 40) {
                        paint.color = Color.rgb((k * 37 + n * 5) % 255, (k * 91 + n * 3) % 255, (k * 13 + n) % 255)
                        val x = ((n * 17 + k * 131) % (preset.width - 200)).toFloat()
                        val y = ((n * 11 + k * 71) % (preset.height - 120)).toFloat()
                        canvas.drawRect(x, y, x + 200f, y + 120f, paint)
                    }
                    surface.unlockCanvasAndPost(canvas)
                    drawn++
                }
                n++
                next += frameNs
                val sleepMs = (next - System.nanoTime()) / 1_000_000
                if (sleepMs > 0) Thread.sleep(sleepMs) else if (sleepMs < -250) next = System.nanoTime()
            }
        } finally {
            elapsed = ((System.nanoTime() - startNs) / 1e9).coerceAtLeast(0.001)
            encoded = encoder.encodedFrames.get()
            encoder.stop()
            surface.release()
        }
        return PerfResult(preset, elapsed, encoded / elapsed, drawn / elapsed)
    }
}
