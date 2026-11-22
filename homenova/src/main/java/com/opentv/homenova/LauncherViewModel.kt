package com.opentv.homenova

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.opentv.homenova.core.AppEntry
import com.opentv.homenova.core.InputEntry
import com.opentv.homenova.core.LauncherNotification
import com.opentv.homenova.core.MediaEntry
import com.opentv.homenova.data.LauncherSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val container = AppContainer.get(application)
    private val appRepository = container.appRepository
    private val settingsRepository = container.settingsRepository
    private val inputRepository = container.inputRepository
    private val contentRepository = container.contentRepository

    val settings: StateFlow<LauncherSettings?> = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    private val allApps = MutableStateFlow<List<AppEntry>>(emptyList())
    val isLoadingApps = MutableStateFlow(true)

    /** Apps visible in the grid and on Home (hidden ones removed). */
    val apps: StateFlow<List<AppEntry>> = combine(
        allApps,
        settings.map { it ?: LauncherSettings() },
    ) { list, prefs ->
        list.filter { it.packageName !in prefs.hiddenApps }
            .sortedWith(
                compareByDescending<AppEntry> { it.packageName in prefs.favourites }
                    .thenBy { it.label.lowercase() },
            )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Includes hidden apps, for the management section in Settings. */
    val allAppsForManagement: StateFlow<List<AppEntry>> = allApps

    val inputs = MutableStateFlow<List<InputEntry>>(emptyList())
    val notifications = MutableStateFlow(contentRepository.notifications)
    val nowPlayingTitle = MutableStateFlow<String?>(null)

    val catalogue: List<MediaEntry> = contentRepository.catalogue
    val featured: List<MediaEntry> = contentRepository.featured
    val continueWatching: List<MediaEntry> = contentRepository.continueWatching
    val trending: List<MediaEntry> = contentRepository.trending
    val recommendations: List<MediaEntry> = contentRepository.recommendations
    val liveChannels: List<MediaEntry> = contentRepository.liveChannels

    init {
        refreshApps()
        refreshInputs()
        viewModelScope.launch {
            container.packageEvents.collect { refreshApps() }
        }
    }

    fun refreshApps() {
        viewModelScope.launch {
            isLoadingApps.value = true
            allApps.value = appRepository.loadApps()
            isLoadingApps.value = false
        }
    }

    fun refreshInputs() {
        viewModelScope.launch {
            inputs.value = inputRepository.loadInputs()
        }
    }

    fun launchApp(app: AppEntry): Boolean = appRepository.launch(app.packageName)

    fun launchApp(packageName: String): Boolean = appRepository.launch(packageName)

    fun openAppInfo(app: AppEntry): Boolean = appRepository.openInfo(app.packageName)

    fun uninstallApp(app: AppEntry): Boolean = appRepository.requestUninstall(app.packageName)

    fun openInput(input: InputEntry): Boolean = inputRepository.openInput(input)

    fun openSystemInputPicker(): Boolean = inputRepository.openSystemPicker()

    fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    fun toggleFavourite(packageName: String) {
        viewModelScope.launch { settingsRepository.toggleFavourite(packageName) }
    }

    fun toggleHidden(packageName: String) {
        viewModelScope.launch { settingsRepository.toggleHidden(packageName) }
    }

    fun resetSettings() {
        viewModelScope.launch { settingsRepository.reset() }
    }

    fun dismissNotification(id: Long) {
        notifications.value = notifications.value.filterNot { it.id == id }
    }

    fun markNotificationRead(id: Long) {
        notifications.value = notifications.value.map {
            if (it.id == id) it.copy(read = true) else it
        }
    }

    fun markAllNotificationsRead() {
        notifications.value = notifications.value.map { it.copy(read = true) }
    }

    fun findMedia(id: String): MediaEntry? = contentRepository.findMedia(id)

    fun isDefaultLauncher(): Boolean {
        val context = getApplication<Application>()
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(
            homeIntent,
            PackageManager.MATCH_DEFAULT_ONLY,
        )
        return resolved?.activityInfo?.packageName == context.packageName
    }
}
