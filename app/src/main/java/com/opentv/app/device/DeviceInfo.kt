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
    /** «compilación N · firma XXXXXXXXXXXX»: sirve para comprobar por qué un APK no se instala encima (firma o número distintos). */
    val appBuildInfo: String = "",
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
            appBuildInfo = readBuildInfo(),
        )
    }

    /** Número de compilación (versionCode) y huella corta de la firma del APK instalado. Dos APK se actualizan uno encima del otro solo si la firma es igual y el número no baja. */
    @Suppress("DEPRECATION")
    private fun readBuildInfo(): String = try {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val pi = pm.getPackageInfo(context.packageName, flags)
        val sigs = if (Build.VERSION.SDK_INT >= 28) pi.signingInfo?.apkContentsSigners else pi.signatures
        val fp = sigs?.firstOrNull()?.toByteArray()?.let { b ->
            java.security.MessageDigest.getInstance("SHA-256").digest(b).take(6).joinToString("") { "%02X".format(it) }
        } ?: "?"
        val code = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
        "compilación $code · firma $fp"
    } catch (e: Exception) {
        ""
    }

    @Suppress("DEPRECATION")
    private fun readVersionName(): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    } catch (e: PackageManager.NameNotFoundException) {
        ""
    }
}
