package com.opentv.app.connect

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Abre la pantalla oficial de transmisión de Android (`Settings.ACTION_CAST_SETTINGS`). Es el
 * único camino que Android ofrece a una app para iniciar la duplicación de pantalla (Miracast u
 * otra): la conexión la crea y la corta el sistema, no OpenTV.
 */
class SystemCastLauncher(private val context: Context) {

    private fun castIntent(): Intent = Intent(Settings.ACTION_CAST_SETTINGS)

    /** `true` si el equipo tiene esa pantalla. Requiere el `<queries>` del manifest (Android 11+). */
    fun isAvailable(): Boolean = context.packageManager.resolveActivity(castIntent(), 0) != null

    /**
     * Devuelve `false` si el equipo no pudo abrirla. Con [from] una Activity visible se abre dentro de la misma tarea
     * (sin NEW_TASK), que en MIUI/HyperOS evita que el sistema mezcle la pantalla con los ajustes de Wi-Fi/red.
     */
    fun open(from: Context? = null): Boolean = try {
        val intent = castIntent()
        if (from is android.app.Activity) {
            from.startActivity(intent)
        } else {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}
