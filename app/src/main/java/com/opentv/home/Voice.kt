package com.opentv.home

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/** Envoltorio de [SpeechRecognizer]. Crear y usar siempre desde el hilo principal. */
class VoiceController(
    private val context: Context,
    private val onListening: (Boolean) -> Unit,
    private val onPartial: (String) -> Unit,
    private val onText: (String) -> Unit,
    private val onFailure: (String) -> Unit,
) {
    private var recognizer: SpeechRecognizer? = null

    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onFailure("Este dispositivo no tiene reconocimiento de voz disponible")
            return
        }
        stop()
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = onListening(true)
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() = onListening(false)

            override fun onError(error: Int) {
                onListening(false)
                onFailure(
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No te escuché, probá de nuevo"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Falta el permiso de micrófono"
                        SpeechRecognizer.ERROR_NETWORK,
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Sin conexión para el reconocimiento de voz"
                        else -> "Error de voz ($error)"
                    },
                )
            }

            override fun onResults(results: Bundle?) {
                onListening(false)
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (text.isNullOrBlank()) onFailure("No te escuché, probá de nuevo") else onText(text)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!text.isNullOrBlank()) onPartial(text)
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        recognizer = r
        r.startListening(intent)
    }

    fun stop() {
        recognizer?.destroy()
        recognizer = null
        onListening(false)
    }
}

sealed interface VoiceAction {
    data class OpenApp(val app: AppEntry) : VoiceAction
    data class Pin(val app: AppEntry) : VoiceAction
    data class Unpin(val app: AppEntry) : VoiceAction
    data class Search(val query: String) : VoiceAction
    data class GoTo(val tab: HomeTab) : VoiceAction
    data class OpenSystem(val shortcut: SystemShortcut) : VoiceAction
    data class SwitchProfile(val profile: Profile) : VoiceAction
    data class ProfileNotFound(val name: String) : VoiceAction
    data class AppNotFound(val name: String) : VoiceAction
    data class Unknown(val spoken: String) : VoiceAction
}

/** Intérprete de comandos hablados en español (y "open" en inglés). */
object VoiceCommands {

    private val unpinPrefixes = listOf("quitar de favoritos ", "sacar de favoritos ", "desfijar ")
    private val pinPrefixes = listOf("agregar a favoritos ", "anadir a favoritos ", "poner en favoritos ", "fijar ", "favorito ")
    private val searchPrefixes = listOf("buscar ", "busca ", "buscame ", "search ")
    private val goPrefixes = listOf("ir a ", "ve a ", "andate a ", "mostrar ", "mostrame ", "volver a ")
    private val profilePrefixes = listOf("cambiar a perfil ", "cambiar al perfil ", "usar perfil ", "perfil ")
    private val openPrefixes = listOf("abrir ", "abre ", "abri ", "lanzar ", "pone ", "poner ", "open ")

    fun parse(spoken: String, apps: List<AppEntry>, profiles: List<Profile> = emptyList()): VoiceAction {
        val text = Norm.of(spoken)
        if (text.isEmpty()) return VoiceAction.Unknown(spoken)

        after(text, profilePrefixes)?.let { name ->
            if (name.isNotEmpty()) {
                return findProfile(name, profiles)?.let { VoiceAction.SwitchProfile(it) } ?: VoiceAction.ProfileNotFound(name)
            }
        }

        after(text, unpinPrefixes)?.let { name ->
            return findApp(name, apps)?.let { VoiceAction.Unpin(it) } ?: VoiceAction.AppNotFound(name)
        }
        after(text, pinPrefixes)?.let { name ->
            return findApp(name, apps)?.let { VoiceAction.Pin(it) } ?: VoiceAction.AppNotFound(name)
        }
        after(text, searchPrefixes)?.let { query ->
            if (query.isNotEmpty()) return VoiceAction.Search(query)
        }

        val goTarget = after(text, goPrefixes) ?: text
        tabFor(goTarget)?.let { return VoiceAction.GoTo(it) }
        SystemShortcut.fromSpoken(goTarget)?.let { return VoiceAction.OpenSystem(it) }

        val name = after(text, openPrefixes) ?: return VoiceAction.Unknown(spoken)
        if (name.isEmpty()) return VoiceAction.Unknown(spoken)
        tabFor(name)?.let { return VoiceAction.GoTo(it) }
        SystemShortcut.fromSpoken(name)?.let { return VoiceAction.OpenSystem(it) }
        return findApp(name, apps)?.let { VoiceAction.OpenApp(it) } ?: VoiceAction.AppNotFound(name)
    }

    fun findProfile(name: String, profiles: List<Profile>): Profile? {
        val n = Norm.of(name)
        if (n.isEmpty()) return null
        return profiles.firstOrNull { Norm.of(it.name) == n }
            ?: profiles.firstOrNull { Norm.of(it.name).contains(n) }
    }

    fun findApp(name: String, apps: List<AppEntry>): AppEntry? {
        val n = Norm.of(name)
        if (n.isEmpty()) return null
        return apps.firstOrNull { it.searchKey == n }
            ?: apps.firstOrNull { it.searchKey.startsWith(n) }
            ?: apps.firstOrNull { it.searchKey.contains(n) }
            ?: apps.firstOrNull { it.searchKey.length >= 3 && n.contains(it.searchKey) }
    }

    private fun tabFor(name: String): HomeTab? = when (name) {
        "inicio", "home", "pantalla de inicio", "pantalla principal" -> HomeTab.Home
        "apps", "aplicaciones", "todas las apps", "todas las aplicaciones" -> HomeTab.Apps
        "busqueda", "buscador" -> HomeTab.Search
        "ajustes", "configuracion" -> HomeTab.Settings
        else -> null
    }

    private fun after(text: String, prefixes: List<String>): String? =
        prefixes.firstOrNull { text.startsWith(it) }?.let { text.removePrefix(it).trim() }
}
