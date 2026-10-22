package com.opentv.app.connect

/** Un TV al que se puede enviar un enlace desde el selector propio de OpenTV. */
sealed interface TvTarget {
    /** Identificador estable para listas. */
    val key: String
    val name: String
    val protocol: String

    data class CastTarget(val device: CastDevice) : TvTarget {
        override val key get() = "cast:${device.id}"
        override val name get() = device.name
        override val protocol get() = "Google Cast"
    }

    data class DlnaTarget(val device: UpnpDevice) : TvTarget {
        override val key get() = "dlna:${device.udn}"
        override val name get() = device.friendlyName
        override val protocol get() = "DLNA"
    }
}

object TvTargets {

    /**
     * Une los dispositivos Cast y DLNA en una sola lista ordenada por nombre. No se fusionan los que
     * tienen el mismo nombre: un mismo TV puede aparecer por los dos métodos y se elige cuál usar.
     */
    fun build(cast: List<CastDevice>, dlna: List<UpnpDevice>): List<TvTarget> =
        (cast.map { TvTarget.CastTarget(it) } + dlna.map { TvTarget.DlnaTarget(it) })
            .distinctBy { it.key }
            .sortedWith(compareBy({ it.name.lowercase() }, { it.protocol }))
}
