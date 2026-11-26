package com.opentv.app.miracast

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Dueño de la sesión Wi‑Fi Direct / Miracast, a nivel de la aplicación (no de la pantalla).
 *
 * Ronda 7: la sesión vivía en el ViewModel de la pantalla de Wi‑Fi Direct y se cancelaba al salir de esa sección (DisposableEffect +
 * onCleared), aunque la transmisión seguía siendo válida. Ahora la sesión sobrevive a la navegación: solo se corta con «Cancelar prueba»,
 * cuando el televisor cierra, o si falla. IptvService (primer plano) mantiene vivo el proceso mientras se transmite.
 */
object WfdSessionHolder {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @Volatile private var session: WfdDiagnosticSession? = null

    fun get(app: Application): WfdDiagnosticSession =
        session ?: synchronized(this) { session ?: WfdDiagnosticSession(app, scope).also { session = it } }
}
