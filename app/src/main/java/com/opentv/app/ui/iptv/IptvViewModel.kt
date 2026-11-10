package com.opentv.app.ui.iptv

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.opentv.app.iptv.IptvAccess
import com.opentv.app.iptv.IptvController
import com.opentv.app.iptv.IptvPreset
import com.opentv.app.iptv.IptvState
import com.opentv.app.stream.MirrorRequest
import kotlinx.coroutines.Dispatchers
import com.opentv.app.media.dlna.DlnaClient
import com.opentv.app.media.dlna.DlnaSession
import com.opentv.app.media.dlna.DlnaStatus
import com.opentv.app.media.dlna.Renderer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** El canal vive en [IptvController] (no en el ViewModel): seguir emitiendo al salir de la pantalla es intencional. */
class IptvViewModel(application: Application) : AndroidViewModel(application) {
    private val controller = IptvController.get(application)

    val state: StateFlow<IptvState> get() = controller.state
    val preset: StateFlow<IptvPreset> get() = controller.preset

    val lastStop: StateFlow<String?> get() = controller.lastStop

    val access: StateFlow<IptvAccess> get() = controller.access

    fun start(resultCode: Int, data: Intent, captureAudio: Boolean) {
        viewModelScope.launch { controller.start(MirrorRequest(resultCode, data), captureAudio) }
    }

    fun cycleAccess() = controller.cycleAccess()

    val auto: StateFlow<Boolean> get() = controller.auto
    val calibration: StateFlow<List<String>> get() = controller.calibration
    val measuring: StateFlow<Boolean> get() = controller.measuring

    val soloTv: StateFlow<Boolean> get() = controller.soloTv
    val localAudio: StateFlow<String> get() = controller.localAudio
    fun toggleSoloTv() = controller.toggleSoloTv()

    val pump: StateFlow<Boolean> get() = controller.pump
    fun togglePump() = controller.togglePump()

    val governor: StateFlow<Boolean> get() = controller.governor
    fun toggleGovernor() = controller.toggleGovernor()
    val marks: StateFlow<Boolean> get() = controller.marks
    fun toggleMarks() = controller.toggleMarks()

    fun toggleAuto() = controller.toggleAuto()

    /** Mide el rendimiento real del codificador (unos 5 s por perfil) fuera del hilo principal. */
    fun measure() {
        viewModelScope.launch(Dispatchers.Default) { controller.measure() }
    }

    fun cyclePreset() = controller.cyclePreset()
    fun stop() = controller.stop()

    // ---- DLNA (6.7): enviar el canal en vivo a un televisor que sea renderizador DLNA, sin instalar nada en él ----
    private val dlnaClient = DlnaClient()
    private val _renderers = MutableStateFlow<List<Renderer>>(emptyList())
    val renderers: StateFlow<List<Renderer>> = _renderers.asStateFlow()
    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()
    private val _dlna = MutableStateFlow<DlnaStatus?>(null)
    val dlna: StateFlow<DlnaStatus?> = _dlna.asStateFlow()
    private var dlnaSession: DlnaSession? = null

    fun searchRenderers() {
        if (_searching.value) return
        _searching.value = true
        viewModelScope.launch(Dispatchers.IO) {
            _renderers.value = runCatching { dlnaClient.discover() }.getOrDefault(emptyList())
            _searching.value = false
        }
    }

    /** Envía el canal en vivo (MPEG‑TS) al televisor [r]. Requiere la emisión IPTV en marcha. */
    fun sendToRenderer(r: Renderer) {
        val live = state.value as? IptvState.Live ?: return
        dlnaSession?.stop()
        val session = DlnaSession(viewModelScope, dlnaClient, r, urlFor = { live.tsUrl }, onOffer = { controller.requestPreset(it.presetName) })
        dlnaSession = session
        viewModelScope.launch { session.status.collect { _dlna.value = it } }
        session.start()
    }

    fun stopRenderer() { dlnaSession?.stop(); dlnaSession = null }
}
