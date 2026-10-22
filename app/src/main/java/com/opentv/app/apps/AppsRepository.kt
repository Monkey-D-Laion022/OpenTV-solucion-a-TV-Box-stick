package com.opentv.app.apps

import android.content.Context
import android.content.Intent

/**
 * Lista las apps con pantalla de inicio usando PackageManager. No usa QUERY_ALL_PACKAGES: el
 * manifest declara `<queries>` solo para los intents LAUNCHER, LEANBACK_LAUNCHER y HOME.
 *
 * [loadInstalledApps] es una lectura bloqueante: llamarla fuera del hilo principal.
 */
class AppsRepository(private val context: Context) {

    fun loadInstalledApps(): List<InstalledApp> {
        val pm = context.packageManager
        val found = LinkedHashMap<String, InstalledApp>()

        // Primero las entradas de Android TV, para preferirlas si una app tiene ambas.
        val categories = listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
        for (category in categories) {
            val query = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (info in pm.queryIntentActivities(query, 0)) {
                val activity = info.activityInfo ?: continue
                val pkg = activity.packageName
                // OpenTV no se lista a sí misma; las actividades no exportadas no se pueden abrir.
                if (pkg == context.packageName || pkg in found || !activity.exported) continue

                found[pkg] = InstalledApp(
                    packageName = pkg,
                    activityName = activity.name,
                    label = info.loadLabel(pm).toString().ifBlank { pkg },
                    screenOrientation = activity.screenOrientation,
                )
            }
        }
        return found.values.toList()
    }
}
