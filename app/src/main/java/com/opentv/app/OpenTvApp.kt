package com.opentv.app

import android.app.Application
import android.content.ComponentCallbacks2

class OpenTvApp : Application() {

    /** Dependencias compartidas; se crean perezosamente al usarse por primera vez. */
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // Al arrancar el proceso no puede haber una transmisión en curso: si quedó el volumen silenciado por un cierre
        // brusco, se devuelve. (Es el proceso, no la Activity: no se repite al girar ni al cambiar la densidad.)
        runCatching { container.phoneMute.restore() }
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Solo ante presión real de memoria; ir a otra app (UI_HIDDEN) no justifica vaciar la caché.
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
        ) {
            container.trimMemory()
        }
    }
}
