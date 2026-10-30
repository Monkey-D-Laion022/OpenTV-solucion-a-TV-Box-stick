package com.opentv.app.catalog

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** Qué imagen pedir. [cacheKey] identifica la imagen sin incluir claves; [request] puede llevar la clave en la dirección. */
class ImageRequest(
    val cacheKey: String,
    val request: CatalogRequest,
    val targetWidthPx: Int,
    /** `true` = 32 bits por píxel (fondos); `false` = 16 bits (pósteres, la mitad de memoria). */
    val highQuality: Boolean,
)

/** Resultado de cargar una imagen. [httpCode] solo viene si el servidor respondió con error. */
class ImageResult(val image: ImageBitmap?, val httpCode: Int?)

/**
 * Cargador de imágenes para equipos modestos. Memoria: caché LRU medida en BYTES con tope fijo. Disco: [ImageDiskCache]
 * con límite. Red: como mucho 2 descargas a la vez. Cada imagen se decodifica ya reducida al ancho que se va a mostrar.
 * Un fallo se recuerda un minuto para no repetir pedidos en bucle. Se cancela con la corrutina que lo llama.
 */
class ImageLoader(
    private val disk: ImageDiskCache,
    private val fetch: Fetch,
    memoryBytes: Int,
) {
    private val memory = object : LruCache<String, Bitmap>(memoryBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val recentFailures = ConcurrentHashMap<String, Long>()
    private val gate = Semaphore(MAX_PARALLEL_DOWNLOADS)

    suspend fun load(req: ImageRequest): ImageResult = withContext(Dispatchers.IO) {
        val memoryKey = req.cacheKey + "@" + req.targetWidthPx
        memory.get(memoryKey)?.let { return@withContext ImageResult(it.asImageBitmap(), null) }
        recentFailures[memoryKey]?.let { at ->
            if (SystemClock.elapsedRealtime() - at < FAILURE_MEMORY_MS) return@withContext ImageResult(null, null)
        }

        var bytes = disk.read(req.cacheKey)
        var httpCode: Int? = null
        if (bytes == null) {
            gate.withPermit {
                bytes = disk.read(req.cacheKey)
                if (bytes == null) {
                    when (val r = fetch(req.request, MAX_IMAGE_BYTES)) {
                        is FetchResult.Ok -> {
                            bytes = r.bytes
                            disk.write(req.cacheKey, r.bytes)
                        }
                        is FetchResult.HttpError -> httpCode = r.code
                        is FetchResult.NetworkError -> Unit
                    }
                }
                Unit
            }
        }
        val data = bytes
        val bitmap = if (data == null) null else decode(data, req)
        if (bitmap == null) {
            if (data != null) disk.remove(req.cacheKey) // archivo dañado: se descarta para volver a bajarlo
            recentFailures[memoryKey] = SystemClock.elapsedRealtime()
            return@withContext ImageResult(null, httpCode)
        }
        memory.put(memoryKey, bitmap)
        ImageResult(bitmap.asImageBitmap(), null)
    }

    private fun decode(data: ByteArray, req: ImageRequest): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = ImageSizing.sampleSize(bounds.outWidth, req.targetWidthPx)
            inPreferredConfig = if (req.highQuality) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
        }
        BitmapFactory.decodeByteArray(data, 0, data.size, options)
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        memory.evictAll()
        null
    }

    fun clearMemory() {
        memory.evictAll()
        recentFailures.clear()
    }

    private companion object {
        const val MAX_PARALLEL_DOWNLOADS = 2
        const val MAX_IMAGE_BYTES = 4_000_000
        const val FAILURE_MEMORY_MS = 60_000L
    }
}
