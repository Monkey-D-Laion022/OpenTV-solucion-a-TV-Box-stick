package com.opentv.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

object AppRepository {

    /**
     * Lista las apps lanzables (de TV y de teléfono), sin la propia.
     * Requiere el bloque <queries> del manifest en Android 11+.
     * Llamar desde un hilo de fondo: carga íconos y banners.
     */
    fun load(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val own = context.packageName

        val tvIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val phoneIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        val tvResolved = pm.queryIntentActivities(tvIntent, 0)
        val tvPackages = tvResolved.map { it.activityInfo.packageName }.toSet()

        return (tvResolved + pm.queryIntentActivities(phoneIntent, 0))
            .asSequence()
            .filter { it.activityInfo.packageName != own }
            .distinctBy { it.activityInfo.packageName }
            .map { info ->
                val pkg = info.activityInfo.packageName
                val isTv = pkg in tvPackages
                val label = info.loadLabel(pm).toString()
                AppEntry(
                    label = label,
                    packageName = pkg,
                    icon = runCatching { info.loadIcon(pm).toBitmap(128, 128).asImageBitmap() }.getOrNull(),
                    banner = if (isTv) {
                        runCatching { info.activityInfo.loadBanner(pm)?.toBitmap(320, 180)?.asImageBitmap() }.getOrNull()
                    } else {
                        null
                    },
                    isTvApp = isTv,
                    searchKey = Norm.of(label),
                )
            }
            .sortedBy { it.searchKey }
            .toList()
    }

    fun launch(context: Context, packageName: String): Boolean {
        val pm = context.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(packageName)
            ?: pm.getLaunchIntentForPackage(packageName)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    /** Pantalla de información de la app (permisos, almacenamiento, forzar detención). */
    fun openAppInfo(context: Context, packageName: String): Boolean {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    /** Abre el diálogo de desinstalación del sistema; el usuario confirma ahí. */
    fun requestUninstall(context: Context, packageName: String): Boolean {
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}
