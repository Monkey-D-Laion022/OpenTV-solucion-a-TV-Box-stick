package com.opentv.app.apps

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Abre apps con un intent explícito a su actividad de inicio (mecanismo oficial). No usa root ni
 * accesibilidad. Nunca lanza excepciones hacia la UI: siempre devuelve un [LaunchResult].
 */
class AppLauncher(private val context: Context) : AppOpener {

    override fun launch(app: InstalledApp): LaunchResult {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(app.packageName, app.activityName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return try {
            context.startActivity(intent)
            LaunchResult.Success
        } catch (e: ActivityNotFoundException) {
            LaunchResult.NotFound
        } catch (e: SecurityException) {
            LaunchResult.Denied
        } catch (e: RuntimeException) {
            LaunchResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }
}
