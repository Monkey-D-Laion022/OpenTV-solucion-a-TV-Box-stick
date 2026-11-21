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
    /** Intento en curso (1…[attempts]), cuántos hay y por qué transporte va; antes el diagnóstico solo mostraba un texto genérico. */
    val attempt: Int = 0,
    val attempts: Int = 0,
    val transport: String = "",
    val recoveries: Int = 0,
    val lastReason: String = "",
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
    private val streamAlive: () -> Boolean = { false },
    /** Clave del formato que ya funcionó en este televisor (se prueba primero). */
    private val rememberedKey: String? = null,
    /** Se llama cuando el televisor pasa a reproducir con una oferta: quien llama la recuerda. */
    private val onPlaying: (Offer) -> Unit = {},
    /** Perfil con el que arrancó el canal; se prueba primero cuando el televisor no declara formatos. */
    private val userProfile: ProfileSelector.UserProfile? = null,
    /** Claves de formatos que este televisor ya rechazó antes (se omiten) y aviso cuando rechaza uno nuevo. */
    private val rejectedKeys: Set<String> = emptySet(),
    private val onRejected: (Offer) -> Unit = {},
) {
    private val _status = MutableStateFlow(DlnaStatus(renderer = renderer.friendlyName))
    val status: StateFlow<DlnaStatus> = _status
    private var job: Job? = null

    private fun log(phase: DlnaPhase, msg: String, offer: String = _status.value.offer) {
        _status.value = _status.value.copy(phase = phase, offer = offer, log = (_status.value.log + msg).takeLast(30))
    }
    private var currentOffer: Offer? = null
    private var notified = false

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) { run() }
    }

    private suspend fun run() {
        log(DlnaPhase.READING_CAPS, "Leyendo los formatos que acepta ${renderer.friendlyName}…")
        val sink = client.readSink(renderer)
        val selection = ProfileSelector.dropRejected(ProfileSelector.preferRemembered(ProfileSelector.select(sink, userProfile), rememberedKey), rejectedKeys)
        selection.reasons.forEach { log(DlnaPhase.READING_CAPS, it) }
        val sup = SessionSupervisor(selection.attempts.size, retriesFor = { i -> selection.attempts.getOrNull(i)?.let { if (!it.declaredByRenderer && it.hls && it.fps > 30) 0 else 2 } ?: 2 })
        _status.value = _status.value.copy(attempts = selection.attempts.size)
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
                    val now = System.currentTimeMillis()
                    action = if (info == null) sup.onPollFailed(now, streamAlive()) else sup.onTransport(info.state, now)
                    _status.value = _status.value.copy(recoveries = sup.recoveries, lastReason = sup.lastReason)
                    if (sup.phase == DlnaPhase.PLAYING && _status.value.phase != DlnaPhase.PLAYING) log(DlnaPhase.PLAYING, "El televisor está reproduciendo.")
                    if (sup.phase == DlnaPhase.PLAYING && !notified) { notified = true; currentOffer?.let { onPlaying(it) } }
                    if (sup.phase == DlnaPhase.PLAYING && sup.unstable(now) && _status.value.log.lastOrNull()?.startsWith("Inestable") != true)
                        log(DlnaPhase.PLAYING, "Inestable: ${sup.recoveries} recuperaciones en pocos minutos (última causa: ${sup.lastReason}). Reproduce, pero la sesión no es fiable.")
                }
            }
        }
    }

    private suspend fun send(sup: SessionSupervisor, attempts: List<Offer>, index: Int, why: String): DlnaAction {
        val offer = attempts[index]
        currentOffer = offer; notified = false
        _status.value = _status.value.copy(attempt = index + 1, attempts = attempts.size, transport = if (offer.hls) "HLS (lista m3u8)" else "MPEG‑TS continuo")
        log(DlnaPhase.SENDING, "Enviando ${offer.label} ($why). ${offer.note}.", offer.label)
        onOffer(offer)
        val res = withContext(Dispatchers.IO) { client.setAndPlay(renderer, urlFor(offer), "OpenTV", offer.protocolInfo) }
        if (!res.ok && (res.failure.orEmpty().let { it.contains("704") || it.contains("not supported", true) })) onRejected(offer)
        if (!res.ok) return sup.onCommandFailed(res.failure ?: "sin detalle", System.currentTimeMillis())
        sup.onSent(System.currentTimeMillis())
        return DlnaAction.None
    }

    fun stop() {
        job?.cancel(); job = null
        scope.launch(Dispatchers.IO) { client.stop(renderer) }
        log(DlnaPhase.STOPPED, "Sesión detenida.")
    }
}
