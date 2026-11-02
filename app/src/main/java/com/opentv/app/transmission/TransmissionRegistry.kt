package com.opentv.app.transmission

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Lo que se sabe de una transmisión activa. `null` = no se puede determinar (nunca se inventa un valor). */
data class TransmissionInfo(
    val id: String,
    /** «IPTV», «OpenTV Link», … */
    val technology: String,
    val width: Int,
    val height: Int,
    val requestedFps: Int,
    val encodedFps: Double?,
    val bitrateKbps: Int?,
    /** Texto del estado de audio (ya redactado por quien lo mide). */
    val audio: String,
    /** Relación de aspecto actual de la pantalla del teléfono (ancho/alto) o `null`. */
    val screenAspect: Double?,
    val detail: String? = null,
)

/**
 * Registro central de las transmisiones que OpenTV tiene activas y de cómo detenerlas. La notificación persistente lee de
 * aquí, así que sirve para cualquier tecnología que se registre. Las que no pasan por este registro (el espejo nativo del
 * sistema y la proyección DLNA, que tienen su propio servicio) no aparecen: la notificación lo aclara.
 */
object TransmissionRegistry {
    private class Entry(var info: TransmissionInfo, val stop: () -> Unit)

    private val entries = ConcurrentHashMap<String, Entry>()
    private val _state = MutableStateFlow<List<TransmissionInfo>>(emptyList())
    val state: StateFlow<List<TransmissionInfo>> = _state.asStateFlow()

    /** Alta o actualización. [stop] se invoca desde el botón «Detener» de la notificación. */
    fun publish(info: TransmissionInfo, stop: () -> Unit) {
        val existing = entries[info.id]
        if (existing != null) existing.info = info else entries[info.id] = Entry(info, stop)
        refresh()
    }

    fun remove(id: String) {
        entries.remove(id)
        refresh()
    }

    val isEmpty: Boolean get() = entries.isEmpty()

    /** Detiene todas las transmisiones registradas. Cada [stop] debe ser seguro de llamar desde el hilo principal. */
    fun stopAll() {
        val stops = entries.values.map { it.stop }
        stops.forEach { runCatching { it() } }
    }

    private fun refresh() {
        _state.value = entries.values.map { it.info }.sortedBy { it.id }
    }
}

/** Redacta la notificación a partir del registro. Lógica pura (se prueba sin Android). */
object TransmissionText {
    data class Rendered(val title: String, val text: String, val bigText: String)

    fun render(list: List<TransmissionInfo>): Rendered {
        if (list.isEmpty()) return Rendered("OpenTV: sin transmisiones activas", "Detenido.", "Detenido.")
        val title = if (list.size == 1) "OpenTV transmitiendo · ${list[0].technology}" else "OpenTV · ${list.size} transmisiones activas"
        val lines = list.map { line(it) }
        return Rendered(title, lines.first(), lines.joinToString("\n"))
    }

    fun line(t: TransmissionInfo): String {
        val fps = t.encodedFps?.let { "%.0f".format(it) } ?: "no disponible"
        val rate = t.bitrateKbps?.let { if (it >= 1000) "%.1f Mbit/s".format(it / 1000.0) else "$it kbit/s" } ?: "no disponible"
        val aspect = when {
            t.screenAspect == null -> "16:9: no determinado"
            isSixteenNine(t.screenAspect) -> "pantalla en 16:9"
            else -> "pantalla NO en 16:9"
        }
        return "${t.technology}: ${t.width}×${t.height} (pedido ${t.requestedFps} fps) · codificados: $fps fps · $rate · audio: ${t.audio} · $aspect" + (t.detail?.let { " · $it" } ?: "")
    }

    /** Tolerancia de 2 % para redondeos de la barra de navegación. */
    fun isSixteenNine(aspect: Double): Boolean {
        val target = 16.0 / 9.0
        return kotlin.math.abs(aspect - target) / target < 0.02
    }
}
