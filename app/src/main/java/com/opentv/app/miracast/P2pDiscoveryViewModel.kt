package com.opentv.app.miracast

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow

class P2pDiscoveryViewModel(application: Application) : AndroidViewModel(application) {
    private val discovery = P2pDiscovery(application, viewModelScope)

    val ui: StateFlow<P2pUi> get() = discovery.ui

    fun hasPermission(): Boolean = discovery.hasPermission()
    fun start() = discovery.start()
    fun stop() = discovery.stop()
    fun permissionDenied() = discovery.permissionDenied()

    /** Al salir de la pantalla se corta la búsqueda y se libera todo. */
    override fun onCleared() {
        discovery.stop()
        super.onCleared()
    }
}
