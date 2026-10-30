package com.opentv.app.ui.iptv

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.opentv.app.iptv.IptvController
import com.opentv.app.iptv.IptvPreset
import com.opentv.app.iptv.IptvState
import com.opentv.app.stream.MirrorRequest
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** El canal vive en [IptvController] (no en el ViewModel): seguir emitiendo al salir de la pantalla es intencional. */
class IptvViewModel(application: Application) : AndroidViewModel(application) {
    private val controller = IptvController.get(application)

    val state: StateFlow<IptvState> get() = controller.state
    val preset: StateFlow<IptvPreset> get() = controller.preset

    fun start(resultCode: Int, data: Intent) {
        viewModelScope.launch { controller.start(MirrorRequest(resultCode, data)) }
    }

    fun cyclePreset() = controller.cyclePreset()
    fun stop() = controller.stop()
}
