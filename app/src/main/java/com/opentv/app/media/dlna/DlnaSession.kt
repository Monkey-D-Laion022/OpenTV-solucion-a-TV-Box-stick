package com.opentv.app.media.dlna

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Lo que ve la pantalla. */
data class DlnaStatus(
    val phase: DlnaPhase = DlnaPhase.IDLE,
    val renderer: String = "",
    val offer: String = "",
    val log: List<String> = emptyList(),
)

/**
 * Una sesión DLNA hacia un televisor: lee capacidades, elige formato, envía, vigila el estado cada [pollMs] y se recupera.
 * [urlFor] da la dirección del flujo para un intento (p. ej. el canal IPTV con la resolución del intento); [onOffer] deja
 * que el dueño del flujo ajuste la resolución antes de enviar. Es independiente de `connect/` y de las tecnologías protegidas.
 */
class DlnaSession(
    private val scope: CoroutineScope,
    private val client: DlnaClient,
    private val renderer: Renderer,
    private val urlFor: (Offer) -> String,
    private val onOffer: (Offer) -> Unit = {},
    private val pollMs: Long = 2_000,
) {
    private val _status = MutableStateFlow(DlnaStatus(renderer = renderer.friendlyName))
    val status: StateFlow<DlnaStatus> = _status
    private var job: Job? = null

    private fun log(phase: DlnaPhase, msg: String, offer: String = _status.value.offer) {
        _status.value = DlnaStatus(phase, renderer.friendlyName, offer, (_status.value.log + msg).takeLast(30))
    }

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) { run() }
    }

    private suspend fun run() {
        log(DlnaPhase.READING_CAPS, "Leyendo los formatos que acepta ${renderer.friendlyName}…")
        val sink = client.readSink(renderer)
        val selection = ProfileSelector.select(sink)
        selection.reasons.forEach { log(DlnaPhase.READING_CAPS, it) }
        val sup = SessionSupervisor(selection.attempts.size)
        sup.begin()
        var action: DlnaAction = sup.onCapabilitiesRead()
        while (scope.isActive) {
            when (val a = action) {
                is DlnaAction.Send -> action = send(sup, selection.attempts, a.index, a.reason)
                is DlnaAction.Retry -> { log(DlnaPhase.RETRYING, a.reason); delay(a.afterMs); action = send(sup, selection.attempts, a.index, "reintento") }
                is DlnaAction.GiveUp -> { log(DlnaPhase.FAILED, a.reason); return }
                DlnaAction.None -> {
                    delay(pollMs)
                    val info = withContext(Dispatchers.IO) { client.transport(renderer) }
                    val state = info?.state ?: TransportState.UNKNOWN
                    action = sup.onTransport(state, System.currentTimeMillis())
                    if (sup.phase == DlnaPhase.PLAYING && _status.value.phase != DlnaPhase.PLAYING) log(DlnaPhase.PLAYING, "El televisor está reproduciendo.")
                }
            }
        }
    }

    private suspend fun send(sup: SessionSupervisor, attempts: List<Offer>, index: Int, why: String): DlnaAction {
        val offer = attempts[index]
        log(DlnaPhase.SENDING, "Enviando ${offer.label} ($why). ${offer.note}.", offer.label)
        onOffer(offer)
        val res = withContext(Dispatchers.IO) { client.setAndPlay(renderer, urlFor(offer), "OpenTV", offer.protocolInfo) }
        if (!res.ok) return sup.onCommandFailed(res.failure ?: "sin detalle")
        sup.onSent(System.currentTimeMillis())
        return DlnaAction.None
    }

    fun stop() {
        job?.cancel(); job = null
        scope.launch(Dispatchers.IO) { client.stop(renderer) }
        log(DlnaPhase.STOPPED, "Sesión detenida.")
    }
}
