package com.opentv.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.opentv.app.OpenTvApp
import com.opentv.app.apps.AppIconLoader
import com.opentv.app.apps.AppLaunchController
import com.opentv.app.apps.AppListLogic
import com.opentv.app.apps.InstalledApp
import com.opentv.app.apps.SortMode
import com.opentv.app.connect.ExternalDisplay
import com.opentv.app.connect.TvTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Estado del inicio: reloj y apps favoritas.
 * El reloj se actualiza cada 15 s y se detiene por completo cuando la pantalla no está visible
 * (WhileSubscribed), para no gastar batería en equipos viejos.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as OpenTvApp).container

    val iconLoader: AppIconLoader get() = container.iconLoader

    val nowMillis: StateFlow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(CLOCK_TICK_MS)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = System.currentTimeMillis(),
    )

    /** Pantalla externa real; el registro de DisplayManager solo existe con el inicio visible. */
    val externalDisplay: StateFlow<ExternalDisplay?> = container.displayMonitor.observe().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = null,
    )

    /** La resolución del teléfono está forzada por OpenTV y falta restaurarla. */
    val resizePending: StateFlow<Boolean> get() = container.resizer.pending

    /** TV elegido como salida, si hay uno. */
    val outputTarget: StateFlow<TvTarget?> get() = container.output.target

    /**
     * Botón único de 16:9: si está forzado lo restaura; si no, lo fuerza (con Shizuku lista). Si Shizuku no está lista
     * llama a [onNeedsSetup] para abrir el asistente en lugar de fallar.
     */
    fun toggle169(onNeedsSetup: () -> Unit) {
        if (container.resizer.hasPending) {
            container.nativeMirror.stop()
            return
        }
        if (container.shizuku.status() != com.opentv.app.display.ShizukuShell.Status.READY) {
            onNeedsSetup()
            return
        }
        container.nativeMirror.forceResolution()
    }

    /** Abre directamente la transmisión del sistema (Cast / Smart View / Miracast). */
    fun openNativeCast(from: android.content.Context) {
        container.systemCast.open(from)
    }

    private val _allApps = MutableStateFlow<List<InstalledApp>>(emptyList())

    /** Todas las apps instaladas (A–Z), para elegir cuáles van en la fila principal. */
    val allApps: StateFlow<List<InstalledApp>> = _allApps.asStateFlow()

    private val _favoriteKeys = MutableStateFlow<Set<String>>(emptySet())

    /** Paquetes fijados en la fila principal. */
    val favoriteKeys: StateFlow<Set<String>> = _favoriteKeys.asStateFlow()

    private val _favoriteApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val favoriteApps: StateFlow<List<InstalledApp>> = _favoriteApps.asStateFlow()

    val appLaunch = AppLaunchController(opener = container.launcher, onAppMissing = { refreshFavorites() })

    private var refreshJob: Job? = null

    /** Relee las apps y deja solo las favoritas que siguen instaladas. */
    fun refreshFavorites() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            try {
                val favorites = container.favorites.load()
                val apps = withContext(Dispatchers.IO) { container.repository.loadInstalledApps() }
                _allApps.value = AppListLogic.sort(apps, SortMode.NAME_ASC, favorites)
                _favoriteKeys.value = favorites
                _favoriteApps.value = AppListLogic.sort(
                    apps.filter { it.packageName in favorites },
                    SortMode.NAME_ASC,
                    favorites,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Las favoritas son un extra del inicio: si falla la lectura, simplemente no se muestran.
                _favoriteApps.value = emptyList()
                _allApps.value = emptyList()
            }
        }
    }

    /** Agrega la app a la fila principal o, si ya estaba, la quita. */
    fun toggleFavorite(app: InstalledApp) {
        container.favorites.toggle(app.packageName)
        refreshFavorites()
    }

    private companion object {
        const val CLOCK_TICK_MS = 15_000L
    }
}
