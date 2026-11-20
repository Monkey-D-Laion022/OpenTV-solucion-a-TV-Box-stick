package com.opentv.home

import android.content.Context

/**
 * Favoritos (con orden), recientes, apps ocultas y opciones, en SharedPreferences.
 * Un archivo por perfil (ver [ProfileStore]). Sin dependencias extra.
 */
class HomePrefs(context: Context, profileId: String = ProfileStore.DEFAULT_ID) {

    private val sp = context.getSharedPreferences(fileFor(profileId), Context.MODE_PRIVATE)

    fun favorites(): List<String> = read("favorites")

    fun setFavorites(list: List<String>) = write("favorites", list)

    /** Apps ocultas de las filas y la grilla (siguen instaladas y se abren por voz). */
    fun hidden(): List<String> = read("hidden")

    fun setHidden(list: List<String>) = write("hidden", list)

    fun recents(): List<String> = read("recents")

    /** Mueve [packageName] al principio de recientes (máximo [MAX_RECENTS]). */
    fun pushRecent(packageName: String): List<String> {
        val list = recents().toMutableList()
        list.remove(packageName)
        list.add(0, packageName)
        val trimmed = list.take(MAX_RECENTS)
        write("recents", trimmed)
        return trimmed
    }

    /** Si es false (por defecto) las notificaciones solo muestran la app, sin título ni texto. */
    fun showNotifText(): Boolean = sp.getBoolean("notif_text", false)

    fun setShowNotifText(on: Boolean) {
        sp.edit().putBoolean("notif_text", on).apply()
    }

    private fun read(key: String): List<String> =
        (sp.getString(key, "") ?: "").split(",").filter { it.isNotBlank() }

    private fun write(key: String, list: List<String>) {
        sp.edit().putString(key, list.joinToString(",")).apply()
    }

    companion object {
        private const val MAX_RECENTS = 12

        /** El perfil principal conserva el archivo de las versiones anteriores. */
        fun fileFor(profileId: String): String =
            if (profileId == ProfileStore.DEFAULT_ID) "opentv_home" else "opentv_home_$profileId"
    }
}
