package com.opentv.app.catalog

import android.app.Application
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Une el catálogo con las pantallas. La lógica vive en [CatalogServices]; esto solo lanza las operaciones. */
class CatalogViewModel(application: Application) : AndroidViewModel(application) {
    private val services = CatalogServices.get(application)

    val state: StateFlow<CatalogUiState> get() = services.state

    fun refresh(force: Boolean = false) {
        viewModelScope.launch { services.refresh(force) }
    }

    fun saveTmdb(raw: String, onResult: (String?) -> Unit) {
        viewModelScope.launch { onResult(services.saveTmdb(raw)) }
    }

    fun saveRpdb(raw: String, onResult: (String?) -> Unit) {
        viewModelScope.launch { onResult(services.saveRpdb(raw)) }
    }

    fun testTmdb() {
        viewModelScope.launch { services.testTmdb() }
    }

    fun testRpdb() {
        viewModelScope.launch { services.testRpdb() }
    }

    fun clearKeys() {
        viewModelScope.launch { services.clearKeys() }
    }

    fun clearImageCache(onDone: () -> Unit) {
        viewModelScope.launch {
            services.clearImageCache()
            onDone()
        }
    }

    suspend fun imageCacheBytes(): Long = services.imageCacheBytes()

    suspend fun poster(item: CatalogItem, widthPx: Int): ImageBitmap? = services.poster(item, widthPx)

    suspend fun backdrop(item: CatalogItem, widthPx: Int): ImageBitmap? = services.backdrop(item, widthPx)
}
