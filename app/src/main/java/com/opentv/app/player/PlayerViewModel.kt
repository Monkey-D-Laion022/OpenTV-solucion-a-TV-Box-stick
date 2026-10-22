package com.opentv.app.player

import android.app.Application
import android.view.Display
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.opentv.app.OpenTvApp
import com.opentv.app.connect.ExternalDisplay
import com.opentv.app.connect.TvTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerUiState(
    /** Enlace en reproducción o `null` si todavía no se eligió ninguno. */
    val currentUrl: String? = null,
    val fit: VideoFit = VideoFit.FIT,
    val error: String? = null,
    /** El video se está mostrando en la pantalla externa (HDMI o inalámbrica) y no en el celular. */
    val onExternal: Boolean = false,
    val paused: Boolean = false,
    /** Nombre del TV que está reproduciendo el último enlace enviado, o `null`. */
    val castingTo: String? = null,
    /** Último mensaje sobre el envío al TV (éxito o error). */
    val castMessage: String? = null,
    val sending: Boolean = false,
)

/**
 * Dueño del único ExoPlayer de la app. Se crea al primer uso y se libera al salir de la pantalla
 * ([onCleared]): en teléfonos con poca RAM no se deja un reproductor vivo en segundo plano.
 */
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as OpenTvApp).container

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    /** Pantalla externa real; el registro de DisplayManager solo existe con la pantalla visible. */
    val externalDisplay: StateFlow<ExternalDisplay?> = container.displayMonitor.observe().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = null,
    )

    /** TV elegido como salida: si hay uno, reproducir envía el enlace allí en vez de usar el teléfono. */
    val outputTarget: StateFlow<TvTarget?> get() = container.output.target

    private var lastUrl: String? = null

    val initialLink: String get() = container.sharedLink.value

    fun setLink(value: String) {
        container.sharedLink.value = value
    }

    private var exo: ExoPlayer? = null

    private val listener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            _state.update { it.copy(error = describe(error)) }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            _state.update { it.copy(paused = !playWhenReady) }
        }
    }

    /** El reproductor que debe mostrar la vista. Se crea la primera vez que se pide. */
    fun player(): ExoPlayer = exo ?: ExoPlayer.Builder(getApplication<Application>())
        .build()
        .also {
            it.addListener(listener)
            exo = it
        }

    /** La pantalla externa como objeto de Android, o `null` si no hay ninguna. */
    fun externalDisplayObject(): Display? = container.displayMonitor.currentDisplay()

    fun play(rawUrl: String) {
        when (val check = PlayableUrl.check(rawUrl)) {
            is PlayableUrlCheck.Invalid -> _state.update { it.copy(error = check.reason) }
            is PlayableUrlCheck.Valid -> {
                lastUrl = check.url
                val target = container.output.target.value
                if (target != null) {
                    sendToTarget(check.url, target)
                    return
                }
                val p = player()
                p.setMediaItem(MediaItem.fromUri(check.url))
                p.prepare()
                p.playWhenReady = true
                _state.update { it.copy(currentUrl = check.url, error = null, paused = false, castingTo = null, castMessage = null) }
            }
        }
    }

    private fun sendToTarget(url: String, target: TvTarget) {
        exo?.pause()
        _state.update { it.copy(sending = true, error = null, castMessage = "Enviando a ${target.name}…") }
        viewModelScope.launch {
            val result = try {
                container.output.send(url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                com.opentv.app.connect.OutputResult(false, "Falló el envío (${e.javaClass.simpleName}).")
            }
            _state.update {
                it.copy(
                    sending = false,
                    castMessage = result.message,
                    castingTo = if (result.ok) target.name else null,
                    currentUrl = if (result.ok) url else it.currentUrl,
                )
            }
        }
    }

    /** Detiene la reproducción en el TV y cierra la salida. */
    fun stopOnTv() {
        viewModelScope.launch {
            val result = container.output.stopPlayback()
            container.output.clear()
            _state.update { it.copy(castingTo = null, castMessage = result.message) }
        }
    }

    /** Deja de usar el TV y reproduce el último enlace en el teléfono. */
    fun playHere() {
        val url = lastUrl ?: return
        container.output.clear()
        _state.update { it.copy(castingTo = null, castMessage = null) }
        play(url)
    }

    fun stop() {
        exo?.stop()
        exo?.clearMediaItems()
        _state.update { it.copy(currentUrl = null, error = null, onExternal = false) }
    }

    fun pause() {
        exo?.pause()
    }

    fun togglePause() {
        val p = exo ?: return
        p.playWhenReady = !p.playWhenReady
    }

    fun setOnExternal(value: Boolean) {
        _state.update { it.copy(onExternal = value) }
    }

    fun reportError(message: String) {
        _state.update { it.copy(error = message, onExternal = false) }
    }

    fun cycleFit() {
        _state.update { it.copy(fit = it.fit.next()) }
    }

    override fun onCleared() {
        exo?.removeListener(listener)
        exo?.release()
        exo = null
    }

    private fun describe(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
            "No se pudo conectar con el servidor del video. Revisá tu conexión y el enlace."
        PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED ->
            "Este enlace usa http sin cifrar y el sistema lo bloqueó."
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
            "El servidor rechazó el pedido (enlace vencido, sin permiso o inexistente)."
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
            "El video no existe en esa dirección."
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED ->
            "El archivo no se pudo leer o su formato no está soportado."
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ->
            "Este teléfono no puede decodificar ese video (códec o resolución no soportados)."
        else -> "No se pudo reproducir (${error.errorCodeName})."
    }
}
