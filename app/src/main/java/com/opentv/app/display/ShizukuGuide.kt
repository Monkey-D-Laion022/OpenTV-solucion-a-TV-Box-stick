package com.opentv.app.display

/** Paso del asistente de Shizuku. [done] se calcula con el estado real; [action] dice qué botón mostrar. */
data class GuideStep(
    val number: Int,
    val title: String,
    val body: String,
    val done: Boolean,
    val action: GuideAction?,
)

enum class GuideAction(val label: String) {
    INSTALL_SHIZUKU("Buscar Shizuku"),
    OPEN_DEVELOPER_OPTIONS("Abrir opciones de desarrollador"),
    OPEN_SHIZUKU("Abrir Shizuku"),
    REQUEST_PERMISSION("Dar permiso a OpenTV"),
}

/**
 * Pasos para activar Shizuku (necesario solo para el 16:9 manual). Cada paso se marca hecho según el estado REAL de
 * Shizuku, no por un toque del usuario. Lógica pura. Los textos describen Xiaomi/HyperOS y Android genérico; los
 * nombres de menú pueden variar según la marca y la versión.
 */
object ShizukuGuide {
    /** @param installed Shizuku instalada; @param running activa (responde); @param granted con permiso para OpenTV. */
    fun steps(installed: Boolean, running: Boolean, granted: Boolean, developerOptionsOn: Boolean): List<GuideStep> {
        return listOf(
            GuideStep(
                1, "Instalar Shizuku",
                "Shizuku es una app gratuita aparte (se llama «Shizuku»). Instalala desde tu tienda de apps. OpenTV no puede instalarla por vos.",
                done = installed, action = if (installed) null else GuideAction.INSTALL_SHIZUKU,
            ),
            GuideStep(
                2, "Activar opciones de desarrollador",
                "Ajustes → Acerca del teléfono → tocá 7 veces seguidas «Versión de MIUI» (o «Versión de HyperOS» / «Número de compilación»). " +
                    "Aparece un aviso «Ya sos desarrollador».",
                done = developerOptionsOn || running, action = GuideAction.OPEN_DEVELOPER_OPTIONS,
            ),
            GuideStep(
                3, "Activar la depuración inalámbrica",
                "En opciones de desarrollador, activá «Depuración inalámbrica» (el teléfono tiene que estar conectado a Wi‑Fi; requiere Android 11 o más). " +
                    "Aceptá el aviso de la red.",
                done = running, action = GuideAction.OPEN_DEVELOPER_OPTIONS,
            ),
            GuideStep(
                4, "Iniciar Shizuku",
                "Abrí Shizuku y tocá «Iniciar» en «Depuración inalámbrica». La primera vez pide emparejar: en Depuración inalámbrica tocá " +
                    "«Vincular dispositivo con código», y escribí el código en la notificación de Shizuku. Después, «Iniciar».",
                done = running, action = if (installed) GuideAction.OPEN_SHIZUKU else null,
            ),
            GuideStep(
                5, "Darle permiso a OpenTV",
                "Volvé acá y tocá el botón: aparece un cartel de Shizuku; aceptalo.",
                done = granted, action = if (running && !granted) GuideAction.REQUEST_PERMISSION else null,
            ),
        )
    }

    /** Aviso fijo: Shizuku no se puede automatizar del todo. */
    const val LIMITS =
        "Shizuku se apaga al reiniciar el teléfono: hay que iniciarlo de nuevo (el emparejamiento queda hecho). " +
            "Android no permite que una app active la depuración por sí sola."
}
