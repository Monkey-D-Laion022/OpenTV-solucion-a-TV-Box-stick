package com.opentv.app.connect

/** Consejos de solución de problemas, de lo más específico a lo más general. Lógica pura. */
object Troubleshooting {

    fun tips(onLocalNetwork: Boolean, dlnaScannedWithoutResults: Boolean): List<String> {
        val tips = mutableListOf<String>()
        if (!onLocalNetwork) {
            tips += "Conectá el teléfono a Wi‑Fi: Cast y DLNA exigen estar en la misma red que el TV."
        }
        if (dlnaScannedWithoutResults) {
            tips += "Si DLNA no encuentra el TV: activá «DLNA» o «Compartir medios» en el TV y " +
                "revisá que el router no aísle los dispositivos (aislamiento de clientes o red " +
                "de invitados)."
        }
        tips += "Verificá que el TV esté encendido y en la entrada de recepción antes de elegirlo " +
            "en el teléfono."
        tips += "Acercá el teléfono al TV, desactivá la VPN y el ahorro de batería, y probá apagar " +
            "y encender el Wi‑Fi."
        tips += "Si el TV pide aceptar la conexión, hacelo en pantalla: sin esa aprobación no " +
            "empieza la transmisión."
        tips += "Si la imagen se ve cortada o con barras negras, ajustá la proporción en el menú " +
            "de imagen del TV: OpenTV no controla eso."
        return tips
    }
}
