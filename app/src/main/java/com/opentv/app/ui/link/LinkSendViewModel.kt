package com.opentv.app.ui.link

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.opentv.app.iptv.IptvPreset
import com.opentv.app.link.LinkSenderController
import com.opentv.app.link.LinkSenderState
import com.opentv.app.stream.MirrorRequest
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** El emisor vive en [LinkSenderController]: seguir transmitiendo al salir de la pantalla es intencional. */
class LinkSendViewModel(application: Application) : AndroidViewModel(application) {
    val controller = LinkSenderController.get(application)
    val state: StateFlow<LinkSenderState> get() = controller.state
    val preset: StateFlow<IptvPreset> get() = controller.preset

    /** Receptor elegido (host, puerto) antes de pedir el permiso de captura; `null` = modo avanzado (esperar receptores). */
    var pendingTarget: Pair<String, Int>? = null

    fun start(resultCode: Int, data: Intent, captureAudio: Boolean) {
        val target = pendingTarget
        viewModelScope.launch { controller.start(MirrorRequest(resultCode, data), captureAudio, target) }
    }
}
