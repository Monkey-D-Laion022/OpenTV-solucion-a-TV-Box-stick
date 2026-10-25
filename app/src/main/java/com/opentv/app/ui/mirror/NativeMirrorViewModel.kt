package com.opentv.app.ui.mirror

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.opentv.app.OpenTvApp
import com.opentv.app.display.NativeMirrorState
import com.opentv.app.display.ResizeResult
import com.opentv.app.display.ShizukuShell
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Espejo nativo del sistema con la resolución del teléfono forzada a 16:9 (Shizuku). */
class NativeMirrorViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as OpenTvApp).container

    val state: StateFlow<NativeMirrorState> get() = container.nativeMirror.state
    val resizePending: StateFlow<Boolean> get() = container.resizer.pending

    val shizuku = MutableStateFlow(container.shizuku.status())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun refresh() {
        shizuku.value = container.shizuku.status()
    }

    fun requestPermission() {
        container.shizuku.requestPermission { refresh() }
    }

    /** Soporte de transmisión nativa de este teléfono (Miracast / Smart View), para avisar al usuario. */
    val castAvailable: Boolean = container.systemCast.isAvailable()
    val wifiDirect: Boolean = application.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_WIFI_DIRECT)

    /** Paso 1: fuerza 16:9 con Shizuku (solo tamaño). No abre nada más. */
    fun force() {
        refresh()
        val status = shizuku.value
        if (status != ShizukuShell.Status.READY) {
            _message.value = status.message
            return
        }
        _message.value = null
        container.nativeMirror.forceResolution()
    }

    /** Paso 2: abre la transmisión del sistema desde la Activity visible. */
    fun openPanel(from: android.content.Context) {
        container.nativeMirror.openPanel(com.opentv.app.display.CastOpener { container.systemCast.open(from) })
    }

    fun stop() = container.nativeMirror.stop()

    /** Botón de seguridad: devuelve el teléfono a su resolución normal. */
    fun restore() {
        viewModelScope.launch {
            _message.value = when (val r = container.resizer.restore()) {
                ResizeResult.Restored -> "Resolución restaurada."
                ResizeResult.NothingToRestore -> "No había nada que restaurar."
                is ResizeResult.Failed -> r.message
                else -> null
            }
            refresh()
        }
    }
}
