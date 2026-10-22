package com.opentv.app.connect

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.opentv.app.OpenTvApp
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DlnaUiState(
    val scanning: Boolean = false,
    /** `true` si ya se hizo al menos una búsqueda completa. */
    val scanned: Boolean = false,
    val devices: List<UpnpDevice> = emptyList(),
    val error: String? = null,
)

data class ConnectUiState(
    val statuses: List<MethodStatus> = emptyList(),
    val externalDisplay: ExternalDisplay? = null,
    val hasCastSettingsScreen: Boolean = false,
    val manufacturer: String = "",
    val onLocalNetwork: Boolean = true,
    val castDevices: List<CastDevice> = emptyList(),
    val castError: String? = null,
    val dlna: DlnaUiState = DlnaUiState(),
    /** Hay una orden DLNA en curso (enviar o detener). */
    val busy: Boolean = false,
    val message: String? = null,
)

/**
 * Estado de «Conectar TV». Las búsquedas (pantalla externa, Cast, DLNA) solo viven mientras la
 * pantalla está visible: [onScreenStarted] y [onScreenStopped] las inician y las cortan.
 */
class ConnectViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as OpenTvApp).container

    private val _state = MutableStateFlow(ConnectUiState())
    val state: StateFlow<ConnectUiState> = _state.asStateFlow()

    private var displayJob: Job? = null
    private var castJob: Job? = null
    private var scanJob: Job? = null
    private var commandJob: Job? = null

    fun onScreenStarted() {
        refreshCapabilities(container.displayMonitor.current())
        displayJob?.cancel()
        displayJob = viewModelScope.launch {
            container.displayMonitor.observe().collect { display -> refreshCapabilities(display) }
        }
    }

    fun onScreenStopped() {
        displayJob?.cancel()
        displayJob = null
        castJob?.cancel()
        castJob = null
        scanJob?.cancel()
        scanJob = null
        _state.update { it.copy(dlna = it.dlna.copy(scanning = false)) }
    }

    private fun refreshCapabilities(display: ExternalDisplay?) {
        val detector = container.capabilityDetector
        val raw = detector.detect(display)
        _state.update {
            it.copy(
                statuses = CapabilityEvaluator.evaluate(raw),
                externalDisplay = display,
                hasCastSettingsScreen = raw.hasCastSettingsScreen,
                manufacturer = detector.manufacturer(),
                onLocalNetwork = raw.onLocalNetwork,
            )
        }
        syncCastDiscovery(wanted = raw.playServicesAvailable && raw.onLocalNetwork)
    }

    private fun syncCastDiscovery(wanted: Boolean) {
        if (!wanted) {
            castJob?.cancel()
            castJob = null
            _state.update { it.copy(castDevices = emptyList(), castError = null) }
            return
        }
        if (castJob?.isActive == true) return
        castJob = viewModelScope.launch {
            container.castDiscovery.devices()
                .catch {
                    _state.update { s ->
                        s.copy(castError = "No se pudo iniciar la búsqueda de dispositivos Cast.")
                    }
                }
                .collect { devices ->
                    _state.update { it.copy(castDevices = devices, castError = null) }
                }
        }
    }

    fun scanDlna() {
        if (scanJob?.isActive == true) return
        if (!container.capabilityDetector.isOnLocalNetwork()) {
            _state.update {
                it.copy(
                    dlna = DlnaUiState(
                        error = "Conectá el teléfono a la misma red Wi‑Fi que el TV y volvé a buscar.",
                    ),
                )
            }
            return
        }
        scanJob = viewModelScope.launch {
            _state.update { it.copy(dlna = it.dlna.copy(scanning = true, error = null)) }
            val result = try {
                DlnaUiState(scanned = true, devices = container.dlna.discover())
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                DlnaUiState(error = "No se pudo usar la red local: ${e.message ?: "error de red"}")
            } catch (e: Exception) {
                DlnaUiState(error = "La búsqueda falló (${e.javaClass.simpleName}).")
            }
            _state.update { it.copy(dlna = result) }
        }
    }

    /** Envía [rawUrl] al TV. El enlace se valida antes: nunca se envía algo que no pasó la política. */
    fun sendLink(device: UpnpDevice, rawUrl: String) {
        if (commandJob?.isActive == true) return
        when (val check = MediaUrlPolicy.check(rawUrl)) {
            is MediaUrlCheck.Invalid -> _state.update { it.copy(message = check.reason) }
            is MediaUrlCheck.Valid ->
                runCommand(device, DlnaSoap.ACTION_PLAY) { container.dlna.play(device, check.url) }
        }
    }

    fun stopPlayback(device: UpnpDevice) {
        if (commandJob?.isActive == true) return
        runCommand(device, DlnaSoap.ACTION_STOP) { container.dlna.stop(device) }
    }

    private fun runCommand(device: UpnpDevice, action: String, block: suspend () -> DlnaResult) {
        commandJob = viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            val result = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DlnaResult.Unreachable(e.javaClass.simpleName)
            }
            _state.update {
                it.copy(busy = false, message = DlnaMessages.describe(device.friendlyName, action, result))
            }
        }
    }

    /** Abre la pantalla de transmisión del sistema; sirve tanto para conectar como para desconectar. */
    fun openSystemCast() {
        val opened = container.systemCast.open()
        _state.update {
            it.copy(
                message = if (opened) {
                    null
                } else {
                    "Este equipo no permitió abrir la pantalla de transmisión. Buscá «Transmitir», " +
                        "«Smart View» o «Proyección» en los Ajustes de Android."
                },
            )
        }
    }
}
