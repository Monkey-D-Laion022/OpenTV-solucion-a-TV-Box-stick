package com.opentv.app.catalog

/**
 * Decide si seguir pidiendo pósteres a RPDB. Si la clave es rechazada se corta de inmediato; si fallan varios pedidos
 * seguidos sin ningún éxito (por ejemplo, un formato de identificador que RPDB no acepta) también, y se usan los
 * pósteres de TMDB. PURO y seguro entre hilos.
 */
class RpdbPolicy(private val maxFailuresWithoutSuccess: Int = 5) {
    private var failures = 0
    private var successes = 0
    var disabledReason: String? = null
        private set

    @Synchronized fun isActive(): Boolean = disabledReason == null

    @Synchronized fun onSuccess() {
        successes++
        failures = 0
    }

    /** [httpCode] `null` = error de red (no cuenta contra la clave ni apaga RPDB por sí solo). */
    @Synchronized fun onFailure(httpCode: Int?) {
        if (disabledReason != null) return
        when {
            httpCode == 401 || httpCode == 403 -> disabledReason = "RPDB rechazó la clave ($httpCode): se usan los pósteres de TMDB."
            httpCode == null -> Unit
            successes == 0 && ++failures >= maxFailuresWithoutSuccess ->
                disabledReason = "RPDB no devolvió pósteres tras $maxFailuresWithoutSuccess intentos (código $httpCode): se usan los de TMDB."
        }
    }

    @Synchronized fun reset() {
        failures = 0
        successes = 0
        disabledReason = null
    }
}
