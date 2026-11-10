package com.opentv.app.media.dlna

/** Fases de una sesión DLNA hacia un televisor. */
enum class DlnaPhase { IDLE, READING_CAPS, SENDING, WAITING_PLAY, PLAYING, STALLED, RETRYING, FAILED, STOPPED }

/** Lo que el supervisor pide hacer. */
sealed interface DlnaAction {
    object None : DlnaAction
    /** Enviar el intento [index] (SetAVTransportURI + Play). */
    data class Send(val index: Int, val reason: String) : DlnaAction
    /** Esperar [ms] y volver a enviar el mismo intento. */
    data class Retry(val index: Int, val afterMs: Long, val reason: String) : DlnaAction
    data class GiveUp(val reason: String) : DlnaAction
}

/**
 * Máquina de estados de una sesión DLNA. Lógica pura: el tiempo y el estado del televisor se pasan, no se leen.
 *
 * - Tras enviar, espera hasta [playTimeoutMs] a ver PLAYING (TRANSITIONING cuenta como progreso).
 * - Si no llega: reintenta el mismo intento hasta [maxRetries] veces con espera creciente; luego pasa al siguiente (perfil más simple).
 * - Si se reproducía y el televisor pasa a STOPPED/NO_MEDIA: es un corte; reintenta igual.
 * - Agotados todos los intentos: FAILED con el motivo, para mostrarlo.
 */
class SessionSupervisor(
    private val attemptCount: Int,
    private val playTimeoutMs: Long = 15_000,
    private val maxRetries: Int = 2,
    private val backoffMs: List<Long> = listOf(2_000, 4_000, 8_000),
) {
    var phase: DlnaPhase = DlnaPhase.IDLE; private set
    var attemptIndex: Int = 0; private set
    var retries: Int = 0; private set
    var lastReason: String = ""; private set
    var playedMs: Long = 0; private set
    private var sentAt = 0L
    private var unreachable = 0
    private val recoveryTimes = ArrayDeque<Long>()
    /** Recuperaciones totales de la sesión (cada vez que hubo que reintentar o cambiar de formato). */
    var recoveries: Int = 0; private set
    private var playingSince = -1L

    init { require(attemptCount >= 1) { "Hace falta al menos un intento" } }

    fun onCapabilitiesRead(): DlnaAction { phase = DlnaPhase.SENDING; return DlnaAction.Send(attemptIndex, "capacidades leídas") }

    /**
     * `true` si hubo [UNSTABLE_RECOVERIES] o más recuperaciones en los últimos [UNSTABLE_WINDOW_MS]: que ahora reproduzca no
     * significa que la sesión sea estable. La pantalla debe mostrarlo aunque el estado sea PLAYING.
     */
    fun unstable(nowMs: Long): Boolean = recoveryTimes.count { nowMs - it <= UNSTABLE_WINDOW_MS } >= UNSTABLE_RECOVERIES

    /** El televisor no respondió a la consulta de estado (tiempo agotado, red caída). Varios seguidos durante la reproducción = corte. */
    fun onPollFailed(nowMs: Long): DlnaAction {
        unreachable++
        if (phase == DlnaPhase.PLAYING && unreachable >= UNREACHABLE_LIMIT) { phase = DlnaPhase.STALLED; return failAttempt("el televisor dejó de responder ($unreachable consultas seguidas sin respuesta)", nowMs) }
        if (phase == DlnaPhase.WAITING_PLAY && nowMs - sentAt >= playTimeoutMs) return failAttempt("el televisor no responde y no llegó a reproducir", nowMs)
        return DlnaAction.None
    }

    fun begin() { phase = DlnaPhase.READING_CAPS }

    /** Se envió el intento actual a [nowMs]. */
    fun onSent(nowMs: Long) { phase = DlnaPhase.WAITING_PLAY; sentAt = nowMs; playingSince = -1L }

    /** Una orden SOAP fue rechazada o no llegó. */
    fun onCommandFailed(detail: String, nowMs: Long = sentAt): DlnaAction = failAttempt("el televisor rechazó la orden: $detail", nowMs)

    fun onTransport(state: TransportState, nowMs: Long): DlnaAction {
        unreachable = 0
        when (phase) {
            DlnaPhase.WAITING_PLAY, DlnaPhase.STALLED -> when (state) {
                TransportState.PLAYING -> { phase = DlnaPhase.PLAYING; playingSince = nowMs; retries = 0; return DlnaAction.None }
                TransportState.TRANSITIONING -> { sentAt = maxOf(sentAt, nowMs - playTimeoutMs / 2); return DlnaAction.None }
                TransportState.ERROR -> return failAttempt("el televisor informó ERROR", nowMs)
                else -> if (nowMs - sentAt >= playTimeoutMs) return failAttempt("no llegó a reproducir en ${playTimeoutMs / 1000} s (estado ${state.name})", nowMs)
            }
            DlnaPhase.PLAYING -> when (state) {
                TransportState.PLAYING, TransportState.TRANSITIONING, TransportState.PAUSED -> if (playingSince >= 0) playedMs = nowMs - playingSince
                TransportState.STOPPED, TransportState.NO_MEDIA, TransportState.ERROR -> { phase = DlnaPhase.STALLED; return failAttempt("la reproducción se cortó (estado ${state.name})", nowMs) }
                TransportState.UNKNOWN -> {}
            }
            else -> {}
        }
        return DlnaAction.None
    }

    private fun failAttempt(reason: String, nowMs: Long): DlnaAction {
        lastReason = reason
        recoveries++; recoveryTimes.addLast(nowMs); while (recoveryTimes.size > 12) recoveryTimes.removeFirst()
        unreachable = 0
        if (retries < maxRetries) {
            val wait = backoffMs.getOrElse(retries) { backoffMs.last() }
            retries++
            phase = DlnaPhase.RETRYING
            return DlnaAction.Retry(attemptIndex, wait, "$reason; reintento $retries de $maxRetries")
        }
        if (attemptIndex + 1 < attemptCount) {
            attemptIndex++; retries = 0
            phase = DlnaPhase.SENDING
            return DlnaAction.Send(attemptIndex, "$reason; se prueba el siguiente formato")
        }
        phase = DlnaPhase.FAILED
        return DlnaAction.GiveUp("$reason; no quedan formatos por probar")
    }

    fun onStopped() { phase = DlnaPhase.STOPPED }

    companion object {
        const val UNSTABLE_RECOVERIES = 3
        const val UNSTABLE_WINDOW_MS = 5 * 60_000L
        const val UNREACHABLE_LIMIT = 3
    }
}
