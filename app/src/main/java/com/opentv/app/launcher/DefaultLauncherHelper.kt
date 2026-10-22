package com.opentv.app.launcher

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Ayuda para elegir a OpenTV como launcher predeterminado SIN imponerlo: solo abre la pantalla
 * oficial de Android donde el usuario elige (y donde puede volver a su launcher original).
 */
class DefaultLauncherHelper(private val context: Context) {

    /** `true` si Android resuelve el botón Inicio hacia OpenTV. */
    fun isDefault(): Boolean {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(
            homeIntent,
            PackageManager.MATCH_DEFAULT_ONLY,
        )
        return resolved?.activityInfo?.packageName == context.packageName
    }

    /** Abre los ajustes de app de inicio; si el equipo no los tiene, los ajustes generales. */
    fun openChooserSettings(): Boolean = tryStart(Settings.ACTION_HOME_SETTINGS) ||
        tryStart(Settings.ACTION_SETTINGS)

    private fun tryStart(action: String): Boolean = try {
        val intent = Intent(action)
        // Con un contexto que no es una Activity, Android exige iniciar una tarea nueva.
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}
