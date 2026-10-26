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
    private var lastDlna: UpnpDevice? = null

    /** TV de salida elegido (lo que se reproduzca se envía ahí). */
    val outputTarget: StateFlow<TvTarget?> get() = container.output.target

    /** Toque en un TV de la lista: lo elige como salida y, con Cast, abre la sesión ya mismo. */
    fun selectTarget(target: TvTarget) {
        if (commandJob?.isActive == true) return
        commandJob = viewModelScope.launch {
            _state.update { it.copy(busy = true, message = "Conectando con ${target.name}…") }
            val result = try {
                container.output.select(target)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                OutputResult(false, "Falló la conexión (${e.javaClass.simpleName}).")
            }
            _state.update { it.copy(busy = false, message = result.message) }
        }
    }

    /** Deja de transmitir: cierra la sesión Cast y olvida el TV elegido. */
    fun clearTarget() {
        container.output.clear()
        _state.update { it.copy(message = "Se dejó de transmitir. La reproducción vuelve al teléfono.") }
    }

    /** Enlace compartido con el reproductor. La pantalla guarda su propia copia para escribir sin saltos. */
    val initialLink: String get() = container.sharedLink.value

    fun setLink(value: String) {
        container.sharedLink.value = value
    }

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
            is MediaUrlCheck.Valid -> {
                lastDlna = device
                runCommand(device, DlnaSoap.ACTION_PLAY) { container.dlna.play(device, check.url) }
            }
        }
    }

    /** Conecta con el dispositivo Cast y le envía [rawUrl]. */
    fun sendToCast(device: CastDevice, rawUrl: String) {
        if (commandJob?.isActive == true) return
        commandJob = viewModelScope.launch {
            _state.update { it.copy(busy = true, message = "Conectando con ${device.name}…") }
            val result = try {
                container.castSender.play(device.id, rawUrl)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                CastSendResult.Failed("Falló el envío (${e.javaClass.simpleName}).")
            }
            val text = when (result) {
                is CastSendResult.Ok -> "Enviado a ${result.deviceName}. El TV reproduce el video por su cuenta."
                is CastSendResult.Failed -> result.reason
            }
            _state.update { it.copy(busy = false, message = text) }
        }
    }

    /** Envía [rawUrl] al TV elegido en el selector, por el método que corresponda. */
    fun send(target: TvTarget, rawUrl: String) {
        when (target) {
            is TvTarget.CastTarget -> sendToCast(target.device, rawUrl)
            is TvTarget.DlnaTarget -> sendLink(target.device, rawUrl)
        }
    }

    /** Corta lo que se envió: cierra la sesión Cast y manda «Stop» al último TV DLNA usado. */
    fun stopAll() {
        container.castSender.stop()
        val dlnaTarget = lastDlna
        if (dlnaTarget != null) {
            stopPlayback(dlnaTarget)
        } else {
            _state.update { it.copy(message = "Se cerró la sesión de Cast.") }
        }
    }

    fun disconnectCast() {
        container.castSender.stop()
        _state.update { it.copy(message = "Se cerró la sesión de Cast.") }
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

    /** Opciones de la central (rotación, silencio del teléfono, cuadros por segundo). */
    val options: com.opentv.app.stream.TransmitOptions get() = container.transmitOptions

    // --- Espejo DLNA (pantalla + sonido) desde la central de conexiones ---

    private var mirrorTarget: UpnpDevice? = null
    private var localChannel = false

    val projection: StateFlow<com.opentv.app.stream.ProjectionState> get() = container.projection.state

    /** Guarda el TV al que se espejará, antes de pedir los permisos de captura. */
    fun prepareMirror(device: UpnpDevice) {
        mirrorTarget = device
        localChannel = false
    }

    /** Prepara el canal local (sin TV elegido): el usuario abre la dirección en VLC o en una app de IPTV. */
    fun prepareLocalChannel() {
        mirrorTarget = null
        localChannel = true
    }

    fun startDlnaMirror(resultCode: Int, data: android.content.Intent) {
        if (localChannel) {
            localChannel = false
            viewModelScope.launch { container.projection.start(null, com.opentv.app.stream.MirrorRequest(resultCode, data)) }
            return
        }
        val device = mirrorTarget ?: return
        viewModelScope.launch {
            container.output.select(TvTarget.DlnaTarget(device))
            container.projection.start(device, com.opentv.app.stream.MirrorRequest(resultCode, data))
        }
    }

    fun mirrorDenied() {
        _state.update { it.copy(message = "No se concedió el permiso de captura de pantalla: sin él no se puede espejar.") }
    }

    fun stopProjection() {
        viewModelScope.launch { container.projection.stop() }
    }

    /** Abre la pantalla de transmisión del sistema; sirve tanto para conectar como para desconectar. */
    fun openSystemCast(from: android.content.Context? = null) {
        val opened = container.systemCast.open(from)
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
