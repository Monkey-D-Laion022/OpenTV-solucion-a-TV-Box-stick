package com.opentv.app.connect

import android.content.Context
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import com.opentv.app.player.PlayableUrl
import com.opentv.app.player.PlayableUrlCheck
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

sealed interface CastSendResult {
    data class Ok(val deviceName: String) : CastSendResult
    data class Failed(val reason: String) : CastSendResult
}

/**
 * Conecta con un dispositivo Google Cast y le envía enlaces, con el SDK oficial. [connect] abre la
 * sesión (sin enviar nada); [play] la abre si hace falta y carga el video. El TV descarga el video
 * por su cuenta, así que lo muestra en su resolución original. NO duplica la pantalla.
 *
 * Debe llamarse desde el hilo principal, con la búsqueda de [CastDiscovery] activa (si no, la
 * ruta no existe). El receptor tiene que poder abrir el enlace desde su propia conexión.
 */
class CastSender(private val context: Context) {

    /** Abre la sesión con el dispositivo (si ya estaba abierta con él, no hace nada). */
    suspend fun connect(deviceId: String): CastSendResult =
        when (val ensured = ensureSession(deviceId)) {
            is Ensured.Failed -> CastSendResult.Failed(ensured.reason)
            is Ensured.Ok -> CastSendResult.Ok(ensured.name)
        }

    suspend fun play(deviceId: String, rawUrl: String): CastSendResult {
        val url = when (val check = PlayableUrl.check(rawUrl)) {
            is PlayableUrlCheck.Invalid -> return CastSendResult.Failed(check.reason)
            is PlayableUrlCheck.Valid -> check.url
        }
        return when (val ensured = ensureSession(deviceId)) {
            is Ensured.Failed -> CastSendResult.Failed(ensured.reason)
            is Ensured.Ok -> load(ensured.session, url, ensured.name)
        }
    }

    /** Cierra la sesión Cast actual, si la hay. */
    fun stop() {
        try {
            CastContext.getSharedInstance(context).sessionManager.endCurrentSession(true)
        } catch (e: RuntimeException) {
            // Sin Cast disponible no hay sesión que cerrar.
        }
    }

    private sealed interface Ensured {
        data class Ok(val session: CastSession, val name: String) : Ensured
        data class Failed(val reason: String) : Ensured
    }

    private sealed interface Started {
        data class Ok(val session: CastSession) : Started
        data class Failed(val code: Int) : Started
    }

    private suspend fun ensureSession(deviceId: String): Ensured {
        val sessions = try {
            CastContext.getSharedInstance(context).sessionManager
        } catch (e: RuntimeException) {
            return Ensured.Failed("No se pudo iniciar Google Cast en este equipo.")
        }
        val route = MediaRouter.getInstance(context).routes.firstOrNull { it.id == deviceId }
            ?: return Ensured.Failed("El dispositivo ya no aparece en la red. Esperá a que vuelva a aparecer.")

        val current = sessions.currentCastSession
        if (current != null && current.isConnected) {
            return if (current.castDevice?.friendlyName == route.name) {
                Ensured.Ok(current, route.name)
            } else {
                // Otra sesión abierta: se cierra y se vuelve a intentar con el TV elegido.
                sessions.endCurrentSession(true)
                Ensured.Failed("Se cerró la sesión anterior. Tocá el TV otra vez para conectar con ${route.name}.")
            }
        }

        return when (val started = withTimeoutOrNull(CONNECT_TIMEOUT_MS) { startSession(sessions, route) }) {
            null -> Ensured.Failed("El dispositivo no respondió a tiempo. Verificá que esté encendido y en tu red.")
            is Started.Failed -> Ensured.Failed("No se pudo conectar con ${route.name} (código ${started.code}).")
            is Started.Ok -> Ensured.Ok(started.session, route.name)
        }
    }

    private suspend fun startSession(sessions: SessionManager, route: MediaRouter.RouteInfo): Started =
        suspendCancellableCoroutine { cont ->
            val listener = object : SessionManagerListener<CastSession> {
                private fun finish(result: Started) {
                    sessions.removeSessionManagerListener(this, CastSession::class.java)
                    if (cont.isActive) cont.resume(result)
                }

                override fun onSessionStarted(session: CastSession, sessionId: String) = finish(Started.Ok(session))
                override fun onSessionStartFailed(session: CastSession, error: Int) = finish(Started.Failed(error))
                override fun onSessionStarting(session: CastSession) = Unit
                override fun onSessionEnding(session: CastSession) = Unit
                override fun onSessionEnded(session: CastSession, error: Int) = Unit
                override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
                override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = finish(Started.Ok(session))
                override fun onSessionResumeFailed(session: CastSession, error: Int) = finish(Started.Failed(error))
                override fun onSessionSuspended(session: CastSession, reason: Int) = Unit
            }
            sessions.addSessionManagerListener(listener, CastSession::class.java)
            cont.invokeOnCancellation {
                sessions.removeSessionManagerListener(listener, CastSession::class.java)
            }
            MediaRouter.getInstance(context).selectRoute(route)
        }

    private suspend fun load(session: CastSession, url: String, name: String): CastSendResult {
        val client = session.remoteMediaClient
            ?: return CastSendResult.Failed("La sesión con $name no permite cargar video.")
        val media = MediaInfo.Builder(url)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(CastMime.guess(url))
            .build()
        val request = MediaLoadRequestData.Builder()
            .setMediaInfo(media)
            .setAutoplay(true)
            .build()
        return withTimeoutOrNull(LOAD_TIMEOUT_MS) {
            suspendCancellableCoroutine<CastSendResult> { cont ->
                client.load(request).setResultCallback { result ->
                    if (cont.isActive) {
                        cont.resume(
                            if (result.status.isSuccess) {
                                CastSendResult.Ok(name)
                            } else {
                                CastSendResult.Failed(
                                    "$name rechazó el video (código ${result.status.statusCode}). " +
                                        "Puede que no pueda abrir ese enlace o ese formato.",
                                )
                            },
                        )
                    }
                }
            }
        } ?: CastSendResult.Failed("$name no respondió a la orden de reproducir.")
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000L
        const val LOAD_TIMEOUT_MS = 15_000L
    }
}
