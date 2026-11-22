package com.opentv.homenova.voice

import java.text.Normalizer

/** Everything a voice command can resolve to. */
sealed interface VoiceAction {
    data class OpenApp(val packageName: String, val label: String) : VoiceAction
    data class OpenScreen(val route: String, val label: String) : VoiceAction
    data class Search(val query: String) : VoiceAction
    data class Play(val mediaId: String, val title: String) : VoiceAction
    data class Volume(val delta: Int) : VoiceAction
    data object Mute : VoiceAction
    data object TellTime : VoiceAction
    data class Speak(val text: String) : VoiceAction
    data object NoMatch : VoiceAction
}

data class VoiceApp(val packageName: String, val label: String)
data class VoiceMedia(val id: String, val title: String)

data class VoiceCatalog(
    val apps: List<VoiceApp> = emptyList(),
    val media: List<VoiceMedia> = emptyList(),
)

/**
 * Deterministic, dependency-free command parser.
 *
 * It is deliberately pure Kotlin so it can be unit-tested on the JVM and so the
 * same grammar works with the Android recognizer, a Bluetooth remote or a text
 * box. Spanish and English synonyms are supported.
 */
object VoiceCommandParser {

    private val screenAliases: List<ScreenAlias> = listOf(
        ScreenAlias("home", "Inicio", listOf("inicio", "home", "pantalla principal", "principal")),
        ScreenAlias("apps", "Aplicaciones", listOf("aplicaciones", "apps", "app", "todas las aplicaciones", "mis aplicaciones")),
        ScreenAlias("discover", "Descubrir", listOf("descubrir", "discover", "recomendaciones", "explorar")),
        ScreenAlias("live", "En vivo", listOf("en vivo", "live", "television", "canales", "directo")),
        ScreenAlias("inputs", "Entradas", listOf("entradas", "inputs", "fuentes", "hdmi")),
        ScreenAlias("notifications", "Notificaciones", listOf("notificaciones", "notifications", "avisos")),
        ScreenAlias("settings", "Ajustes", listOf("ajustes", "configuracion", "settings", "preferencias")),
        ScreenAlias("search", "Buscar", listOf("buscar", "search", "busqueda")),
    )

    private val openVerbs = listOf("abre", "abreme", "abrir", "abre la", "abre el", "lanza", "inicia", "open", "launch", "start")
    private val playVerbs = listOf("reproduce", "reproducir", "pon", "ponme", "play", "ver", "watch")
    private val searchVerbs = listOf("busca", "buscar", "search", "encuentra", "find")
    private val politePrefixes = listOf("por favor", "please", "oye nova", "hey nova", "nova", "vale")
    private val navigationPrefixes = listOf("ve a", "ir a", "vete a", "go to", "navega a", "llévame a", "llevame a")

    fun parse(raw: String, catalog: VoiceCatalog): VoiceAction {
        var text = normalize(raw)
        if (text.isBlank()) return VoiceAction.NoMatch

        politePrefixes.forEach { prefix ->
            if (text.startsWith("$prefix ")) text = text.removePrefix("$prefix ").trim()
        }
        navigationPrefixes.forEach { prefix ->
            if (text.startsWith("$prefix ")) text = text.removePrefix("$prefix ").trim()
        }

        when {
            text in setOf("silencio", "silenciar", "mute", "quita el sonido") -> return VoiceAction.Mute
            text.contains("sube el volumen") || text.contains("volume up") ||
                text.contains("mas volumen") || text == "volumen arriba" ->
                return VoiceAction.Volume(+10)
            text.contains("baja el volumen") || text.contains("volume down") ||
                text.contains("menos volumen") || text == "volumen abajo" ->
                return VoiceAction.Volume(-10)
            text.contains("que hora es") || text == "hora" || text == "time" ||
                text.contains("dime la hora") ->
                return VoiceAction.TellTime
            text.contains("apaga la tele") || text.contains("apagar la tele") ||
                text.contains("power off") ->
                return VoiceAction.Speak("Para apagar el televisor, usa el botón de encendido del mando o del sistema.")
        }

        // Search is checked before app matching so "busca ..." never launches an app.
        for (verb in searchVerbs) {
            if (text == verb) return VoiceAction.OpenScreen("search", "Buscar")
            if (text.startsWith("$verb ")) {
                val query = text.removePrefix("$verb ").trim()
                if (query.isNotBlank()) return VoiceAction.Search(query)
            }
        }

        // "abre <app>" / "abre <pantalla>"
        for (verb in openVerbs) {
            if (text == verb) continue
            if (text.startsWith("$verb ")) {
                val target = text.removePrefix("$verb ").trim()
                bestApp(target, catalog.apps)?.let {
                    return VoiceAction.OpenApp(it.packageName, it.label)
                }
                screenAliases.firstOrNull { it.matches(target) }?.let {
                    return VoiceAction.OpenScreen(it.route, it.label)
                }
            }
        }

        // "pon <contenido>"
        for (verb in playVerbs) {
            if (text == verb) continue
            if (text.startsWith("$verb ")) {
                val target = text.removePrefix("$verb ").trim()
                bestMedia(target, catalog.media)?.let {
                    return VoiceAction.Play(it.id, it.title)
                }
                bestApp(target, catalog.apps)?.let {
                    return VoiceAction.OpenApp(it.packageName, it.label)
                }
            }
        }

        // Bare screen name, e.g. "ajustes".
        screenAliases.firstOrNull { it.matches(text) }?.let {
            return VoiceAction.OpenScreen(it.route, it.label)
        }

        // Bare app or content title.
        bestApp(text, catalog.apps)?.let { return VoiceAction.OpenApp(it.packageName, it.label) }
        bestMedia(text, catalog.media)?.let { return VoiceAction.Play(it.id, it.title) }

        return if (text.length >= 3) VoiceAction.Search(raw.trim()) else VoiceAction.NoMatch
    }

    fun normalize(input: String): String {
        val withoutAccents = Normalizer.normalize(input.lowercase(), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
        return withoutAccents
            .replace("[^a-z0-9ñ ]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    private fun bestApp(query: String, apps: List<VoiceApp>): VoiceApp? {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isBlank()) return null
        return apps
            .map { it to score(normalizedQuery, normalize(it.label)) }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?.first
    }

    private fun bestMedia(query: String, media: List<VoiceMedia>): VoiceMedia? {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isBlank()) return null
        return media
            .map { it to score(normalizedQuery, normalize(it.title)) }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?.first
    }

    private fun score(query: String, candidate: String): Int {
        if (candidate == query) return 100
        if (candidate.startsWith(query)) return 80
        if (candidate.contains(query) || query.contains(candidate)) return 60
        val distance = levenshtein(query, candidate)
        return if (query.length >= 4 && distance <= 2) 40 else 0
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(
                    current[j - 1] + 1,
                    previous[j] + 1,
                    previous[j - 1] + cost,
                )
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }

    private data class ScreenAlias(
        val route: String,
        val label: String,
        val aliases: List<String>,
    ) {
        fun matches(text: String): Boolean =
            aliases.any { alias -> text == alias || text.startsWith("$alias ") }
    }
}
