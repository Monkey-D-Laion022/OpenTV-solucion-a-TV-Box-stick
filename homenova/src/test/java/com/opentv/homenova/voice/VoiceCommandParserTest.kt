package com.opentv.homenova.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceCommandParserTest {

    private val catalog = VoiceCatalog(
        apps = listOf(
            VoiceApp("com.example.video", "Nova Video"),
            VoiceApp("com.example.music", "Nova Música"),
            VoiceApp("com.example.games", "Arcade Central"),
        ),
        media = listOf(
            VoiceMedia("neon-horizon", "Neon Horizon"),
            VoiceMedia("last-signal", "The Last Signal"),
        ),
    )

    @Test
    fun opensAppWithSpanishVerb() {
        val action = VoiceCommandParser.parse("abre Nova Video", catalog)
        assertEquals(VoiceAction.OpenApp("com.example.video", "Nova Video"), action)
    }

    @Test
    fun opensAppIgnoringAccents() {
        val action = VoiceCommandParser.parse("ABRE nova musica", catalog)
        assertEquals(VoiceAction.OpenApp("com.example.music", "Nova Música"), action)
    }

    @Test
    fun resolvesScreenWithNavigationVerb() {
        assertEquals(
            VoiceAction.OpenScreen("settings", "Ajustes"),
            VoiceCommandParser.parse("ve a ajustes", catalog),
        )
    }

    @Test
    fun opensSettings() {
        assertEquals(
            VoiceAction.OpenScreen("settings", "Ajustes"),
            VoiceCommandParser.parse("ajustes", catalog),
        )
    }

    @Test
    fun playsContent() {
        assertEquals(
            VoiceAction.Play("neon-horizon", "Neon Horizon"),
            VoiceCommandParser.parse("reproduce neon horizon", catalog),
        )
    }

    @Test
    fun searchesForUnknownContent() {
        // Queries are normalised (lowercase, no accents) for matching; the
        // search screen normalises user input the same way.
        val action = VoiceCommandParser.parse("busca películas de nieve", catalog)
        assertEquals(VoiceAction.Search("peliculas de nieve"), action)
    }

    @Test
    fun controlsVolume() {
        assertEquals(VoiceAction.Volume(10), VoiceCommandParser.parse("sube el volumen", catalog))
        assertEquals(VoiceAction.Volume(-10), VoiceCommandParser.parse("baja el volumen", catalog))
        assertEquals(VoiceAction.Mute, VoiceCommandParser.parse("silencio", catalog))
    }

    @Test
    fun toleratesTypos() {
        val action = VoiceCommandParser.parse("abre nova vide", catalog)
        assertEquals(VoiceAction.OpenApp("com.example.video", "Nova Video"), action)
    }

    @Test
    fun blankInputDoesNotMatch() {
        assertEquals(VoiceAction.NoMatch, VoiceCommandParser.parse("   ", catalog))
    }
}
