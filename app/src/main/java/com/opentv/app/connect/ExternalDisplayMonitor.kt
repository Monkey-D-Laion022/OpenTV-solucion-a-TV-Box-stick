package com.opentv.app.connect

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Detecta pantallas externas conectadas ahora mismo con `DisplayManager` (API pública). Es la
 * única señal REAL de conexión que Android da a una app. No distingue si la pantalla es HDMI,
 * USB-C o una transmisión inalámbrica: por eso los textos lo dicen así.
 */
class ExternalDisplayMonitor(private val context: Context) {

    private val displayManager: DisplayManager
        get() = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    fun current(): ExternalDisplay? =
        displayManager.displays
            .firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
            ?.let { ExternalDisplay(it.name.orEmpty().ifBlank { "Pantalla externa" }) }

    /** Emite el estado actual y cada cambio. Se desregistra solo al dejar de recolectarse. */
    fun observe(): Flow<ExternalDisplay?> = callbackFlow {
        val manager = displayManager
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {
                trySend(current())
            }

            override fun onDisplayRemoved(displayId: Int) {
                trySend(current())
            }

            override fun onDisplayChanged(displayId: Int) {
                trySend(current())
            }
        }
        manager.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        trySend(current())
        awaitClose { manager.unregisterDisplayListener(listener) }
    }.distinctUntilChanged()
}
