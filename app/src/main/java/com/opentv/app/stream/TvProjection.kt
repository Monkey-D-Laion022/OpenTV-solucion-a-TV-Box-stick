package com.opentv.app.stream

import android.app.Presentation
import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.view.Display
import com.opentv.app.connect.DidlLite
import com.opentv.app.connect.DlnaController
import com.opentv.app.connect.DlnaMessages
import com.opentv.app.connect.DlnaResult
import com.opentv.app.connect.DlnaSoap
import com.opentv.app.connect.LanAddress
import com.opentv.app.connect.UpnpDevice
import java.io.IOException
import java.net.Inet4Address
import java.security.SecureRandom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface ProjectionState {
    data object Idle : ProjectionState
    data object Starting : ProjectionState
    data class Streaming(val url: String, val deviceName: String) : ProjectionState
    data class Failed(val message: String) : ProjectionState
}

/**
 * Proyecta la interfaz de OpenTV en un TV DLNA, sin cables y sin la función «Emitir» de Android:
 * dibuja la interfaz en una pantalla virtual propia de 1920×1080 (16:9), la codifica en H.264, la sirve
 * por HTTP en la red local como MPEG-TS y le pide al TV que abra ese enlace.
 *
 * Lo que se ve en el TV llega con unos segundos de retraso (el TV guarda un búfer): sirve para
 * navegar, no para juegos. Debe llamarse desde el hilo principal.
 */
class TvProjection(
    context: Context,
    private val dlna: DlnaController,
) {
    private val displayManager = context.applicationContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    private val _state = MutableStateFlow<ProjectionState>(ProjectionState.Idle)
    val state: StateFlow<ProjectionState> = _state.asStateFlow()

    private class Session(
        val hub: StreamHub,
        val server: HttpStreamServer,
        val encoder: ScreenEncoder,
        val virtualDisplay: VirtualDisplay,
        val presentation: Presentation,
        val target: UpnpDevice,
    )

    private var session: Session? = null

    /**
     * Arma todo y le pide al TV que reproduzca. [createPresentation] construye la ventana con la
     * interfaz que se verá en el TV, sobre la pantalla virtual que se le entrega.
     */
    suspend fun start(target: UpnpDevice, createPresentation: (Display) -> Presentation) {
        if (_state.value is ProjectionState.Starting || _state.value is ProjectionState.Streaming) return
        _state.value = ProjectionState.Starting
        release()

        val ip = LanIp.pick()
        if (ip == null) {
            fail("No hay una red Wi‑Fi local activa. Conectá el teléfono a la misma red que el TV.")
            return
        }

        val hub = StreamHub()
        val token = randomToken()
        val server = HttpStreamServer(hub, token, acceptClient = ::isLanClient)
        var encoder: ScreenEncoder? = null
        var virtualDisplay: VirtualDisplay? = null
        var presentation: Presentation? = null
        try {
            val port = server.start(ip)
            val enc = ScreenEncoder { chunk, keyStart -> hub.publish(chunk, keyStart) }
            encoder = enc
            val surface = enc.start()
            val vd = displayManager.createVirtualDisplay(
                "OpenTV-TV", WIDTH, HEIGHT, DENSITY_DPI, surface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION or
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
            )
            if (vd == null) {
                throw IllegalStateException("Android no creó la pantalla virtual.")
            }
            virtualDisplay = vd
            val shown = createPresentation(vd.display)
            presentation = shown
            shown.show()

            val url = "http://${ip.hostAddress}:$port${server.path}"
            session = Session(hub, server, enc, vd, shown, target)

            val result = dlna.play(target, url, DidlLite.forUrl(url, "OpenTV"))
            if (result == DlnaResult.Ok) {
                _state.value = ProjectionState.Streaming(url, target.friendlyName)
            } else {
                release()
                fail(DlnaMessages.describe(target.friendlyName, DlnaSoap.ACTION_PLAY, result))
            }
        } catch (e: IOException) {
            cleanup(server, encoder, virtualDisplay, presentation)
            fail("No se pudo abrir el servidor de video local (${e.message ?: "error de red"}).")
        } catch (e: RuntimeException) {
            cleanup(server, encoder, virtualDisplay, presentation)
            session = null
            fail("No se pudo iniciar la proyección (${e.javaClass.simpleName}: ${e.message ?: "sin detalle"}).")
        }
    }

    /** Pide al TV que pare y libera todo. */
    suspend fun stop() {
        val current = session
        if (current != null) {
            // Si el TV no responde igual se libera todo: no se deja nada vivo.
            dlna.stop(current.target)
        }
        release()
    }

    /** Libera todo sin hablar con el TV; se usa al cerrar la app. */
    fun shutdown() = release()

    private fun release() {
        val current = session
        session = null
        if (current != null) cleanup(current.server, current.encoder, current.virtualDisplay, current.presentation)
        _state.value = ProjectionState.Idle
    }

    private fun cleanup(
        server: HttpStreamServer?,
        encoder: ScreenEncoder?,
        virtualDisplay: VirtualDisplay?,
        presentation: Presentation?,
    ) {
        runCatching { presentation?.dismiss() }
        runCatching { virtualDisplay?.release() }
        runCatching { encoder?.stop() }
        runCatching { server?.stop() }
    }

    private fun fail(message: String) {
        _state.value = ProjectionState.Failed(message)
    }

    private fun isLanClient(address: java.net.InetAddress): Boolean =
        address is Inet4Address && LanAddress.isPrivateIpv4(address.hostAddress.orEmpty())

    private fun randomToken(): String {
        val bytes = ByteArray(12)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val WIDTH = 1920
        const val HEIGHT = 1080
        const val DENSITY_DPI = 240
    }
}
