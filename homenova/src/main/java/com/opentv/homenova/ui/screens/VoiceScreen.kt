package com.opentv.homenova.ui.screens

import android.app.Activity
import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opentv.homenova.ui.components.NovaButton
import com.opentv.homenova.ui.components.FocusableCard
import com.opentv.homenova.ui.theme.LocalNovaPalette
import com.opentv.homenova.voice.VoiceAction
import com.opentv.homenova.voice.VoiceAssistant
import com.opentv.homenova.voice.VoiceCatalog
import com.opentv.homenova.voice.VoiceCommandParser
import kotlinx.coroutines.delay

private enum class VoicePhase { LISTENING, PROCESSING, RESULT, ERROR }

/**
 * Full-screen voice experience built on RecognizerIntent.
 *
 * The callback returns a feedback string for actions that stay on this screen
 * (volume, time, unknown input) and null for actions that navigate elsewhere.
 */
@Composable
fun VoiceScreen(
    language: String,
    catalog: VoiceCatalog,
    onAction: (VoiceAction) -> String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalNovaPalette.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val available = remember { VoiceAssistant.isRecognitionAvailable(context) }
    var phase by remember { mutableStateOf(VoicePhase.LISTENING) }
    var transcript by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val retryFocus = remember { FocusRequester() }

    fun handle(action: VoiceAction) {
        val response = onAction(action)
        if (response != null) {
            feedback = response
            phase = VoicePhase.RESULT
        }
    }

    val recognizer = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data
                ?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!text.isNullOrBlank()) {
                transcript = text
                phase = VoicePhase.PROCESSING
                handle(VoiceCommandParser.parse(text, catalog))
            } else {
                phase = VoicePhase.ERROR
                error = "No se ha entendido la petición. Inténtalo de nuevo."
            }
        } else if (result.resultCode == Activity.RESULT_CANCELED) {
            phase = VoicePhase.ERROR
            error = "Escucha cancelada. Puedes volver a intentarlo o escribir tu búsqueda."
        } else {
            phase = VoicePhase.ERROR
            error = "El servicio de voz devolvió un error. Prueba de nuevo."
        }
    }

    fun listen() {
        feedback = null
        error = null
        transcript = ""
        if (!available) {
            phase = VoicePhase.ERROR
            error = "No hay un servicio de reconocimiento de voz instalado. Usa el teclado o el asistente del sistema."
            return
        }
        phase = VoicePhase.LISTENING
        try {
            recognizer.launch(VoiceAssistant.recognitionIntent(language))
        } catch (_: ActivityNotFoundException) {
            phase = VoicePhase.ERROR
            error = "No se ha encontrado una actividad de reconocimiento de voz."
        }
    }

    LaunchedEffect(Unit) {
        delay(320)
        listen()
    }

    // When the recognizer closes, hand focus back to the on-screen controls so
    // a remote-only user is never stranded without a focus target.
    LaunchedEffect(phase) {
        if (phase == VoicePhase.RESULT || phase == VoicePhase.ERROR) {
            delay(160)
            runCatching { retryFocus.requestFocus() }
        }
    }

    val transition = rememberInfiniteTransition(label = "voice")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse",
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xF70A0B0D))
            .padding(horizontal = 80.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier.size(170.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val listening = phase == VoicePhase.LISTENING
                        val ringColor = palette.accent
                        repeat(3) { ring ->
                            val progress = ((pulse + ring / 3f) % 1f)
                            val radius = size.minDimension * (0.25f + progress * 0.24f)
                            drawCircle(
                                color = ringColor.copy(
                                    alpha = if (listening) (1f - progress) * 0.25f else 0.06f,
                                ),
                                radius = radius,
                                center = Offset(size.width / 2f, size.height / 2f),
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(78.dp)
                            .background(
                                if (phase == VoicePhase.ERROR) palette.danger.copy(alpha = 0.2f)
                                else palette.accent.copy(alpha = 0.18f),
                                CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (phase == VoicePhase.ERROR) Icons.Rounded.Warning else Icons.Rounded.Mic,
                            contentDescription = null,
                            tint = if (phase == VoicePhase.ERROR) palette.danger else palette.accent,
                            modifier = Modifier.size(34.dp),
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))
                Text(
                    text = when (phase) {
                        VoicePhase.LISTENING -> "Te escucho…"
                        VoicePhase.PROCESSING -> "Procesando…"
                        VoicePhase.RESULT -> "Listo"
                        VoicePhase.ERROR -> "No se pudo escuchar"
                    },
                    style = MaterialTheme.typography.displayMedium,
                    color = palette.textPrimary,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = when {
                        transcript.isNotBlank() && phase != VoicePhase.LISTENING -> "«$transcript»"
                        phase == VoicePhase.LISTENING -> "Di un comando como «abre Ajustes» o «reproduce Neon Horizon»."
                        else -> "Puedes reintentar o escribir la búsqueda."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (transcript.isNotBlank()) palette.accent else palette.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(640.dp),
                )
                if (feedback != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = feedback.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.textPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(620.dp),
                    )
                }
                if (error != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = error.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.danger,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(620.dp),
                    )
                }

                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SuggestionChip("Abre Ajustes") { handle(VoiceCommandParser.parse("abre ajustes", catalog)) }
                    SuggestionChip("Reproduce Neon Horizon") {
                        handle(VoiceCommandParser.parse("reproduce neon horizon", catalog))
                    }
                    SuggestionChip("Busca documentales") {
                        handle(VoiceCommandParser.parse("busca documentales", catalog))
                    }
                    SuggestionChip("Qué hora es") {
                        handle(VoiceCommandParser.parse("qué hora es", catalog))
                    }
                }

                Spacer(Modifier.height(26.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NovaButton(
                        label = if (phase == VoicePhase.LISTENING) "Escuchando…" else "Volver a escuchar",
                        icon = Icons.Rounded.Refresh,
                        primary = true,
                        onSelect = { listen() },
                        modifier = Modifier.focusRequester(retryFocus),
                    )
                    NovaButton(
                        label = "Cerrar",
                        primary = false,
                        onSelect = onClose,
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionChip(label: String, onSelect: () -> Unit) {
    val palette = LocalNovaPalette.current
    FocusableCard(
        onSelect = onSelect,
        shape = RoundedCornerShape(50),
        focusedScale = 1.06f,
        background = Color.White.copy(alpha = 0.06f),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
            color = palette.textSecondary,
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp),
        )
    }
}
