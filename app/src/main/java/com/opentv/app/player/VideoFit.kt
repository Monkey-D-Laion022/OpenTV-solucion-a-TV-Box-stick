package com.opentv.app.player

/**
 * Cómo se acomoda el video en la pantalla. Nunca deforma salvo que el usuario elija «Estirar».
 * El paso a las constantes de Media3 se hace en la pantalla, para mantener esto sin Android.
 */
enum class VideoFit(val label: String, val description: String) {
    FIT("Ajustar", "Se ve completo, con barras si la proporción no coincide."),
    ZOOM("Llenar", "Llena la pantalla recortando los bordes, sin deformar."),
    STRETCH("Estirar", "Llena la pantalla deformando la imagen.");

    fun next(): VideoFit {
        val all = values()
        return all[(ordinal + 1) % all.size]
    }
}
