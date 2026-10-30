package com.opentv.app.catalog

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/**
 * Punto único del catálogo (una sola instancia por proceso, para que Inicio y Ajustes compartan estado y cachés).
 * No hay claves en el código: las introduce el usuario en Ajustes › Catálogo y se guardan con [SecretStore].
 * Nada se descarga al abrir la app: solo cuando Inicio está visible y hay datos vencidos, o al pedirlo en Ajustes.
 */
class CatalogServices private constructor(app: Application) {
    private val secrets = SecretStore(app)
    private val repository = CatalogRepository(
        dir = File(app.cacheDir, "catalog"),
        fetch = { request, maxBytes -> HttpFetcher.get(request, maxBytes) },
        now = { System.currentTimeMillis() },
    )
    private val disk = ImageDiskCache(File(app.cacheDir, "catalog-img"), MAX_DISK_BYTES)
    private val lowRam = (app.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true
    private val loader = ImageLoader(
        disk = disk,
        fetch = { request, maxBytes -> HttpFetcher.get(request, maxBytes) },
        memoryBytes = ((Runtime.getRuntime().maxMemory() / 16).toInt().coerceIn(6 * MB, 24 * MB)) / (if (lowRam) 2 else 1),
    )
    private val rpdbPolicy = RpdbPolicy()
    private val refreshLock = Mutex()

    @Volatile private var cachedLoaded = false
    @Volatile private var tmdbKey: String? = null
    @Volatile private var rpdbKey: String? = null
    @Volatile private var keysLoaded = false

    private val _state = MutableStateFlow(
        CatalogUiState(tmdbConfigured = secrets.has(SecretStore.TMDB), rpdbConfigured = secrets.has(SecretStore.RPDB)),
    )
    val state: StateFlow<CatalogUiState> = _state.asStateFlow()

    private fun loadKeys() {
        if (keysLoaded) return
        synchronized(this) {
            if (keysLoaded) return
            tmdbKey = secrets.get(SecretStore.TMDB)
            rpdbKey = secrets.get(SecretStore.RPDB)
            keysLoaded = true
        }
    }

    private fun storageNote(): String {
        val protections = listOfNotNull(secrets.protectionOf(SecretStore.TMDB), secrets.protectionOf(SecretStore.RPDB))
        return when {
            protections.isEmpty() -> ""
            protections.all { it == SecretStore.Protection.KEYSTORE } ->
                "Cifradas con el almacén de claves de Android, dentro del almacenamiento privado de OpenTV."
            else ->
                "Guardadas en el almacenamiento privado de OpenTV SIN cifrado adicional (el almacén de claves de Android no estuvo disponible)."
        }
    }

    private fun refreshFlags() {
        _state.update {
            it.copy(
                tmdbConfigured = secrets.has(SecretStore.TMDB),
                rpdbConfigured = secrets.has(SecretStore.RPDB),
                rpdbActive = secrets.has(SecretStore.RPDB) && rpdbPolicy.isActive(),
                rpdbNote = rpdbPolicy.disabledReason,
                storageNote = storageNote(),
            )
        }
    }

    /** Muestra lo guardado y, si hay datos vencidos (o [force]), actualiza. Seguro de llamar muchas veces. */
    suspend fun refresh(force: Boolean = false) {
        withContext(Dispatchers.IO) {
            loadKeys()
            if (!cachedLoaded) {
                val snapshot = repository.cached()
                cachedLoaded = true
                _state.update { it.copy(rows = snapshot.rows, imageConfig = snapshot.config, updatedAtMillis = snapshot.updatedAtMillis) }
            }
            refreshFlags()
            val key = tmdbKey
            if (key == null) {
                _state.update { it.copy(problem = CatalogProblem.NO_CREDENTIAL, problemDetail = null, loading = false) }
                return@withContext
            }
            if (!refreshLock.tryLock()) return@withContext
            try {
                _state.update { it.copy(loading = true) }
                val r = repository.refresh(key, force)
                _state.update {
                    it.copy(
                        loading = false, rows = r.snapshot.rows, imageConfig = r.snapshot.config,
                        updatedAtMillis = r.snapshot.updatedAtMillis, problem = r.problem, problemDetail = r.detail,
                    )
                }
            } finally {
                refreshLock.unlock()
            }
        }
    }

    /** Guarda la clave de TMDB. Devuelve un mensaje de error, o `null` si se guardó. No hace ninguna petición. */
    suspend fun saveTmdb(raw: String): String? = withContext(Dispatchers.IO) {
        val key = raw.trim()
        if (TmdbEndpoints.kindOf(key) == TmdbCredentialKind.INVALID) {
            return@withContext "El formato no corresponde a una clave v3 ni a un token v4 de TMDB."
        }
        secrets.put(SecretStore.TMDB, key)
        tmdbKey = key
        keysLoaded = true
        _state.update { it.copy(tmdbTest = null, problem = null, problemDetail = null) }
        refreshFlags()
        null
    }

    suspend fun saveRpdb(raw: String): String? = withContext(Dispatchers.IO) {
        val key = raw.trim()
        if (key.length !in 4..80 || !key.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
            return@withContext "La clave de RPDB tiene un formato no válido (solo letras, números, guiones)."
        }
        secrets.put(SecretStore.RPDB, key)
        rpdbKey = key
        keysLoaded = true
        rpdbPolicy.reset()
        _state.update { it.copy(rpdbTest = null) }
        refreshFlags()
        null
    }

    suspend fun testTmdb() {
        withContext(Dispatchers.IO) {
            loadKeys()
            val key = tmdbKey
            _state.update { it.copy(testing = true) }
            val result = if (key == null) TestResult(false, "No hay clave de TMDB guardada.", System.currentTimeMillis()) else repository.testTmdb(key)
            _state.update { it.copy(testing = false, tmdbTest = result) }
        }
    }

    suspend fun testRpdb() {
        withContext(Dispatchers.IO) {
            loadKeys()
            _state.update { it.copy(testing = true) }
            val result = repository.testRpdb(rpdbKey.orEmpty())
            if (result.ok == true) rpdbPolicy.reset()
            _state.update { it.copy(testing = false, rpdbTest = result) }
            refreshFlags()
        }
    }

    /** Borra las claves guardadas y todo lo descargado con ellas (catálogo e imágenes). */
    suspend fun clearKeys() {
        withContext(Dispatchers.IO) {
            secrets.remove(SecretStore.TMDB)
            secrets.remove(SecretStore.RPDB)
            tmdbKey = null
            rpdbKey = null
            keysLoaded = true
            rpdbPolicy.reset()
            repository.clear()
            disk.clear()
            loader.clearMemory()
            _state.update {
                CatalogUiState(storageNote = "")
            }
            cachedLoaded = true
        }
    }

    suspend fun clearImageCache() {
        withContext(Dispatchers.IO) {
            disk.clear()
            loader.clearMemory()
        }
    }

    suspend fun imageCacheBytes(): Long = withContext(Dispatchers.IO) { disk.sizeBytes() }

    /** Póster de [item]: de RPDB si hay clave y responde; si no, de TMDB. `null` = no hay imagen disponible. */
    suspend fun poster(item: CatalogItem, widthPx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        loadKeys()
        val rpdb = rpdbKey
        if (rpdb != null && rpdbPolicy.isActive()) {
            val r = loader.load(
                ImageRequest(
                    cacheKey = ImageSizing.cacheKey("rpdb-poster", item.kind.rpdbPrefix, item.id.toString()),
                    request = RpdbEndpoints.poster(rpdb, item),
                    targetWidthPx = widthPx,
                    highQuality = false,
                ),
            )
            if (r.image != null) {
                rpdbPolicy.onSuccess()
                return@withContext r.image
            }
            rpdbPolicy.onFailure(r.httpCode)
            if (!rpdbPolicy.isActive()) refreshFlags()
        }
        tmdbImage(item.posterPath, _state.value.imageConfig?.posterSizes.orEmpty(), widthPx, "w342", "poster", highQuality = false)
    }

    /** Fondo de [item] (de TMDB). */
    suspend fun backdrop(item: CatalogItem, widthPx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        tmdbImage(item.backdropPath, _state.value.imageConfig?.backdropSizes.orEmpty(), widthPx, "w780", "backdrop", highQuality = true)
    }

    private suspend fun tmdbImage(
        path: String?,
        sizes: List<String>,
        widthPx: Int,
        fallbackSize: String,
        label: String,
        highQuality: Boolean,
    ): ImageBitmap? {
        val config = _state.value.imageConfig ?: return null
        if (path == null) return null
        val size = ImageSizing.pick(sizes, widthPx, fallbackSize)
        return loader.load(
            ImageRequest(
                cacheKey = ImageSizing.cacheKey("tmdb-$label", size, path),
                request = CatalogRequest(ImageSizing.url(config, path, size), emptyMap()),
                targetWidthPx = widthPx,
                highQuality = highQuality,
            ),
        ).image
    }

    companion object {
        private const val MB = 1024 * 1024
        private const val MAX_DISK_BYTES = 64L * MB

        @Volatile private var instance: CatalogServices? = null

        fun get(app: Application): CatalogServices =
            instance ?: synchronized(this) { instance ?: CatalogServices(app).also { instance = it } }
    }
}
