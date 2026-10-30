package com.opentv.app.catalog

import java.io.File

/**
 * Caché en disco de imágenes (bytes originales ya descargados). PURO (solo `java.io`). Los nombres salen de
 * [ImageSizing.cacheKey]; nunca contienen claves. Al superar [maxBytes] se borran los menos usados recientemente.
 */
class ImageDiskCache(private val dir: File, private val maxBytes: Long) {
    private var writesSinceTrim = 0

    fun read(key: String): ByteArray? = try {
        val f = File(dir, key)
        if (f.isFile) {
            f.setLastModified(System.currentTimeMillis())
            f.readBytes()
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }

    @Synchronized
    fun write(key: String, bytes: ByteArray) {
        try {
            dir.mkdirs()
            val tmp = File(dir, "$key.tmp")
            tmp.writeBytes(bytes)
            val dest = File(dir, key)
            if (!tmp.renameTo(dest)) {
                dest.delete()
                tmp.renameTo(dest)
            }
            if (++writesSinceTrim >= 10) {
                writesSinceTrim = 0
                trim()
            }
        } catch (e: Exception) {
            // Disco lleno o sin permiso: se sigue sin caché.
        }
    }

    /** Deja la carpeta por debajo del límite borrando primero lo más viejo. */
    @Synchronized
    fun trim() {
        val files = dir.listFiles()?.filter { it.isFile } ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return
        for (f in files.sortedBy { it.lastModified() }) {
            if (total <= maxBytes) break
            total -= f.length()
            f.delete()
        }
    }

    fun remove(key: String) {
        runCatching { File(dir, key).delete() }
    }

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    fun sizeBytes(): Long = dir.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
}
