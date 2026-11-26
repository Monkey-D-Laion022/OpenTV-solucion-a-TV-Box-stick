package com.opentv.app.miracast

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow

/** Une el descubrimiento de la etapa A (reutilizado sin cambios de lógica) con la prueba de negociación de la etapa B. */
class WfdDiagnosticViewModel(application: Application) : AndroidViewModel(application) {
    private val discovery = P2pDiscovery(application, viewModelScope)
    // La sesión es de la aplicación, no de la pantalla: salir de Wi‑Fi Direct ya no corta la transmisión (ver WfdSessionHolder).
    private val session = WfdSessionHolder.get(application)

    val discoveryUi: StateFlow<P2pUi> get() = discovery.ui
    val sessionUi: StateFlow<WfdUi> get() = session.ui

    fun hasPermission(): Boolean = discovery.hasPermission()
    fun permissionDenied() = discovery.permissionDenied()
    fun startDiscovery() = discovery.start()
    fun stopDiscovery() = discovery.stop()

    /** Solo se llama después de que el usuario confirmó el dispositivo en el diálogo. */
    fun startTest(peer: P2pPeer, request: com.opentv.app.stream.MirrorRequest? = null, captureAudio: Boolean = false, force1080p60: Boolean = false) {
        discovery.stop()
        session.start(peer, request, captureAudio, force1080p60)
    }

    fun cancelTest() = session.cancel()

    /** Al salir de la pantalla se detiene solo la búsqueda; la sesión en curso sigue (se corta con «Cancelar prueba»). */
    override fun onCleared() {
        discovery.stop()
        super.onCleared()
    }
}
