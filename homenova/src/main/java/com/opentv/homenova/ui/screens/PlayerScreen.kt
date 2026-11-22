package com.opentv.homenova.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.KeyEvent as AndroidKeyEvent
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.opentv.homenova.core.MediaEntry
import com.opentv.homenova.ui.components.DialogAction
import com.opentv.homenova.ui.components.FocusableCard
import com.opentv.homenova.ui.components.IconAction
import com.opentv.homenova.ui.components.NovaButton
import com.opentv.homenova.ui.components.NovaDialog
import com.opentv.homenova.ui.components.Pill
import com.opentv.homenova.ui.components.ProgressLine
import com.opentv.homenova.ui.theme.LocalNovaPalette
import kotlinx.coroutines.delay
import java.util.Locale

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    media: MediaEntry,
    onBack: () -> Unit,
    onNowPlayingChanged: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalNovaPalette.current
    val context = LocalContext.current
    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var buffering by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    var speed by remember { mutableFloatStateOf(1f) }
    var showTracks by remember { mutableStateOf(false) }
    var seekNotice by remember { mutableStateOf<String?>(null) }
    var interaction by remember { mutableIntStateOf(0) }

    val activity = remember(context) { context.findActivity() }
    DisposableEffect(Unit) {
        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    DisposableEffect(media.id) {
        val player = ExoPlayer.Builder(context).build()
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    durationMs = player.duration.coerceAtLeast(0L)
                }
            }

            override fun onPlayerError(playerError: PlaybackException) {
                error = playerError.localizedMessage ?: "No se ha podido reproducir el contenido."
                buffering = false
            }
        })
        val url = media.videoUrl
        if (url != null) {
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
            player.playWhenReady = true
        } else {
            error = "Este contenido no tiene una fuente de vídeo configurada."
            buffering = false
        }
        exoPlayer = player
        onNowPlayingChanged(media.title)
        onDispose {
            player.release()
            exoPlayer = null
            onNowPlayingChanged(null)
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            exoPlayer?.let { player ->
                positionMs = player.currentPosition.coerceAtLeast(0L)
                if (player.duration > 0) durationMs = player.duration
            }
            delay(500)
        }
    }

    // Whenever the controls (re)appear, move focus onto them. Doing this inside the
    // key handler is too early: the controls are not composed until the next frame.
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            delay(120)
            runCatching { playFocus.requestFocus() }
        }
    }

    LaunchedEffect(controlsVisible, isPlaying, interaction) {
        if (controlsVisible && isPlaying) {
            delay(4_800)
            controlsVisible = false
            runCatching { rootFocus.requestFocus() }
        }
    }

    fun togglePlayback() {
        val player = exoPlayer ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekBy(deltaMs: Long) {
        val player = exoPlayer ?: return
        val target = (player.currentPosition + deltaMs).coerceIn(0L, durationMs.coerceAtLeast(0L))
        player.seekTo(target)
        positionMs = target
        seekNotice = if (deltaMs > 0) "+${deltaMs / 1000} s" else "${deltaMs / 1000} s"
    }

    BackHandler {
        if (controlsVisible) controlsVisible = false else onBack()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) interaction++
                if (!controlsVisible) {
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.nativeKeyEvent.keyCode) {
                        AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                        AndroidKeyEvent.KEYCODE_ENTER,
                        AndroidKeyEvent.KEYCODE_DPAD_DOWN,
                        AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                            controlsVisible = true
                            runCatching { playFocus.requestFocus() }
                            true
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                            seekBy(-10_000)
                            true
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                            seekBy(30_000)
                            true
                        }
                        AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            togglePlayback()
                            true
                        }
                        AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                            seekBy(30_000)
                            true
                        }
                        AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> {
                            seekBy(-10_000)
                            true
                        }
                        else -> false
                    }
                } else {
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.nativeKeyEvent.keyCode) {
                        AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            togglePlayback()
                            true
                        }
                        AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                            seekBy(30_000)
                            true
                        }
                        AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> {
                            seekBy(-10_000)
                            true
                        }
                        else -> false
                    }
                }
            }
            .focusRequester(rootFocus)
            .focusable(),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    keepScreenOn = true
                }
            },
            update = { view -> view.player = exoPlayer },
        )

        if (buffering && error == null) {
            CircularProgressIndicator(
                color = palette.accent,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(44.dp),
            )
        }

        if (seekNotice != null) {
            LaunchedEffect(seekNotice) {
                delay(900)
                seekNotice = null
            }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            ) {
                Text(
                    text = seekNotice.orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                )
            }
        }

        if (controlsVisible) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.72f),
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.86f),
                            ),
                        ),
                    )
                    .padding(horizontal = 36.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    IconAction(
                        icon = Icons.Rounded.ArrowBack,
                        label = "Volver",
                        onSelect = onBack,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = media.title,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (media.isLive) {
                                "EN VIVO · ${media.liveProgram ?: media.subtitle}"
                            } else {
                                "${media.year} · ${media.rating} · ${media.genres.joinToString(" / ")}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    }
                    if (media.isLive) {
                        Pill(text = "EN VIVO", color = palette.danger, filled = true)
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlayerControl(
                            icon = Icons.Rounded.Replay10,
                            label = "Retroceder 10 segundos",
                            onSelect = { seekBy(-10_000) },
                        )
                        PlayerControl(
                            icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            label = if (isPlaying) "Pausa" else "Reproducir",
                            large = true,
                            onSelect = { togglePlayback() },
                            modifier = Modifier.focusRequester(playFocus),
                        )
                        PlayerControl(
                            icon = Icons.Rounded.Forward30,
                            label = "Avanzar 30 segundos",
                            onSelect = { seekBy(30_000) },
                        )
                        Spacer(Modifier.width(20.dp))
                        PlayerControl(
                            icon = Icons.Rounded.Subtitles,
                            label = "Subtítulos y pistas",
                            onSelect = { showTracks = true },
                        )
                        PlayerControl(
                            icon = Icons.Rounded.Speed,
                            label = "Velocidad ${speed}x",
                            onSelect = {
                                speed = when (speed) {
                                    0.75f -> 1f
                                    1f -> 1.25f
                                    1.25f -> 1.5f
                                    else -> 0.75f
                                }
                                exoPlayer?.setPlaybackSpeed(speed)
                            },
                        )
                    }

                    if (!media.isLive) {
                        Column(Modifier.fillMaxWidth()) {
                            ProgressLine(
                                progress = if (durationMs > 0) {
                                    positionMs.toFloat() / durationMs.toFloat()
                                } else {
                                    0f
                                },
                                height = 4.dp,
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 7.dp),
                            ) {
                                Text(
                                    text = formatTime(positionMs),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.78f),
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = formatTime(durationMs),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.78f),
                                )
                            }
                        }
                        Text(
                            text = "OK muestra controles · ←/→ busca · ATRÁS vuelve",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.42f),
                        )
                    } else {
                        Text(
                            text = "OK muestra controles · ATRÁS vuelve",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.42f),
                        )
                    }
                }
            }
        }

        if (error != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xF214161A), RoundedCornerShape(22.dp))
                    .padding(30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = palette.danger,
                    modifier = Modifier.size(38.dp),
                )
                Text(
                    text = "No se pudo reproducir",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                )
                Text(
                    text = error.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.68f),
                    modifier = Modifier.width(440.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NovaButton(
                        label = "Reintentar",
                        primary = true,
                        onSelect = {
                            error = null
                            exoPlayer?.prepare()
                            exoPlayer?.play()
                        },
                    )
                    NovaButton(
                        label = "Volver",
                        primary = false,
                        onSelect = onBack,
                    )
                }
            }
        }

        if (showTracks) {
            val player = exoPlayer
            val groups = player?.currentTracks?.groups.orEmpty()
            val actions = mutableListOf<DialogAction>()
            val textGroups = groups.filter { it.type == C.TRACK_TYPE_TEXT }
            textGroups.forEachIndexed { index, group ->
                val format = group.getTrackFormat(0)
                actions += DialogAction(
                    label = "Subtítulos: ${format.label ?: format.language ?: "Pista ${index + 1}"}",
                    icon = Icons.Rounded.Subtitles,
                    onClick = {
                        showTracks = false
                        player?.let { active ->
                            active.trackSelectionParameters = active.trackSelectionParameters
                                .buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                .setOverrideForType(
                                    TrackSelectionOverride(group.mediaTrackGroup, 0),
                                )
                                .build()
                        }
                    },
                )
            }
            actions += DialogAction(
                label = "Desactivar subtítulos",
                icon = Icons.Rounded.Subtitles,
                onClick = {
                    showTracks = false
                    player?.let { active ->
                        active.trackSelectionParameters = active.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                            .build()
                    }
                },
            )
            groups.filter { it.type == C.TRACK_TYPE_AUDIO }.forEachIndexed { index, group ->
                val format = group.getTrackFormat(0)
                actions += DialogAction(
                    label = "Audio: ${format.label ?: format.language ?: "Pista ${index + 1}"}",
                    icon = Icons.Rounded.Audiotrack,
                    onClick = {
                        showTracks = false
                        player?.let { active ->
                            active.trackSelectionParameters = active.trackSelectionParameters
                                .buildUpon()
                                .setOverrideForType(
                                    TrackSelectionOverride(group.mediaTrackGroup, 0),
                                )
                                .build()
                        }
                    },
                )
            }
            if (actions.isEmpty()) {
                actions += DialogAction(
                    label = "Sin pistas alternativas",
                    onClick = { showTracks = false },
                )
            }
            NovaDialog(
                title = "Pistas de reproducción",
                message = "Elige una pista de audio o activa los subtítulos disponibles.",
                onDismiss = { showTracks = false },
                actions = actions,
            )
        }
    }
}

@Composable
private fun PlayerControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    val palette = LocalNovaPalette.current
    FocusableCard(
        onSelect = onSelect,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        focusedScale = 1.1f,
        focusedElevation = 12.dp,
        background = Color.White.copy(alpha = 0.12f),
    ) {
        Box(
            modifier = Modifier.size(if (large) 62.dp else 46.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(if (large) 30.dp else 23.dp),
            )
        }
    }
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
