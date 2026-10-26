package com.opentv.app.stream

/** Volumen de multimedia del teléfono. */
interface VolumeControl {
    var level: Int
}

/** Dónde se anota el volumen original, para devolverlo aunque la app se cierre mal. */
interface MuteStore {
    var savedLevel: Int?
}

/**
 * Baja el volumen multimedia del teléfono a 0 mientras se transmite (EXPERIMENTAL: Android no ofrece un modo público
 * de enviar el sonido solo al TV; la captura de audio copia lo que suena, no lo reemplaza) y lo devuelve después.
 * Si la captura sale antes del control de volumen, el TV también quedaría mudo: por eso es opcional. Lógica pura.
 */
class PhoneMute(private val volume: VolumeControl, private val store: MuteStore) {
    val isMuted: Boolean get() = store.savedLevel != null

    fun mute() {
        if (store.savedLevel == null) store.savedLevel = volume.level
        volume.level = 0
    }

    /** Devuelve el volumen que había; no hace nada si no se había silenciado. */
    fun restore() {
        val saved = store.savedLevel ?: return
        volume.level = saved
        store.savedLevel = null
    }
}
