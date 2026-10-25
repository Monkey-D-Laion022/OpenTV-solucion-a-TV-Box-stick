package com.opentv.app.display

import android.content.Context

/** Guarda en disco qué hay que deshacer, para poder restaurar la resolución aunque la app se cierre mal. */
class PrefsResizeStore(context: Context) : ResizeStore {
    private val prefs = context.applicationContext.getSharedPreferences("opentv_resize", Context.MODE_PRIVATE)

    override var pending: PendingResize?
        get() {
            if (!prefs.getBoolean(KEY_PENDING, false)) return null
            return PendingResize(
                prevSize = prefs.getString(KEY_PREV_SIZE, null),
                prevDensity = prefs.getInt(KEY_PREV_DENSITY, -1).takeIf { it >= 0 },
                appliedSize = prefs.getString(KEY_APPLIED_SIZE, "").orEmpty(),
                appliedDensity = prefs.getInt(KEY_APPLIED_DENSITY, 0),
                // Los pendientes guardados por versiones anteriores siempre cambiaron la densidad.
                densityChanged = prefs.getBoolean(KEY_DENSITY_CHANGED, true),
                rotationLocked = prefs.getBoolean(KEY_ROT_LOCKED, false),
                prevAutoRotate = prefs.getInt(KEY_PREV_AUTO, -1).takeIf { it >= 0 },
                prevUserRotation = prefs.getInt(KEY_PREV_USER, -1).takeIf { it >= 0 },
            )
        }
        set(value) {
            val e = prefs.edit()
            if (value == null) {
                e.clear()
            } else {
                e.putBoolean(KEY_PENDING, true)
                    .putString(KEY_PREV_SIZE, value.prevSize)
                    .putInt(KEY_PREV_DENSITY, value.prevDensity ?: -1)
                    .putString(KEY_APPLIED_SIZE, value.appliedSize)
                    .putInt(KEY_APPLIED_DENSITY, value.appliedDensity)
                    .putBoolean(KEY_DENSITY_CHANGED, value.densityChanged)
                    .putBoolean(KEY_ROT_LOCKED, value.rotationLocked)
                    .putInt(KEY_PREV_AUTO, value.prevAutoRotate ?: -1)
                    .putInt(KEY_PREV_USER, value.prevUserRotation ?: -1)
            }
            e.commit() // síncrono: tiene que estar en disco antes de cambiar la resolución
        }

    private companion object {
        const val KEY_PENDING = "pending"
        const val KEY_PREV_SIZE = "prevSize"
        const val KEY_PREV_DENSITY = "prevDensity"
        const val KEY_APPLIED_SIZE = "appliedSize"
        const val KEY_APPLIED_DENSITY = "appliedDensity"
        const val KEY_DENSITY_CHANGED = "densityChanged"
        const val KEY_ROT_LOCKED = "rotationLocked"
        const val KEY_PREV_AUTO = "prevAutoRotate"
        const val KEY_PREV_USER = "prevUserRotation"
    }
}
