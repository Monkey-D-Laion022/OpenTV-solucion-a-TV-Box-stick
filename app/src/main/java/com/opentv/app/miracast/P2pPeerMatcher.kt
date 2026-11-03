package com.opentv.app.miracast

/**
 * Resuelve el par elegido contra la lista VIGENTE del sistema. Lógica pura.
 * Primero por dirección (sin distinguir mayúsculas); si no está, por nombre exacto SOLO si es único: algunos televisores
 * cambian su dirección Wi‑Fi Direct entre sesiones y la dirección guardada en memoria queda vieja.
 */
object P2pPeerMatcher {
    data class Candidate(val address: String, val name: String, val status: Int)
    data class Match(val candidate: Candidate?, val addressChanged: Boolean)

    fun resolve(list: List<Candidate>, wantedAddress: String, wantedName: String): Match {
        val byAddress: Candidate? = list.firstOrNull { it.address.equals(wantedAddress, ignoreCase = true) }
        if (byAddress != null) return Match(byAddress, false)
        val name = wantedName.trim()
        if (name.isEmpty()) return Match(null, false)
        val byName: List<Candidate> = list.filter { it.name.trim().equals(name, ignoreCase = true) }
        return if (byName.size == 1) Match(byName[0], true) else Match(null, false)
    }
}
