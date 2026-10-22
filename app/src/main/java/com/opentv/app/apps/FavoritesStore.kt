package com.opentv.app.apps

import android.content.Context

/** Guarda las favoritas en SharedPreferences (suficiente para una lista corta; sin Room). */
class FavoritesStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Nombres de paquete marcados como favoritos. */
    @Synchronized
    fun load(): Set<String> = prefs.getStringSet(KEY, null)?.toSet() ?: emptySet()

    /** Marca o desmarca una app y devuelve el conjunto actualizado. */
    @Synchronized
    fun toggle(packageName: String): Set<String> {
        val current = load()
        val next = if (packageName in current) current - packageName else current + packageName
        // `next` es un conjunto nuevo: SharedPreferences exige no modificar el que ya guardó.
        prefs.edit().putStringSet(KEY, next).apply()
        return next
    }

    private companion object {
        const val PREFS_NAME = "opentv_favorites"
        const val KEY = "packages"
    }
}
