package com.opentv.app.apps

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.opentv.app.OpenTvApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppsUiState(
    val isLoading: Boolean = true,
    val allApps: List<InstalledApp> = emptyList(),
    val query: String = "",
    val sortMode: SortMode = SortMode.FAVORITES_FIRST,
    val favorites: Set<String> = emptySet(),
    /** Error al leer la lista de apps, o `null`. Los errores al abrir una app van en [appLaunch]. */
    val loadError: String? = null,
)

class AppsViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as OpenTvApp).container
    private val repository = container.repository
    private val favoritesStore = container.favorites

    val iconLoader: AppIconLoader = container.iconLoader

    private val _state = MutableStateFlow(AppsUiState())
    val state: StateFlow<AppsUiState> = _state.asStateFlow()

    val appLaunch = AppLaunchController(opener = container.launcher, onAppMissing = { refresh() })

    private var refreshJob: Job? = null

    /** Vuelve a leer las apps instaladas. Se llama al mostrar la pantalla y con «Actualizar». */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, loadError = null) }
            try {
                val apps = withContext(Dispatchers.IO) { repository.loadInstalledApps() }
                _state.update {
                    it.copy(isLoading = false, allApps = apps, favorites = favoritesStore.load())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, loadError = "No se pudo leer la lista de aplicaciones.")
                }
            }
        }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun cycleSortMode() {
        _state.update { it.copy(sortMode = it.sortMode.next()) }
    }

    fun toggleFavorite(app: InstalledApp) {
        val updated = favoritesStore.toggle(app.packageName)
        _state.update { it.copy(favorites = updated) }
    }
}
