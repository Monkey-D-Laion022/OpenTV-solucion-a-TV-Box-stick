package com.opentv.app.ui.remote

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.opentv.app.OpenTvApp
import com.opentv.app.apps.AppIconLoader
import com.opentv.app.apps.AppLaunchController
import com.opentv.app.apps.AppListLogic
import com.opentv.app.apps.InstalledApp
import com.opentv.app.apps.SortMode
import com.opentv.app.connect.TvTarget
import com.opentv.app.stream.Direction
import com.opentv.app.display.ResizeResult
import com.opentv.app.stream.MirrorRequest
import com.opentv.app.stream.ProjectionState
import com.opentv.app.stream.ProjectionStats
import com.opentv.app.stream.StreamProfile
import com.opentv.app.stream.TvCursor
import com.opentv.app.stream.TvNavigation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Lo que se dibuja en el TV: las apps de la fila principal y dónde está el cursor. */
data class TvUiState(
    val apps: List<InstalledApp> = emptyList(),
    val cursor: TvCursor = TvCursor(),
    /** Aviso breve en el TV (por ejemplo «Abriendo…»). */
    val notice: String? = null,
) {
    /** Fila 0: apps. Fila 1: la opción «Detener proyección». */
    val rowSizes: List<Int> get() = listOf(apps.size, 1)
}

/** Control remoto táctil: mueve el cursor de la interfaz que se ve en el TV y lanza la proyección. */
class RemoteViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as OpenTvApp).container

    private val _tv = MutableStateFlow(TvUiState())
    val tvState: StateFlow<TvUiState> = _tv.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val projection: StateFlow<ProjectionState> get() = container.projection.state
    val stats: StateFlow<ProjectionStats> get() = container.projection.stats
    val profile: StateFlow<StreamProfile> get() = container.projection.profile
    val testPattern: StateFlow<Boolean> get() = container.projection.testPattern
    val shizukuStatus = MutableStateFlow(container.shizuku.status())
    val resizePending: StateFlow<Boolean> get() = container.resizer.pending
    val force169: StateFlow<Boolean> get() = container.projection.force169
    val latency: StateFlow<com.opentv.app.stream.StreamLatency> get() = container.projection.latency
    val outputTarget: StateFlow<TvTarget?> get() = container.output.target
    val iconLoader: AppIconLoader get() = container.iconLoader
    val appLaunch = AppLaunchController(opener = container.launcher, onAppMissing = { loadApps() })

    init {
        loadApps()
    }

    fun loadApps() {
        viewModelScope.launch {
            try {
                val favorites = container.favorites.load()
                val apps = withContext(Dispatchers.IO) { container.repository.loadInstalledApps() }
                val row = AppListLogic.sort(apps.filter { it.packageName in favorites }, SortMode.NAME_ASC, favorites)
                _tv.update { it.copy(apps = row).let { s -> s.copy(cursor = TvNavigation.normalize(s.rowSizes, s.cursor)) } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _tv.update { it.copy(apps = emptyList()) }
            }
        }
    }

    fun press(direction: Direction) {
        _tv.update { it.copy(cursor = TvNavigation.move(it.rowSizes, it.cursor, direction), notice = null) }
    }

    /** OK: abre la app seleccionada (en el teléfono) o detiene la proyección. */
    fun ok() {
        val state = _tv.value
        val cursor = TvNavigation.normalize(state.rowSizes, state.cursor)
        if (cursor.row == 0) {
            val app = state.apps.getOrNull(cursor.col) ?: return
            _tv.update { it.copy(notice = "Abriendo ${app.label} en el teléfono…") }
            appLaunch.onAppClick(app)
        } else {
            stop()
        }
    }

    fun back() {
        _tv.update { it.copy(cursor = TvCursor(0, 0), notice = null) }
    }

    private fun dlnaTargetOrNull(): TvTarget.DlnaTarget? {
        val target = outputTarget.value
        if (target !is TvTarget.DlnaTarget) {
            _message.value = if (target == null) {
                "Primero elegí un TV: tocá «Elegir TV» y seleccioná tu televisor (DLNA)."
            } else {
                "La proyección usa DLNA. El TV elegido es ${target.protocol}: elegí uno que aparezca como DLNA."
            }
            return null
        }
        _message.value = null
        return target
    }

    /** Espejo de la pantalla del teléfono (captura del sistema) hacia el TV elegido. */
    fun startMirror(resultCode: Int, data: android.content.Intent) {
        val target = dlnaTargetOrNull() ?: return
        viewModelScope.launch { container.projection.start(target.device, MirrorRequest(resultCode, data)) }
    }

    fun mirrorDenied() {
        _message.value = "No se concedió el permiso de captura de pantalla: sin él no se puede espejar."
    }

    fun start(activity: ComponentActivity) {
        val target = dlnaTargetOrNull() ?: return
        loadApps()
        val loader = iconLoader
        val tvUi = tvState
        viewModelScope.launch {
            container.projection.start(target.device) { display ->
                TvPresentation(activity, display) {
                    // Lambda (y no referencia a `load`): el parámetro es `suspend` y así se compila sin ambigüedad.
                    TvLauncherContent(state = tvUi, loadIcon = { app -> loader.load(app) })
                }
            }
        }
    }

    fun refreshShizuku() {
        shizukuStatus.value = container.shizuku.status()
    }

    fun requestShizuku() {
        container.shizuku.requestPermission { refreshShizuku() }
    }

    fun toggleForce169() = container.projection.toggleForce169()

    /** Botón de seguridad: devuelve el teléfono a su resolución normal. */
    fun restoreResolution() {
        viewModelScope.launch {
            _message.value = when (val r = container.resizer.restore()) {
                ResizeResult.Restored -> "Resolución restaurada."
                ResizeResult.NothingToRestore -> "No había nada que restaurar."
                is ResizeResult.Failed -> r.message
                else -> null
            }
            refreshShizuku()
        }
    }

    fun cycleProfile() = container.projection.cycleProfile()

    fun cycleLatency() = container.projection.cycleLatency()

    fun toggleTestPattern() = container.projection.toggleTestPattern()

    fun stop() {
        viewModelScope.launch { container.projection.stop() }
    }
}
