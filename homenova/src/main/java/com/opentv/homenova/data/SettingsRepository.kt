package com.opentv.homenova.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "nova_launcher_settings",
)

/** Everything the launcher persists between boots. */
data class LauncherSettings(
    val startScreen: String = "home",
    val gridColumns: Int = 6,
    val clock24h: Boolean = false,
    val videoPreviews: Boolean = true,
    val audioPreviews: Boolean = false,
    val screensaverMinutes: Int = 10,
    val reduceMotion: Boolean = false,
    val ambientBackground: Boolean = true,
    val showRecommendations: Boolean = true,
    val dpadSounds: Boolean = true,
    val voiceLanguage: String = "es-ES",
    val useSystemAssistant: Boolean = false,
    val accent: String = "aurora",
    val favourites: Set<String> = emptySet(),
    val hiddenApps: Set<String> = emptySet(),
    val notificationsEnabled: Boolean = true,
    val dndEnabled: Boolean = false,
)

class SettingsRepository(private val context: Context) {

    val settings: Flow<LauncherSettings> = context.settingsDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { it.toSettings() }

    suspend fun current(): LauncherSettings = settings.first()

    suspend fun update(transform: (LauncherSettings) -> LauncherSettings) {
        context.settingsDataStore.edit { preferences ->
            val next = transform(preferences.toSettings())
            preferences[Keys.START_SCREEN] = next.startScreen
            preferences[Keys.GRID_COLUMNS] = next.gridColumns
            preferences[Keys.CLOCK_24H] = next.clock24h
            preferences[Keys.VIDEO_PREVIEWS] = next.videoPreviews
            preferences[Keys.AUDIO_PREVIEWS] = next.audioPreviews
            preferences[Keys.SCREENSAVER_MINUTES] = next.screensaverMinutes
            preferences[Keys.REDUCE_MOTION] = next.reduceMotion
            preferences[Keys.AMBIENT_BACKGROUND] = next.ambientBackground
            preferences[Keys.SHOW_RECOMMENDATIONS] = next.showRecommendations
            preferences[Keys.DPAD_SOUNDS] = next.dpadSounds
            preferences[Keys.VOICE_LANGUAGE] = next.voiceLanguage
            preferences[Keys.USE_SYSTEM_ASSISTANT] = next.useSystemAssistant
            preferences[Keys.ACCENT] = next.accent
            preferences[Keys.FAVOURITES] = next.favourites
            preferences[Keys.HIDDEN_APPS] = next.hiddenApps
            preferences[Keys.NOTIFICATIONS_ENABLED] = next.notificationsEnabled
            preferences[Keys.DND_ENABLED] = next.dndEnabled
        }
    }

    suspend fun toggleFavourite(packageName: String) = update { current ->
        current.copy(
            favourites = if (packageName in current.favourites) {
                current.favourites - packageName
            } else {
                current.favourites + packageName
            },
        )
    }

    suspend fun toggleHidden(packageName: String) = update { current ->
        current.copy(
            hiddenApps = if (packageName in current.hiddenApps) {
                current.hiddenApps - packageName
            } else {
                current.hiddenApps + packageName
            },
        )
    }

    suspend fun reset() = update { LauncherSettings() }

    private fun Preferences.toSettings() = LauncherSettings(
        startScreen = this[Keys.START_SCREEN] ?: "home",
        gridColumns = (this[Keys.GRID_COLUMNS] ?: 6).coerceIn(4, 8),
        clock24h = this[Keys.CLOCK_24H] ?: false,
        videoPreviews = this[Keys.VIDEO_PREVIEWS] ?: true,
        audioPreviews = this[Keys.AUDIO_PREVIEWS] ?: false,
        screensaverMinutes = this[Keys.SCREENSAVER_MINUTES] ?: 10,
        reduceMotion = this[Keys.REDUCE_MOTION] ?: false,
        ambientBackground = this[Keys.AMBIENT_BACKGROUND] ?: true,
        showRecommendations = this[Keys.SHOW_RECOMMENDATIONS] ?: true,
        dpadSounds = this[Keys.DPAD_SOUNDS] ?: true,
        voiceLanguage = this[Keys.VOICE_LANGUAGE] ?: "es-ES",
        useSystemAssistant = this[Keys.USE_SYSTEM_ASSISTANT] ?: false,
        accent = this[Keys.ACCENT] ?: "aurora",
        favourites = this[Keys.FAVOURITES] ?: emptySet(),
        hiddenApps = this[Keys.HIDDEN_APPS] ?: emptySet(),
        notificationsEnabled = this[Keys.NOTIFICATIONS_ENABLED] ?: true,
        dndEnabled = this[Keys.DND_ENABLED] ?: false,
    )

    private object Keys {
        val START_SCREEN = stringPreferencesKey("start_screen")
        val GRID_COLUMNS = intPreferencesKey("grid_columns")
        val CLOCK_24H = booleanPreferencesKey("clock_24h")
        val VIDEO_PREVIEWS = booleanPreferencesKey("video_previews")
        val AUDIO_PREVIEWS = booleanPreferencesKey("audio_previews")
        val SCREENSAVER_MINUTES = intPreferencesKey("screensaver_minutes")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val AMBIENT_BACKGROUND = booleanPreferencesKey("ambient_background")
        val SHOW_RECOMMENDATIONS = booleanPreferencesKey("show_recommendations")
        val DPAD_SOUNDS = booleanPreferencesKey("dpad_sounds")
        val VOICE_LANGUAGE = stringPreferencesKey("voice_language")
        val USE_SYSTEM_ASSISTANT = booleanPreferencesKey("use_system_assistant")
        val ACCENT = stringPreferencesKey("accent")
        val FAVOURITES = stringSetPreferencesKey("favourites")
        val HIDDEN_APPS = stringSetPreferencesKey("hidden_apps")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val DND_ENABLED = booleanPreferencesKey("dnd_enabled")
    }
}
