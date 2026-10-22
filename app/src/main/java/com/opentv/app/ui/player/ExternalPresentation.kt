package com.opentv.app.ui.player

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.opentv.app.player.VideoFit

/** Ventana que Android dibuja SOLO en la pantalla externa, con el video a pantalla completa. */
@OptIn(UnstableApi::class)
private class VideoPresentation(
    context: Context,
    display: Display,
    private val exo: ExoPlayer,
    private var fit: VideoFit,
) : Presentation(context, display) {

    private var view: PlayerView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        view = PlayerView(context).apply {
            useController = false
            resizeMode = fit.resizeMode()
            player = exo
        }
        setContentView(view!!)
    }

    fun setFit(value: VideoFit) {
        fit = value
        view?.resizeMode = value.resizeMode()
    }

    /** Suelta el reproductor antes de cerrar para que otra vista pueda volver a usarlo. */
    fun release() {
        view?.player = null
        dismiss()
    }
}

@OptIn(UnstableApi::class)
internal fun VideoFit.resizeMode(): Int = when (this) {
    VideoFit.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    VideoFit.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    VideoFit.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
}

/**
 * Mientras [active] sea verdadero y haya [display], el video se dibuja en la pantalla externa a
 * su resolución nativa (por eso llena un TV 16:9 sin las barras del espejo del teléfono). Si la
 * ventana no se puede abrir, se avisa con [onFailed] y el video vuelve al celular.
 */
@Composable
fun ExternalPresentation(
    active: Boolean,
    display: Display?,
    player: () -> ExoPlayer,
    fit: VideoFit,
    onFailed: (String) -> Unit,
) {
    val context = LocalContext.current
    val holder = remember { arrayOfNulls<VideoPresentation>(1) }

    DisposableEffect(active, display?.displayId) {
        if (active && display != null) {
            holder[0] = try {
                VideoPresentation(context, display, player(), fit).also { it.show() }
            } catch (e: RuntimeException) {
                onFailed("No se pudo abrir la pantalla externa (${e.javaClass.simpleName}).")
                null
            }
        }
        onDispose {
            holder[0]?.release()
            holder[0] = null
        }
    }
    LaunchedEffect(fit) { holder[0]?.setFit(fit) }
}
