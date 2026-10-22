package com.opentv.app.apps

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Carga los íconos bajo demanda (solo los que se ven en pantalla) a 128×128 y los guarda en una
 * caché LRU con tope en bytes, para no agotar la memoria en teléfonos viejos.
 */
class AppIconLoader(
    context: Context,
    maxCacheBytes: Int = defaultCacheBytes(),
) {
    private val packageManager: PackageManager = context.packageManager

    private val cache = object : LruCache<String, Bitmap>(maxCacheBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    /** Devuelve el ícono, o `null` si la app ya no existe o el ícono no se puede leer. */
    suspend fun load(app: InstalledApp): Bitmap? {
        cache.get(app.packageName)?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val drawable = packageManager.getActivityIcon(
                    ComponentName(app.packageName, app.activityName),
                )
                val bitmap = drawable.toBitmap(ICON_SIZE_PX, ICON_SIZE_PX)
                cache.put(app.packageName, bitmap)
                bitmap
            } catch (e: CancellationException) {
                throw e
            } catch (e: PackageManager.NameNotFoundException) {
                null
            } catch (e: Exception) {
                null
            }
        }
    }

    fun clear() {
        cache.evictAll()
    }

    companion object {
        const val ICON_SIZE_PX = 128
        private const val MIN_CACHE_BYTES = 2L * 1024 * 1024
        private const val MAX_CACHE_BYTES = 8L * 1024 * 1024

        /** 1/16 de la memoria del proceso, entre 2 y 8 MB. */
        fun defaultCacheBytes(): Int =
            (Runtime.getRuntime().maxMemory() / 16).coerceIn(MIN_CACHE_BYTES, MAX_CACHE_BYTES).toInt()
    }
}
