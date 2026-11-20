package com.opentv.home

import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.text.format.DateFormat
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import java.util.Date
import kotlin.random.Random

/**
 * Protector de pantalla: reloj sobre fondo negro que cambia de lugar cada minuto
 * (evita marcar la pantalla). Se elige en Ajustes → Pantalla → Protector de pantalla.
 * Usa Views clásicas a propósito: un DreamService no necesita Compose para esto.
 */
class OpenTvDreamService : DreamService() {

    private val handler = Handler(Looper.getMainLooper())
    private var root: FrameLayout? = null
    private var clock: TextView? = null

    private val tick = object : Runnable {
        override fun run() {
            updateClock()
            handler.postDelayed(this, 60_000L)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true

        val container = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val text = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 72f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        }
        container.addView(
            text,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        root = container
        clock = text
        setContentView(container)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        handler.post(tick)
    }

    override fun onDreamingStopped() {
        handler.removeCallbacks(tick)
        super.onDreamingStopped()
    }

    private fun updateClock() {
        val container = root ?: return
        val text = clock ?: return
        text.text = DateFormat.getTimeFormat(this).format(Date())
        container.post {
            val maxX = (container.width - text.width).coerceAtLeast(0)
            val maxY = (container.height - text.height).coerceAtLeast(0)
            text.x = if (maxX > 0) Random.nextInt(maxX).toFloat() else 0f
            text.y = if (maxY > 0) Random.nextInt(maxY).toFloat() else 0f
        }
    }
}
