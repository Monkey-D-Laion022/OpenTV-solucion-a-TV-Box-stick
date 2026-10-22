package com.opentv.app.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PlayerUiState(
    /** Enlace en reproducción o `null` si todavía no se eligió ninguno. */
    val currentUrl: String? = null,
    val fit: VideoFit = VideoFit.FIT,
    val error: String? = null,
)

/**
 * Dueño del único ExoPlayer de la app. Se crea al primer uso y se libera al salir de la pantalla
 * ([onCleared]): en teléfonos con poca RAM no se deja un reproductor vivo en segundo plano.
 */
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var exo: ExoPlayer? = null

    private val listener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            _state.update { it.copy(error = describe(error)) }
        }
    }

    /** El reproductor que debe mostrar la vista. Se crea la primera vez que se pide. */
    fun player(): ExoPlayer = exo ?: ExoPlayer.Builder(getApplication<Application>())
        .build()
        .also {
            it.addListener(listener)
            exo = it
        }

    fun play(rawUrl: String) {
        when (val check = PlayableUrl.check(rawUrl)) {
            is PlayableUrlCheck.Invalid -> _state.update { it.copy(error = check.reason) }
            is PlayableUrlCheck.Valid -> {
                val p = player()
                p.setMediaItem(MediaItem.fromUri(check.url))
                p.prepare()
                p.playWhenReady = true
                _state.update { it.copy(currentUrl = check.url, error = null) }
            }
        }
    }

    fun stop() {
        exo?.stop()
        exo?.clearMediaItems()
        _state.update { it.copy(currentUrl = null, error = null) }
    }

    fun pause() {
        exo?.pause()
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
