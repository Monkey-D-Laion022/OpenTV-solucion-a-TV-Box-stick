package com.opentv.app.stream

import android.content.Context
import android.media.AudioManager

/** Volumen multimedia real del teléfono (no pide permisos). */
class AudioManagerVolume(context: Context) : VolumeControl {
    private val audio = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    override var level: Int
        get() = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        set(value) {
            runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0) }
        }
}

class PrefsMuteStore(context: Context) : MuteStore {
    private val prefs = context.applicationContext.getSharedPreferences("opentv_mute", Context.MODE_PRIVATE)

    override var savedLevel: Int?
        get() = prefs.getInt("saved", -1).takeIf { it >= 0 }
        set(value) {
            prefs.edit().apply { if (value == null) remove("saved") else putInt("saved", value) }.commit()
        }
}
