package com.opentv.app.miracast

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow

/** Une el descubrimiento de la etapa A (reutilizado sin cambios de lógica) con la prueba de negociación de la etapa B. */
class WfdDiagnosticViewModel(application: Application) : AndroidViewModel(application) {
    private val discovery = P2pDiscovery(application, viewModelScope)
    private val session = WfdDiagnosticSession(application, viewModelScope)

    val discoveryUi: StateFlow<P2pUi> get() = discovery.ui
    val sessionUi: StateFlow<WfdUi> get() = session.ui

    fun hasPermission(): Boolean = discovery.hasPermission()
    fun permissionDenied() = discovery.permissionDenied()
    fun startDiscovery() = discovery.start()
    fun stopDiscovery() = discovery.stop()

    /** Solo se llama después de que el usuario confirmó el dispositivo en el diálogo. */
    fun startTest(peer: P2pPeer) {
        discovery.stop()
        session.start(peer)
    }

    fun cancelTest() = session.cancel()

    /** Al salir de la pantalla se corta todo y se libera. */
    override fun onCleared() {
        session.cancel()
        discovery.stop()
        super.onCleared()
    }
}
