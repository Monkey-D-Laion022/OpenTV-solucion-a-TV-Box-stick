package com.opentv.app.stream

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.View
import java.util.concurrent.atomic.AtomicLong

/**
 * Dibuja la interfaz de OpenTV sobre la superficie del codificador a ritmo fijo, sin depender de que la
 * pantalla virtual avise cuándo hay un cuadro nuevo. Así el codificador siempre recibe video: aunque la
 * interfaz esté quieta, o todavía no haya dibujado nada (en ese caso se envía un cartel «OpenTV»).
 * Corre en el hilo principal, que es donde se puede dibujar una vista.
 */
class ViewFrameFeeder(
    private val surface: Surface,
    private val width: Int,
    private val height: Int,
    private val fps: Int,
    private val viewProvider: () -> View?,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = height / 10f
    }
    @Volatile private var running = false

    val drawnFrames = AtomicLong(0)
    @Volatile var uiVisible = false
        private set

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            if (!drawOnce()) {
                running = false
                return
            }
            handler.postDelayed(this, (1000L / fps))
        }
    }

    fun start() {
        running = true
        handler.post(tick)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(tick)
    }

    /** `false` si la superficie ya no existe y hay que dejar de dibujar. */
    private fun drawOnce(): Boolean {
        val canvas: Canvas = try {
            lock()
        } catch (e: RuntimeException) {
            return false
        }
        try {
            canvas.drawColor(BACKGROUND)
            val view = viewProvider()
            if (view != null && view.width > 0 && view.height > 0) {
                canvas.save()
                canvas.scale(width.toFloat() / view.width, height.toFloat() / view.height)
                view.draw(canvas)
                canvas.restore()
                uiVisible = true
            } else {
                uiVisible = false
                canvas.drawText("OpenTV", width / 2f, height / 2f, paint)
            }
            drawnFrames.incrementAndGet()
        } catch (e: RuntimeException) {
            // Un cuadro que falla no debe cortar la proyección.
        } finally {
            try {
                surface.unlockCanvasAndPost(canvas)
            } catch (e: RuntimeException) {
                return false
            }
        }
        return true
    }

    private fun lock(): Canvas = try {
        surface.lockHardwareCanvas()
    } catch (e: RuntimeException) {
        surface.lockCanvas(null) // equipos donde el codificador no acepta dibujo por GPU
    }

    private companion object {
        val BACKGROUND = Color.parseColor("#0B0F1A")
    }
}
