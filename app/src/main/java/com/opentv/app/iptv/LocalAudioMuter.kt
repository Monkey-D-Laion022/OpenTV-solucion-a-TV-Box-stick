package com.opentv.app.iptv

import android.content.Context
import android.media.AudioManager

/**
 * Baja a 0 el volumen de «multimedia» del teléfono y lo restaura al terminar. No usa permisos. Solo restaura si el
 * volumen sigue en 0 (si el usuario lo subió a mano, se respeta). El resultado sobre la captura lo juzga [LocalMuteJudge].
 */
class LocalAudioMuter(context: Context) {
    private val audio = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var saved: Int? = null

    val isMuted: Boolean get() = saved != null

    fun mute() {
        if (saved != null) return
        val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (current == 0) return // ya estaba en silencio: no hay nada que restaurar
        saved = current
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0) }
    }

    fun restore() {
        val s = saved ?: return
        saved = null
        runCatching {
            if (audio.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) audio.setStreamVolume(AudioManager.STREAM_MUSIC, s, 0)
        }
    }
}
