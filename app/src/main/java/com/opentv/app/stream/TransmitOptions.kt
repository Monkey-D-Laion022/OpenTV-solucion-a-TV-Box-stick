package com.opentv.app.stream

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Opciones de transmisión que se eligen en la central y valen para el próximo espejo. Lógica pura. */
class TransmitOptions {
    private val _lockRotation = MutableStateFlow(true)

    /** Al forzar 16:9, fijar la rotación en horizontal (como en la versión que andaba estable). Por defecto, fija. */
    val lockRotation: StateFlow<Boolean> = _lockRotation.asStateFlow()

    private val _muteLocal = MutableStateFlow(false)

    /** Silenciar el teléfono mientras dura el espejo DLNA (experimental). */
    val muteLocal: StateFlow<Boolean> = _muteLocal.asStateFlow()

    private val _rate = MutableStateFlow(StreamRate.FPS30)
    val rate: StateFlow<StreamRate> = _rate.asStateFlow()

    fun toggleLockRotation() { _lockRotation.value = !_lockRotation.value }
    fun toggleMuteLocal() { _muteLocal.value = !_muteLocal.value }
    fun cycleRate() { _rate.value = _rate.value.next() }
}
