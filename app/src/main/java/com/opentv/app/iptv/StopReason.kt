package com.opentv.app.iptv

/**
 * Por qué terminó una emisión. Se muestra en pantalla para distinguir un corte decidido por el usuario de uno causado
 * por Android, así una interrupción no queda sin explicación. Lógica pura.
 */
enum class StopReason(val label: String) {
    USER("Detenida por el usuario."),
    NOTIFICATION("Detenida con el botón «Detener» de la notificación."),
    PROJECTION_ENDED("Android cerró la captura de pantalla (el sistema o el usuario terminó la proyección; no fue OpenTV)."),
    SERVICE_LOST("Android detuvo el servicio en primer plano de OpenTV (posible ahorro de batería del sistema)."),
    ERROR("Se detuvo por un error."),
}
