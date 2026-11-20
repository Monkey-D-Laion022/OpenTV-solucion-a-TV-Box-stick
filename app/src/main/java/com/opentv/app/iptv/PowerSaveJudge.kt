package com.opentv.app.iptv

/** Aviso por ahorro de energía. Lógica pura: Android solo informa si el modo está activo; el efecto sobre el codificador NO se mide aquí. */
object PowerSaveJudge {
    fun warning(powerSave: Boolean, targetFps: Int, thermal: String?): String? {
        if (!powerSave) return null
        val fps = if (targetFps >= 60) " Con 60 fps es donde más se nota." else ""
        return "El ahorro de energía del teléfono está activado: Android puede limitar la CPU/GPU y el codificador, y la emisión puede entrecortarse.$fps " +
            "Desactivalo mientras transmitís (Ajustes › Batería). Estado térmico: ${thermal ?: "no disponible"}."
    }
}
