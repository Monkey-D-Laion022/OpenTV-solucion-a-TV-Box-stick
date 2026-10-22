package com.opentv.app.connect

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Resultado de una operación de salida, con el texto que se le muestra a la persona. */
data class OutputResult(val ok: Boolean, val message: String)

/**
 * El TV de salida elegido. Una vez elegido, lo que se reproduzca en OpenTV se envía a ese TV en
 * lugar de reproducirse en el teléfono. Con Cast, elegir abre la sesión en el momento; DLNA no
 * tiene sesión, así que queda como destino y se conecta al enviar.
 */
class OutputController(
    private val cast: CastSender,
    private val dlna: DlnaController,
) {
    private val _target = MutableStateFlow<TvTarget?>(null)
    val target: StateFlow<TvTarget?> = _target.asStateFlow()

    suspend fun select(target: TvTarget): OutputResult = when (target) {
        is TvTarget.CastTarget -> when (val r = cast.connect(target.device.id)) {
            is CastSendResult.Ok -> {
                _target.value = target
                OutputResult(true, "Conectado a ${r.deviceName}. Lo que reproduzcas se verá en ese TV.")
            }
            is CastSendResult.Failed -> OutputResult(false, r.reason)
        }
        is TvTarget.DlnaTarget -> {
            _target.value = target
            OutputResult(
                true,
                "${target.name} elegido. DLNA no abre una sesión: se conecta cuando reproduzcas algo.",
            )
        }
    }

    /** Envía [url] al TV elegido. Sin TV elegido no hace nada y lo dice. */
    suspend fun send(url: String): OutputResult = when (val t = _target.value) {
        null -> OutputResult(false, "No hay un TV elegido.")
        is TvTarget.CastTarget -> when (val r = cast.play(t.device.id, url)) {
            is CastSendResult.Ok -> OutputResult(true, "Reproduciendo en ${r.deviceName}.")
            is CastSendResult.Failed -> OutputResult(false, r.reason)
        }
        is TvTarget.DlnaTarget -> {
            val result = dlna.play(t.device, url)
            OutputResult(result == DlnaResult.Ok, DlnaMessages.describe(t.name, DlnaSoap.ACTION_PLAY, result))
        }
    }

    /** Detiene la reproducción en el TV elegido, sin dejar de tenerlo elegido. */
    suspend fun stopPlayback(): OutputResult = when (val t = _target.value) {
        null -> OutputResult(false, "No hay un TV elegido.")
        is TvTarget.CastTarget -> {
            cast.stop()
            _target.value = null
            OutputResult(true, "Se cerró la sesión con ${t.name}.")
        }
        is TvTarget.DlnaTarget -> {
            val result = dlna.stop(t.device)
            OutputResult(result == DlnaResult.Ok, DlnaMessages.describe(t.name, DlnaSoap.ACTION_STOP, result))
        }
    }

    /** Deja de usar el TV: cierra Cast si estaba abierto y olvida el destino. */
    fun clear() {
        if (_target.value is TvTarget.CastTarget) cast.stop()
        _target.value = null
    }
}
