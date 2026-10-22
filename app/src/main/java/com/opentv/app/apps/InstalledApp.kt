package com.opentv.app.apps

/**
 * Una aplicación con pantalla de inicio que se puede abrir desde OpenTV.
 *
 * [screenOrientation] es el valor de `ActivityInfo.screenOrientation` declarado en el manifest de
 * la app (ver `OrientationPolicy`). Solo refleja lo DECLARADO: una app puede bloquear la
 * orientación por código y eso no se puede detectar desde fuera.
 */
data class InstalledApp(
    val packageName: String,
    val activityName: String,
    val label: String,
    val screenOrientation: Int,
)
