package com.opentv.app.apps

import com.opentv.app.core.OrientationPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Resultado de intentar abrir una app. */
sealed interface LaunchResult {
    data object Success : LaunchResult
    data object NotFound : LaunchResult
    data object Denied : LaunchResult
    data class Failed(val reason: String) : LaunchResult
}

/** Abre una app. La implementación real usa Android; en las pruebas se reemplaza por un doble. */
fun interface AppOpener {
    fun launch(app: InstalledApp): LaunchResult
}

data class LaunchUiState(
    /** App con orientación solo vertical que espera confirmación del usuario. */
    val pendingWarning: InstalledApp? = null,
    /** Mensaje de error del último intento, o `null`. */
    val message: String? = null,
)

/**
 * Regla única para abrir apps, compartida por «Aplicaciones» y las favoritas del inicio:
 * si la app declara orientación solo vertical se pide confirmación antes de abrirla
 * (OpenTV no puede forzarla a horizontal); los fallos se traducen a mensajes claros.
 */
class AppLaunchController(
    private val opener: AppOpener,
    private val onAppMissing: () -> Unit,
) {
    private val _state = MutableStateFlow(LaunchUiState())
    val state: StateFlow<LaunchUiState> = _state.asStateFlow()

    fun onAppClick(app: InstalledApp) {
        if (OrientationPolicy.declaresPortraitOnly(app.screenOrientation)) {
            _state.update { it.copy(pendingWarning = app, message = null) }
        } else {
            open(app)
        }
    }

    fun confirmWarning() {
        val app = _state.value.pendingWarning ?: return
        _state.update { it.copy(pendingWarning = null) }
        open(app)
    }

    fun dismissWarning() {
        _state.update { it.copy(pendingWarning = null) }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun open(app: InstalledApp) {
        when (val result = opener.launch(app)) {
            LaunchResult.Success -> _state.update { it.copy(message = null) }
            LaunchResult.NotFound -> {
                _state.update { it.copy(message = "${app.label} ya no está disponible.") }
                onAppMissing()
            }
            LaunchResult.Denied ->
                _state.update { it.copy(message = "Android no permitió abrir ${app.label}.") }
            is LaunchResult.Failed ->
                _state.update { it.copy(message = "No se pudo abrir ${app.label}: ${result.reason}") }
        }
    }
}
