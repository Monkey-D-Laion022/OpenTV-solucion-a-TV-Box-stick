package com.opentv.app.apps

import java.text.Normalizer
import java.util.Locale

enum class SortMode(val label: String) {
    FAVORITES_FIRST("Favoritas primero"),
    NAME_ASC("Nombre A–Z"),
    NAME_DESC("Nombre Z–A");

    /** Siguiente modo, en círculo; lo usa el botón «Orden». */
    fun next(): SortMode = values()[(ordinal + 1) % values().size]
}

/** Búsqueda y orden de la lista de apps. Lógica pura: sin Android, probada en la JVM. */
object AppListLogic {

    private val diacritics = Regex("\\p{InCombiningDiacriticalMarks}+")

    /** Minúsculas y sin acentos, para que «camara» encuentre «Cámara». */
    fun normalize(text: String): String =
        Normalizer.normalize(text.trim(), Normalizer.Form.NFD)
            .replace(diacritics, "")
            .lowercase(Locale.ROOT)

    /** Una consulta vacía devuelve todo; si no, busca en el nombre y en el paquete. */
    fun filter(apps: List<InstalledApp>, query: String): List<InstalledApp> {
        val needle = normalize(query)
        if (needle.isEmpty()) return apps
        return apps.filter {
            normalize(it.label).contains(needle) || it.packageName.lowercase(Locale.ROOT).contains(needle)
        }
    }

    fun sort(apps: List<InstalledApp>, mode: SortMode, favorites: Set<String>): List<InstalledApp> {
        // La clave de orden se calcula una sola vez por app.
        val keyed = apps.map { it to normalize(it.label) }
        val byName = compareBy<Pair<InstalledApp, String>>({ it.second }, { it.first.packageName })
        val ordered = when (mode) {
            SortMode.NAME_ASC -> keyed.sortedWith(byName)
            SortMode.NAME_DESC -> keyed.sortedWith(byName.reversed())
            SortMode.FAVORITES_FIRST -> keyed.sortedWith(
                compareBy<Pair<InstalledApp, String>> { if (it.first.packageName in favorites) 0 else 1 }
                    .then(byName),
            )
        }
        return ordered.map { it.first }
    }

    fun apply(
        apps: List<InstalledApp>,
        query: String,
        mode: SortMode,
        favorites: Set<String>,
    ): List<InstalledApp> = sort(filter(apps, query), mode, favorites)
}
