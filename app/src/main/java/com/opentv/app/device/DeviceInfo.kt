package com.opentv.app.device

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager

/** Instantánea de solo lectura de lo que el sistema informa sobre pantalla y equipo. */
data class DeviceInfo(
    val displayWidthPx: Int,
    val displayHeightPx: Int,
    val windowWidthPx: Int,
    val windowHeightPx: Int,
    val densityDpi: Int,
    val isLandscape: Boolean,
    val sdkInt: Int,
    val androidRelease: String,
    val manufacturer: String,
    val model: String,
    val totalRamBytes: Long,
    val isLowRamDevice: Boolean,
    val appVersionName: String,
)

/**
 * Lee [DeviceInfo] con API públicas. Con un contexto de Activity informa de la pantalla donde se
 * muestra esa Activity (por ejemplo, una pantalla externa si la app se abrió allí).
 */
class DeviceInfoProvider(private val context: Context) {

    @Suppress("DEPRECATION")
    fun read(): DeviceInfo {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val real = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(real)

        val window = context.resources.displayMetrics
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }

        return DeviceInfo(
            displayWidthPx = real.widthPixels,
            displayHeightPx = real.heightPixels,
            windowWidthPx = window.widthPixels,
            windowHeightPx = window.heightPixels,
            densityDpi = window.densityDpi,
            isLandscape = window.widthPixels >= window.heightPixels,
            sdkInt = Build.VERSION.SDK_INT,
            androidRelease = Build.VERSION.RELEASE.orEmpty(),
            manufacturer = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() },
            model = Build.MODEL.orEmpty(),
            totalRamBytes = memoryInfo.totalMem,
            isLowRamDevice = activityManager.isLowRamDevice,
            appVersionName = readVersionName(),
        )
    }

    @Suppress("DEPRECATION")
    private fun readVersionName(): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    } catch (e: PackageManager.NameNotFoundException) {
        ""
    }
}
