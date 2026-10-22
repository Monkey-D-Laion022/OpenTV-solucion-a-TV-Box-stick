package com.opentv.app.core

/**
 * Decide si una app externa declara orientación SOLO vertical en su manifest.
 *
 * Android no ofrece ninguna API pública para forzar a otra app a abrirse en horizontal o en 16:9,
 * así que OpenTV solo puede AVISAR antes de abrirla. Los valores numéricos corresponden a las
 * constantes de `ActivityInfo.SCREEN_ORIENTATION_*` (se repiten aquí para que esta lógica no
 * dependa de Android y pueda probarse en la JVM).
 */
object OrientationPolicy {

    private const val PORTRAIT = 1 // SCREEN_ORIENTATION_PORTRAIT
    private const val SENSOR_PORTRAIT = 7 // SCREEN_ORIENTATION_SENSOR_PORTRAIT
    private const val REVERSE_PORTRAIT = 9 // SCREEN_ORIENTATION_REVERSE_PORTRAIT
    private const val USER_PORTRAIT = 12 // SCREEN_ORIENTATION_USER_PORTRAIT

    private val portraitOnly = setOf(PORTRAIT, SENSOR_PORTRAIT, REVERSE_PORTRAIT, USER_PORTRAIT)

    fun declaresPortraitOnly(screenOrientation: Int): Boolean = screenOrientation in portraitOnly
}
