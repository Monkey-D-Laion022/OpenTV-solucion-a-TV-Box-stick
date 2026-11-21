package com.opentv.app.iptv

/** Aviso por ahorro de energía. Lógica pura: Android solo informa si el modo está activo; el efecto sobre el codificador NO se mide aquí. */
object PowerSaveJudge {
    /** El estándar de Android o cualquiera de los indicadores propios del fabricante (Xiaomi: ahorro normal y ahorro extremo). */
    fun active(standard: Boolean, oemSaver: Boolean, oemSuperSaver: Boolean): Boolean = standard || oemSaver || oemSuperSaver

    fun warning(powerSave: Boolean, targetFps: Int, thermal: String?): String? {
        if (!powerSave) return null
        val fps = if (targetFps >= 60) " Con 60 fps es donde más se nota." else ""
        return "El ahorro de energía del teléfono está activado: Android puede limitar la CPU/GPU y el codificador, y la emisión puede entrecortarse.$fps " +
            "Desactivalo mientras transmitís (Ajustes › Batería). Estado térmico: ${thermal ?: "no disponible"}."
    }
}
