package com.opentv.app.iptv

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * «Audio solo en TV» compartido por las tecnologías que capturan el audio con [CapturePipeline] (IPTV y OpenTV Link).
 * Une la preferencia del usuario, [LocalMuteJudge] (decide midiendo) y [LocalAudioMuter] (baja/restaura el volumen).
 * Comprobado físicamente en IPTV (LG y Noblex Android TV). Miracast nativo, DLNA y el espejo por navegador usan archivos
 * protegidos y no pasan por aquí.
 */
class SoloTvAudio private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("opentv_iptv", Context.MODE_PRIVATE)
    private val judge = LocalMuteJudge()
    private val muter = LocalAudioMuter(context)

    private val _enabled = MutableStateFlow(prefs.getBoolean("solo_tv", false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _label = MutableStateFlow(LocalMuteJudge.Phase.OFF.label)
    val label: StateFlow<String> = _label.asStateFlow()

    /** [sessionActive]: hay una emisión con captura de audio en marcha ahora. */
    fun toggle(sessionActive: Boolean) {
        val on = !_enabled.value
        _enabled.value = on
        prefs.edit().putBoolean("solo_tv", on).apply()
        if (sessionActive) {
            if (on) judge.arm() else apply(judge.disarm())
        }
        _label.value = judge.phase.label
    }

    /** Al empezar una emisión. */
    fun onStart(capturesSystemAudio: Boolean) {
        if (_enabled.value && capturesSystemAudio) judge.arm() else judge.disarm()
        _label.value = judge.phase.label
    }

    /** Una vez por segundo con el pico de audio capturado. */
    fun onSecond(capturesSystemAudio: Boolean, peakDb: Double) {
        if (_enabled.value && capturesSystemAudio) apply(judge.onSecond(peakDb))
    }

    /** Al terminar (por la razón que sea): restaura siempre el volumen original. */
    fun onStop() {
        runCatching { muter.restore() }
        judge.disarm()
        _label.value = judge.phase.label
    }

    private fun apply(action: LocalMuteJudge.Action) {
        when (action) {
            LocalMuteJudge.Action.MUTE -> muter.mute()
            LocalMuteJudge.Action.RESTORE -> muter.restore()
            LocalMuteJudge.Action.NONE -> Unit
        }
        _label.value = judge.phase.label
    }

    companion object {
        @Volatile private var instance: SoloTvAudio? = null
        fun get(context: Context): SoloTvAudio =
            instance ?: synchronized(this) { instance ?: SoloTvAudio(context.applicationContext).also { instance = it } }
    }
}
