package com.opentv.homenova.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.opentv.homenova.core.AppEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the installed, launchable applications from the platform.
 *
 * TV apps can expose either a LEANBACK_LAUNCHER activity or a regular LAUNCHER
 * activity; both are collected and de-duplicated by package name.
 */
class AppRepository(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager

    suspend fun loadApps(): List<AppEntry> = withContext(Dispatchers.IO) {
        val candidates = LinkedHashMap<String, android.content.pm.ResolveInfo>()

        val leanback = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val regular = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        listOf(leanback, regular).forEach { intent ->
            runCatching { packageManager.queryIntentActivities(intent, 0) }
                .getOrDefault(emptyList())
                .forEach { info ->
                    val packageName = info.activityInfo?.packageName ?: return@forEach
                    candidates.putIfAbsent(packageName, info)
                }
        }

        candidates.values.mapNotNull { info ->
            val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
            if (packageName == context.packageName) return@mapNotNull null

            val appInfo = runCatching {
                packageManager.getApplicationInfo(packageName, 0)
            }.getOrNull() ?: return@mapNotNull null

            if (!isLaunchable(packageName)) return@mapNotNull null

            AppEntry(
                packageName = packageName,
                label = runCatching { info.loadLabel(packageManager).toString() }
                    .getOrDefault(packageName),
                icon = runCatching { info.loadIcon(packageManager).toImageBitmapSafe() }.getOrNull(),
                isGame = appInfo.category == ApplicationInfo.CATEGORY_GAME,
                isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                updatedAt = runCatching { packageManager.getPackageInfo(packageName, 0).lastUpdateTime }
                    .getOrDefault(0L),
            )
        }.sortedBy { it.label.lowercase() }
    }

    private fun isLaunchable(packageName: String): Boolean =
        packageManager.getLeanbackLaunchIntentForPackage(packageName) != null ||
            packageManager.getLaunchIntentForPackage(packageName) != null

    fun launch(packageName: String): Boolean {
        val intent = packageManager.getLeanbackLaunchIntentForPackage(packageName)
            ?: packageManager.getLaunchIntentForPackage(packageName)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    fun openInfo(packageName: String): Boolean = runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    }.getOrDefault(false)

    fun requestUninstall(packageName: String): Boolean = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_DELETE)
                .setData(Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    }.getOrDefault(false)

    private fun Drawable.toImageBitmapSafe(): ImageBitmap =
        toBitmap(width = 128, height = 128).asImageBitmap()
}
