package com.opentv.app.stream

/** Qué hacer con la respuesta del TV al comando Play. Lógica pura. */
enum class PlayOutcome {
    /** El TV confirmó. */
    CONFIRMED,

    /** El TV no confirmó (o demoró), pero ya está descargando el video: se sigue transmitiendo. */
    TV_IS_PULLING,

    /** No hay señales de que el TV vaya a reproducir: se corta y se avisa. */
    FAILED;

    companion object {
        /**
         * @param accepted el comando terminó bien.
         * @param tvIsPulling el TV ya hizo un GET del video y recibió datos.
         */
        fun decide(accepted: Boolean, tvIsPulling: Boolean): PlayOutcome = when {
            accepted -> CONFIRMED
            tvIsPulling -> TV_IS_PULLING
            else -> FAILED
        }
    }
}
