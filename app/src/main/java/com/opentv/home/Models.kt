package com.opentv.home

import androidx.compose.ui.graphics.ImageBitmap
import java.text.Normalizer

/** Una app lanzable. [searchKey] es el nombre normalizado (sin tildes ni símbolos) para buscar y para la voz. */
data class AppEntry(
    val label: String,
    val packageName: String,
    val icon: ImageBitmap?,
    /** Banner 16:9 de la app si la app de TV lo declara; si no, null. */
    val banner: ImageBitmap?,
    val isTvApp: Boolean,
    val searchKey: String,
)

enum class HomeTab(val title: String) {
    Home("Inicio"),
    Apps("Apps"),
    Search("Buscar"),
    Settings("Ajustes"),
}

/** Normaliza texto: minúsculas, sin tildes, solo letras/números/espacios. */
object Norm {
    private val marks = Regex("\\p{Mn}+")
    private val junk = Regex("[^a-z0-9 ]")
    private val spaces = Regex("\\s+")

    fun of(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(marks, "")
            .lowercase()
            .replace(junk, " ")
            .replace(spaces, " ")
            .trim()
}
