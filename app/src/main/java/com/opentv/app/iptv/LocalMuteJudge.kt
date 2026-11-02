package com.opentv.app.iptv

/**
 * Decide, midiendo el audio CAPTURADO, si bajar el volumen del teléfono (para que el sonido salga solo por el televisor)
 * deja intacta la captura. Android no documenta si el volumen local afecta a AudioPlaybackCapture y puede variar según
 * teléfono, app y salida (parlante, auricular, Bluetooth), así que no se asume: se prueba y se revierte si la captura se apaga.
 *
 * Una vez por segundo se le pasa el nivel pico (dBFS) de lo capturado en ese segundo. Lógica pura, sin Android.
 *  1. [Phase.WAIT_SOUND]: espera a oír sonido real durante [BASELINE_SECONDS] segundos (nada que comparar sin sonido).
 *  2. Pide [Action.MUTE] y pasa a [Phase.VERIFYING]: si sigue llegando sonido capturado → [Phase.CONFIRMED].
 *  3. Si tras silenciar el nivel capturado cae a silencio [FAIL_SECONDS] segundos seguidos → [Action.RESTORE] y
 *     [Phase.ATTENUATED] (en ese teléfono el volumen local también apaga lo capturado, o el contenido se detuvo: se
 *     vuelve al audio normal y se dice). Sin conclusión en [VERIFY_TIMEOUT_SECONDS] → también se restaura.
 */
class LocalMuteJudge {
    enum class Phase(val label: String) {
        OFF("Audio normal (suena en el teléfono y en el televisor)."),
        WAIT_SOUND("Esperando sonido para comprobar si se puede silenciar el teléfono…"),
        VERIFYING("Teléfono silenciado; comprobando que el sonido siga llegando al televisor…"),
        CONFIRMED("Audio solo en TV: el teléfono está silenciado y la captura sigue recibiendo sonido (medido en este teléfono; no confirma lo que oye el televisor)."),
        ATTENUATED("No se pudo confirmar: al silenciar el teléfono la captura también quedó en silencio (o el contenido se detuvo). Se restauró el volumen; el audio suena en ambos."),
    }

    enum class Action { NONE, MUTE, RESTORE }

    var phase: Phase = Phase.OFF
        private set

    private var soundSeconds = 0
    private var baselinePeak = SILENCE_DB
    private var verifySeconds = 0
    private var silentSeconds = 0

    fun arm() {
        phase = Phase.WAIT_SOUND
        soundSeconds = 0; baselinePeak = SILENCE_DB; verifySeconds = 0; silentSeconds = 0
    }

    /** Devuelve [Action.RESTORE] si hay algo silenciado que deshacer (el usuario apagó el modo). */
    fun disarm(): Action {
        val wasMuted = phase == Phase.VERIFYING || phase == Phase.CONFIRMED
        phase = Phase.OFF
        return if (wasMuted) Action.RESTORE else Action.NONE
    }

    /** [peakDb]: nivel pico de lo capturado durante el último segundo (−96 = nada). */
    fun onSecond(peakDb: Double): Action {
        when (phase) {
            Phase.OFF, Phase.CONFIRMED, Phase.ATTENUATED -> return Action.NONE
            Phase.WAIT_SOUND -> {
                if (peakDb > AUDIBLE_DB) {
                    soundSeconds++
                    if (peakDb > baselinePeak) baselinePeak = peakDb
                } else soundSeconds = 0
                if (soundSeconds >= BASELINE_SECONDS) {
                    phase = Phase.VERIFYING
                    verifySeconds = 0; silentSeconds = 0
                    return Action.MUTE
                }
                return Action.NONE
            }
            Phase.VERIFYING -> {
                verifySeconds++
                if (peakDb > AUDIBLE_DB && peakDb > baselinePeak - MAX_DROP_DB) {
                    phase = Phase.CONFIRMED
                    return Action.NONE
                }
                silentSeconds = if (peakDb <= SILENCE_DB + 26) silentSeconds + 1 else 0
                if (silentSeconds >= FAIL_SECONDS || verifySeconds >= VERIFY_TIMEOUT_SECONDS) {
                    phase = Phase.ATTENUATED
                    return Action.RESTORE
                }
                return Action.NONE
            }
        }
    }

    companion object {
        const val SILENCE_DB = -96.0
        /** Por encima de este nivel (dBFS) se considera sonido real. */
        const val AUDIBLE_DB = -55.0
        const val BASELINE_SECONDS = 2
        const val FAIL_SECONDS = 3
        const val VERIFY_TIMEOUT_SECONDS = 8
        /** Caída máxima respecto del nivel previo para dar la captura por intacta. */
        const val MAX_DROP_DB = 25.0
    }
}
