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
import kotlinx.coroutines.flow.StateFlow
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

    fun toggleAuto() = controller.toggleAuto()

    /** Mide el rendimiento real del codificador (unos 5 s por perfil) fuera del hilo principal. */
    fun measure() {
        viewModelScope.launch(Dispatchers.Default) { controller.measure() }
    }

    fun cyclePreset() = controller.cyclePreset()
    fun stop() = controller.stop()
}
