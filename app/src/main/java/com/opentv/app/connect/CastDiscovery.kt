package com.opentv.app.connect

import android.content.Context
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Un dispositivo Google Cast detectado en la red. */
data class CastDevice(val id: String, val name: String)

/**
 * Busca dispositivos Google Cast con el SDK oficial (CastContext + MediaRouter). Cast ENVÍA
 * contenido al receptor; no duplica la pantalla. Hoy solo detecta: el envío llega junto con el
 * reproductor (Fase 4).
 *
 * MediaRouter exige hilo principal: recolectar este flujo desde `viewModelScope` (Main). La
 * búsqueda activa gasta batería, así que solo vive mientras se recolecta (pantalla visible).
 * Si Play Services falla, el flujo termina con una excepción que el llamador debe capturar.
 */
class CastDiscovery(private val context: Context) {

    fun devices(): Flow<List<CastDevice>> = callbackFlow {
        // Inicializa el SDK de Cast (usa CastOptionsProvider, declarado en el manifest).
        CastContext.getSharedInstance(context)

        val router = MediaRouter.getInstance(context)
        val selector = MediaRouteSelector.Builder()
            .addControlCategory(
                CastMediaControlIntent.categoryForCast(
                    CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID,
                ),
            )
            .build()

        fun snapshot(): List<CastDevice> = router.routes
            .filter { !it.isDefault && it.matchesSelector(selector) }
            .map { CastDevice(id = it.id, name = it.name) }
            .distinctBy { it.id }
            .sortedBy { it.name }

        val callback = object : MediaRouter.Callback() {
            override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) {
                trySend(snapshot())
            }

            override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) {
                trySend(snapshot())
            }

            override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
                trySend(snapshot())
            }
        }
        router.addCallback(selector, callback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
        trySend(snapshot())
        awaitClose { router.removeCallback(callback) }
    }
}
