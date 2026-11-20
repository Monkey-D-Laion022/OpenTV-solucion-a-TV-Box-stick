package com.opentv.home

import android.content.Context

/** Perfil local de OpenTV (no es una cuenta de Google): cada uno tiene sus favoritos, recientes y apps ocultas. */
data class Profile(val id: String, val name: String)

/**
 * Lista de perfiles y perfil activo, en SharedPreferences.
 * El perfil «Principal» (id [DEFAULT_ID]) usa el archivo de preferencias de siempre, así que los
 * favoritos de versiones anteriores del módulo se conservan.
 */
class ProfileStore(context: Context) {

    private val appContext = context.applicationContext
    private val sp = appContext.getSharedPreferences("opentv_profiles", Context.MODE_PRIVATE)

    fun profiles(): List<Profile> {
        val parsed = (sp.getString(KEY_LIST, "") ?: "")
            .split("\n")
            .mapNotNull { line ->
                val sep = line.indexOf('|')
                if (sep <= 0) null else Profile(line.substring(0, sep), line.substring(sep + 1))
            }
        return parsed.ifEmpty { listOf(Profile(DEFAULT_ID, DEFAULT_NAME)) }
    }

    fun activeId(): String {
        val list = profiles()
        val saved = sp.getString(KEY_ACTIVE, null)
        return list.firstOrNull { it.id == saved }?.id ?: list.first().id
    }

    fun setActive(id: String) {
        sp.edit().putString(KEY_ACTIVE, id).apply()
    }

    fun add(name: String): Profile {
        val list = profiles()
        val clean = name.replace('|', ' ').replace('\n', ' ').trim().take(MAX_NAME)
            .ifEmpty { "Perfil ${list.size + 1}" }
        var id = System.currentTimeMillis().toString(36)
        while (list.any { it.id == id }) id += "x"
        val profile = Profile(id, clean)
        save(list + profile)
        return profile
    }

    /** Elimina un perfil y sus datos. Devuelve false si es el único que queda. */
    fun remove(id: String): Boolean {
        val list = profiles()
        if (list.size <= 1 || list.none { it.id == id }) return false
        val rest = list.filterNot { it.id == id }
        save(rest)
        if (sp.getString(KEY_ACTIVE, null) == id) setActive(rest.first().id)
        appContext.deleteSharedPreferences(HomePrefs.fileFor(id))
        return true
    }

    private fun save(list: List<Profile>) {
        sp.edit().putString(KEY_LIST, list.joinToString("\n") { "${it.id}|${it.name}" }).apply()
    }

    companion object {
        const val DEFAULT_ID = "main"
        const val DEFAULT_NAME = "Principal"
        private const val KEY_LIST = "list"
        private const val KEY_ACTIVE = "active"
        private const val MAX_NAME = 20
    }
}
