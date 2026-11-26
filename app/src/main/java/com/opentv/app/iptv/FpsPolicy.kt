package com.opentv.app.iptv

import android.content.Context

/**
 * Política de FPS elegida por la persona. «Mantener 60 fps»: el gobernador NUNCA baja los FPS pedidos (ni baja a una resolución de menos FPS);
 * ante sobrecarga solo se recorta el bitrate. Con el teléfono muy cargado puede haber tirones en vez de una bajada a 30: es lo que se pidió.
 */
object FpsPolicy {
    @Volatile var hold: Boolean = false
        private set
    private const val FILE = "opentv_fps_policy"
    private const val KEY = "hold"

    fun load(context: Context) {
        hold = try { context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY, false) } catch (e: RuntimeException) { false }
    }

    fun set(context: Context, value: Boolean) {
        hold = value
        try { context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY, value).apply() } catch (e: RuntimeException) { /* sin guardar */ }
    }
}
