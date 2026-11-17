package com.opentv.app.iptv

import android.content.Context
import com.opentv.app.media.dlna.DlnaClient
import com.opentv.app.media.dlna.DlnaSession
import com.opentv.app.media.dlna.DlnaStatus
import com.opentv.app.media.dlna.Renderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Dueño de la sesión DLNA del canal IPTV. Antes vivía en el viewModelScope de la pantalla IPTV: al salir de esa pantalla o al destruirse la
 * Activity (por ejemplo, al abrir otra app pesada) la supervisión y la reconexión morían en silencio aunque el canal siguiera emitiendo.
 * Ahora vive en un ámbito de proceso, igual que [IptvController], y se detiene sola cuando el canal deja de estar en vivo.
 */
object IptvDlna {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val client = DlnaClient()
    private val _status = MutableStateFlow<DlnaStatus?>(null)
    val status: StateFlow<DlnaStatus?> = _status.asStateFlow()
    private var session: DlnaSession? = null
    private var statusJob: Job? = null
    private var watchJob: Job? = null

    @Synchronized
    fun send(context: Context, renderer: Renderer) {
        val controller = IptvController.get(context.applicationContext)
        val live = controller.state.value as? IptvState.Live ?: return
        stop()
        val s = DlnaSession(
            scope, client, renderer,
            urlFor = { offer -> if (offer.hls) live.hlsUrl else live.tsUrl },
            onOffer = { controller.requestPreset(it.presetName) },
            streamAlive = { ((controller.state.value as? IptvState.Live)?.clients ?: 0) > 0 },
        )
        session = s
        statusJob = scope.launch { s.status.collect { _status.value = it } }
        // Si el canal se detiene (usuario, servicio perdido), la sesión DLNA no tiene nada que supervisar.
        watchJob = scope.launch { controller.state.collect { if (it !is IptvState.Live) stop() } }
        s.start()
    }

    @Synchronized
    fun stop() {
        session?.stop(); session = null
        statusJob?.cancel(); statusJob = null
        watchJob?.cancel(); watchJob = null
    }
}
