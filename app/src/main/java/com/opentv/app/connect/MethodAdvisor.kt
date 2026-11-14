package com.opentv.app.connect

/**
 * Elige el mejor método para un mismo televisor que aparece por varios protocolos. Lógica pura, sin Android: la UI solo
 * muestra el resultado (un TV, un botón «Conectar» recomendado y el resto como alternativas), sin menús duplicados.
 *
 * Orden de preferencia (de lo que da imagen más fiel a lo más básico), siempre filtrado por lo que el TV realmente anunció:
 * 1. OpenTV Link (receptor propio, cifrado) 2. Miracast (espejo nativo) 3. Google Cast 4. DLNA (flujo de OpenTV) 5. Navegador /tv.
 * Un método que ya falló en esta sesión pasa al final, para no insistir en lo que no funcionó.
 */
enum class LinkKind(val label: String, val rank: Int, val mirrorsScreen: Boolean) {
    OPENTV_LINK("OpenTV Link", 0, true),
    MIRACAST("Miracast", 1, true),
    CAST("Google Cast", 2, false),
    DLNA("DLNA", 3, false),
    BROWSER("Navegador del TV (/tv)", 4, false),
}

data class DeviceOffer(val kind: LinkKind, val id: String)

data class UnifiedDevice(val name: String, val offers: List<DeviceOffer>) {
    val kinds: Set<LinkKind> get() = offers.map { it.kind }.toSet()
}

object MethodAdvisor {
    /** Une ofertas de varios descubrimientos en un solo dispositivo por nombre normalizado (sin tildes ni mayúsculas ni sufijos de modelo). */
    fun merge(offers: List<Pair<String, DeviceOffer>>): List<UnifiedDevice> =
        offers.groupBy { normalize(it.first) }
            .map { (_, v) -> UnifiedDevice(v.first().first.trim(), v.map { it.second }.distinctBy { it.kind to it.id }) }
            .sortedBy { it.name.lowercase() }

    fun normalize(name: String): String = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .split(' ').filter { it.isNotEmpty() && it !in BRANDS }.joinToString(" ")

    private val BRANDS = setOf("lg", "samsung", "sony", "tcl", "philips", "hisense")

    /** Orden de intentos: mejor primero; los métodos fallidos van al final. */
    fun ranked(device: UnifiedDevice, failed: Set<LinkKind> = emptySet(), wantMirror: Boolean = true): List<LinkKind> =
        device.kinds.sortedWith(
            compareBy<LinkKind>({ it in failed }, { if (wantMirror) !it.mirrorsScreen && device.kinds.any { k -> k.mirrorsScreen && k !in failed } else false }, { it.rank }),
        )

    fun best(device: UnifiedDevice, failed: Set<LinkKind> = emptySet(), wantMirror: Boolean = true): LinkKind? = ranked(device, failed, wantMirror).firstOrNull()
}
