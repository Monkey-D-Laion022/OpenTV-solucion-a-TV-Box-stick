package com.opentv.homenova.voice

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast

/**
 * Thin platform bridge for speech and system assistants.
 *
 * Google Assistant is a privileged system service and cannot be bundled into an
 * app. If a voice interaction service is installed (as on certified TVs), the
 * launcher can hand the request over with ACTION_VOICE_COMMAND; otherwise the
 * built-in RecognizerIntent flow is used. Both paths share the same parser.
 */
object VoiceAssistant {

    fun recognitionIntent(language: String): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Habla ahora…")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

    fun isRecognitionAvailable(context: Context): Boolean =
        SpeechRecognizer.isRecognitionAvailable(context) ||
            runCatching { context.packageManager.queryIntentActivities(recognitionIntent("es-ES"), 0) }
                .getOrDefault(emptyList())
                .isNotEmpty()

    /** Returns true when a system voice interaction service handled the intent. */
    fun launchSystemAssistant(context: Context): Boolean {
        val candidates = listOf(
            Intent(Intent.ACTION_VOICE_COMMAND),
            Intent(Intent.ACTION_ASSIST),
        )
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val resolved = runCatching { context.startActivity(intent) }
            if (resolved.isSuccess) return true
        }
        Toast.makeText(context, "No hay un asistente de voz del sistema disponible", Toast.LENGTH_SHORT)
            .show()
        return false
    }

    fun adjustVolume(context: Context, delta: Int) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val direction = if (delta >= 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
    }

    fun mute(context: Context) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        audio.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_MUTE,
            AudioManager.FLAG_SHOW_UI,
        )
    }
}
