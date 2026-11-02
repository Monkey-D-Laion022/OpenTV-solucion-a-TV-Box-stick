package com.opentv.app.miracast

/**
 * Decide cómo seguir cuando `WifiP2pManager.connect()` falla o antes de llamarlo. Lógica pura (se prueba sin Android).
 *
 * Contexto: en el diagnóstico anterior `connect()` devolvía el código 0 («error interno») casi al instante. La
 * secuencia usada conectaba sobre un canal recién creado sin haber descubierto el dispositivo en ESE canal, y sin limpiar
 * solicitudes o grupos previos; en varios equipos Android eso termina en error 0. La secuencia nueva limpia, descubre,
 * espera a ver el par con su estado y recién entonces conecta; si falla con 0 o con «ocupado» reinicia el canal y reintenta
 * un número acotado de veces. No hay certeza de que resuelva el problema: lo que se prueba aquí es la decisión, no el
 * comportamiento del sistema Android ni del televisor.
 */
class P2pConnectPlanner(private val maxBusyWaits: Int = 3, private val maxResets: Int = 2) {
    enum class Kind { WAIT_AND_RETRY, RESET_AND_RETRY, GIVE_UP }
    data class Decision(val kind: Kind, val delayMs: Long, val note: String)
    enum class PeerAction { WAIT, CONNECT }
    data class PeerCheck(val action: PeerAction, val note: String)

    var busyWaits = 0
        private set
    var resets = 0
        private set

    /** Tiempo máximo de espera a que el par aparezca en la lista del canal antes de intentar conectar igual. */
    val peerWaitMs: Long get() = PEER_WAIT_MS

    fun onPeerCheck(found: Boolean, status: Int?, waitedMs: Long): PeerCheck = when {
        found && status == STATUS_AVAILABLE -> PeerCheck(PeerAction.CONNECT, "El televisor aparece disponible en la lista de este canal.")
        found && status == STATUS_INVITED -> PeerCheck(PeerAction.CONNECT, "El televisor ya figura invitado: se envía la solicitud.")
        found && status == STATUS_UNAVAILABLE -> if (waitedMs >= PEER_WAIT_MS)
            PeerCheck(PeerAction.CONNECT, "El televisor figura NO disponible (ocupado con otro grupo o con Screen Share cerrado); se intenta igual.")
        else PeerCheck(PeerAction.WAIT, "El televisor figura no disponible; se espera a que cambie.")
        found -> PeerCheck(PeerAction.CONNECT, "El televisor figura con estado ${statusName(status)}; se envía la solicitud.")
        waitedMs >= PEER_WAIT_MS -> PeerCheck(PeerAction.CONNECT, "El televisor no apareció en ${PEER_WAIT_MS / 1000} s en este canal; se intenta conectar igual para registrar la respuesta.")
        else -> PeerCheck(PeerAction.WAIT, "Esperando que el televisor aparezca en la lista de este canal…")
    }

    fun onConnectFailure(reason: Int): Decision = when (reason) {
        REASON_BUSY -> if (busyWaits < maxBusyWaits) {
            busyWaits++
            Decision(Kind.WAIT_AND_RETRY, 500, "Sistema ocupado (código 2): reintento $busyWaits de $maxBusyWaits sin reiniciar.")
        } else resetOrGiveUp("Sistema ocupado persistente (código 2)")
        REASON_ERROR -> resetOrGiveUp("Error interno (código 0)")
        else -> Decision(Kind.GIVE_UP, 0, "Código $reason: no se reintenta.")
    }

    private fun resetOrGiveUp(what: String): Decision =
        if (resets < maxResets) {
            resets++
            Decision(Kind.RESET_AND_RETRY, 1_000, "$what: se reinicia el canal Wi‑Fi Direct y se repite la secuencia (reinicio $resets de $maxResets).")
        } else Decision(Kind.GIVE_UP, 0, "$what: se agotaron los $maxResets reinicios.")

    companion object {
        const val STATUS_CONNECTED = 0
        const val STATUS_INVITED = 1
        const val STATUS_FAILED = 2
        const val STATUS_AVAILABLE = 3
        const val STATUS_UNAVAILABLE = 4
        const val REASON_ERROR = 0
        const val REASON_BUSY = 2
        const val PEER_WAIT_MS = 10_000L

        fun statusName(status: Int?): String = when (status) {
            STATUS_CONNECTED -> "conectado"; STATUS_INVITED -> "invitado"; STATUS_FAILED -> "fallido"
            STATUS_AVAILABLE -> "disponible"; STATUS_UNAVAILABLE -> "no disponible"; else -> "desconocido"
        }

        /** Explicación final cuando se agotan los reintentos. Separa lo medido de las hipótesis y de un límite real de Android. */
        fun explainFailure(reason: Int, resets: Int, sawPeer: Boolean, peerStatus: Int?): String {
            val seen = if (sawPeer) "El televisor SÍ estaba en la lista de este canal (estado ${statusName(peerStatus)})." else "El televisor NO apareció en la lista de este canal."
            val base = "Tras $resets reinicio(s) del canal, connect() siguió fallando con el código $reason. $seen"
            val causes = when (reason) {
                REASON_ERROR -> " Hipótesis (no demostradas): el televisor no acepta invitaciones Wi‑Fi Direct de una app común, tiene Screen Share cerrado o " +
                    "ocupado, o el teléfono tiene el punto de acceso/ahorro de energía interfiriendo."
                REASON_BUSY -> " El sistema sigue ocupado: cerrá Pantalla inalámbrica del sistema y cualquier otra sesión Wi‑Fi Direct y repetí."
                else -> ""
            }
            val limit = " Límite de Android demostrado por su API pública: una app no puede anunciarse como fuente Wi‑Fi Display (WFD) en la negociación; eso " +
                "solo lo hace el sistema (WifiP2pWfdInfo es API oculta). Por eso el espejo nativo del sistema sigue siendo la vía Miracast; esta prueba solo observa."
            return base + causes + limit
        }
    }
}
