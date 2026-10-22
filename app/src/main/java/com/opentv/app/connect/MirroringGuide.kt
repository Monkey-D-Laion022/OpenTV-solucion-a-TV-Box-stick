package com.opentv.app.connect

/**
 * Marca del TV, para mostrar los pasos a seguir en el televisor. Los nombres de las opciones
 * cambian según modelo y versión de software: por eso los textos dicen «suele» y «buscá».
 */
enum class TvBrand(val label: String, val steps: List<String>) {
    SAMSUNG(
        "Samsung",
        listOf(
            "Con el control del TV, abrí la lista de fuentes (Source).",
            "Elegí «Compartir pantalla» o «Screen Sharing». En algunos modelos se llama " +
                "«Smart View» o «Screen Mirroring».",
        ),
    ),
    LG(
        "LG",
        listOf(
            "En el inicio del TV, abrí la aplicación «Screen Share» (Compartir pantalla).",
            "Dejá esa pantalla abierta: el TV espera la conexión del teléfono.",
        ),
    ),
    SONY(
        "Sony",
        listOf(
            "Pulsá el botón de entradas (Input) del control.",
            "Elegí «Screen mirroring» y dejá esa pantalla abierta.",
        ),
    ),
    ANDROID_TV(
        "Android TV / Google TV",
        listOf(
            "TV de Philips, TCL, Hisense, Xiaomi y otros con Android TV suelen traer " +
                "Chromecast integrado: no hace falta activar nada.",
            "Verificá que el TV esté en la misma red Wi‑Fi que el teléfono. Para estos TV " +
                "probá primero Google Cast.",
        ),
    ),
    OTHER(
        "Otra marca",
        listOf(
            "Buscá en las entradas o en los ajustes de red del TV una opción llamada " +
                "«Screen Mirroring», «Miracast», «Wi‑Fi Display», «Screen Share» o «Cast».",
            "Si no encontrás ninguna, es probable que el TV no admita Miracast: usá DLNA o " +
                "Google Cast.",
        ),
    );

    /** Siguiente marca, en círculo; lo usa el botón selector. */
    fun next(): TvBrand {
        val all = values()
        return all[(ordinal + 1) % all.size]
    }
}

/** Pasos a seguir en el teléfono, según el fabricante informado por Android. */
data class PhoneGuide(val featureName: String, val steps: List<String>)

object PhoneGuides {

    fun forManufacturer(manufacturer: String): PhoneGuide {
        val name = manufacturer.trim().lowercase()
        val feature = when {
            name.contains("samsung") -> "Smart View"
            name.contains("xiaomi") || name.contains("redmi") || name.contains("poco") -> "Cast / Transmitir"
            name.contains("huawei") || name.contains("honor") -> "Proyección inalámbrica"
            name.contains("oppo") || name.contains("realme") || name.contains("oneplus") ->
                "Cast / Transmitir pantalla"
            name.contains("motorola") || name.contains("lenovo") -> "Transmitir (Cast)"
            name.contains("sony") -> "Duplicación de pantalla (Screen mirroring)"
            name.contains("google") -> "Transmitir pantalla"
            else -> "Transmitir / Cast / Duplicar pantalla"
        }
        return PhoneGuide(
            featureName = feature,
            steps = listOf(
                "Dejá abierta en el TV la pantalla de recepción (ver pasos del TV).",
                "Tocá «Abrir transmisión del sistema». Si el equipo no la abre, deslizá el panel " +
                    "rápido y buscá la opción «$feature» (el nombre puede variar).",
                "Elegí tu TV en la lista y aceptá el aviso que aparezca en el TV.",
                "Para cortar, usá «Desconectar» o la misma opción del panel rápido.",
            ),
        )
    }
}
