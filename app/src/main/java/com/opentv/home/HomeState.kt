package com.opentv.home

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Estado de la pantalla de inicio. Una instancia por actividad. */
@Stable
class HomeState(
    private val context: Context,
    private val store: ProfileStore,
) {
    /** Preferencias del perfil activo; se reemplazan al cambiar de perfil. */
    private var prefs = HomePrefs(context, store.activeId())

    var apps by mutableStateOf<List<AppEntry>>(emptyList())
        private set
    var loaded by mutableStateOf(false)
        private set

    var profiles by mutableStateOf(store.profiles())
        private set
    var activeProfileId by mutableStateOf(store.activeId())
        private set

    var favoritePkgs by mutableStateOf(prefs.favorites())
        private set
    var recentPkgs by mutableStateOf(prefs.recents())
        private set
    var hiddenPkgs by mutableStateOf(prefs.hidden())
        private set
    var showNotifText by mutableStateOf(prefs.showNotifText())
        private set

    /** ¿Concedió el usuario el acceso a notificaciones? Se refresca al volver a la pantalla. */
    var notifAccess by mutableStateOf(NotificationHub.isAccessEnabled(context))
        private set

    /** Panel lateral de ajustes rápidos (botón de la barra superior). */
    var quickPanelOpen by mutableStateOf(false)

    /** Diálogo para elegir, crear o eliminar perfiles. */
    var profileDialogOpen by mutableStateOf(false)

    var tab by mutableStateOf(HomeTab.Home)
    var query by mutableStateOf("")
    var status by mutableStateOf("")
    var listening by mutableStateOf(false)

    /** App con el foco actual: alimenta el banner destacado. */
    var focusedApp by mutableStateOf<AppEntry?>(null)

    /** App cuyo menú contextual (mantener presionado) está abierto. */
    var menuApp by mutableStateOf<AppEntry?>(null)

    val activeProfile: Profile
        get() = profiles.firstOrNull { it.id == activeProfileId } ?: profiles.first()

    /** Apps no ocultas: es lo que ven las filas, la grilla y la búsqueda. */
    val visibleApps: List<AppEntry>
        get() = apps.filter { it.packageName !in hiddenPkgs }

    val favorites: List<AppEntry>
        get() = favoritePkgs.mapNotNull { pkg -> visibleApps.firstOrNull { it.packageName == pkg } }

    val recents: List<AppEntry>
        get() = recentPkgs.mapNotNull { pkg -> visibleApps.firstOrNull { it.packageName == pkg } }

    val tvApps: List<AppEntry>
        get() = visibleApps.filter { it.isTvApp }

    val searchResults: List<AppEntry>
        get() {
            val q = Norm.of(query)
            return if (q.isEmpty()) emptyList() else visibleApps.filter { it.searchKey.contains(q) || it.packageName.contains(q) }
        }

    val featured: AppEntry?
        get() = focusedApp ?: recents.firstOrNull() ?: favorites.firstOrNull() ?: visibleApps.firstOrNull()

    fun isFavorite(app: AppEntry): Boolean = app.packageName in favoritePkgs

    suspend fun reload() {
        apps = withContext(Dispatchers.Default) { AppRepository.load(context) }
        loaded = true
    }

    fun refreshAccess() {
        notifAccess = NotificationHub.isAccessEnabled(context)
    }

    // --- Perfiles -----------------------------------------------------------

    private fun load(id: String) {
        prefs = HomePrefs(context, id)
        activeProfileId = id
        favoritePkgs = prefs.favorites()
        recentPkgs = prefs.recents()
        hiddenPkgs = prefs.hidden()
        showNotifText = prefs.showNotifText()
        focusedApp = null
        menuApp = null
        tab = HomeTab.Home
        status = "Perfil: ${activeProfile.name}"
    }

    fun switchProfile(id: String) {
        if (id == activeProfileId || profiles.none { it.id == id }) return
        store.setActive(id)
        load(id)
    }

    fun addProfile(name: String) {
        val profile = store.add(name)
        profiles = store.profiles()
        switchProfile(profile.id)
    }

    fun deleteProfile(id: String) {
        val wasActive = id == activeProfileId
        if (!store.remove(id)) {
            status = "Tiene que quedar al menos un perfil"
            return
        }
        profiles = store.profiles()
        if (wasActive) load(store.activeId()) else status = "Perfil eliminado"
    }

    // --- Apps ---------------------------------------------------------------

    fun open(app: AppEntry) {
        if (AppRepository.launch(context, app.packageName)) {
            recentPkgs = prefs.pushRecent(app.packageName)
            status = "Abriendo ${app.label}"
        } else {
            status = "No pude abrir ${app.label}"
        }
    }

    fun setFavorite(app: AppEntry, on: Boolean) {
        val list = favoritePkgs.toMutableList()
        if (on && app.packageName !in list) list.add(app.packageName)
        if (!on) list.remove(app.packageName)
        favoritePkgs = list
        prefs.setFavorites(list)
    }

    /** Mueve un favorito [delta] posiciones (-1 izquierda, +1 derecha). */
    fun moveFavorite(app: AppEntry, delta: Int) {
        val list = favoritePkgs.toMutableList()
        val from = list.indexOf(app.packageName)
        if (from < 0) return
        val to = (from + delta).coerceIn(0, list.lastIndex)
        if (from == to) return
        list.removeAt(from)
        list.add(to, app.packageName)
        favoritePkgs = list
        prefs.setFavorites(list)
    }

    fun hide(app: AppEntry) {
        if (app.packageName in hiddenPkgs) return
        val list = hiddenPkgs + app.packageName
        hiddenPkgs = list
        prefs.setHidden(list)
        if (focusedApp?.packageName == app.packageName) focusedApp = null
        status = "${app.label} oculta. Se restaura en Ajustes"
    }

    fun restoreHidden() {
        hiddenPkgs = emptyList()
        prefs.setHidden(emptyList())
        status = "Apps ocultas restauradas"
    }

    fun openAppInfo(app: AppEntry) {
        if (!AppRepository.openAppInfo(context, app.packageName)) status = "No pude abrir la información de ${app.label}"
    }

    fun uninstall(app: AppEntry) {
        if (!AppRepository.requestUninstall(context, app.packageName)) status = "No pude desinstalar ${app.label}"
    }

    fun openSystem(shortcut: SystemShortcut) {
        status = if (shortcut.launch(context)) "Abriendo ${shortcut.title}" else "Este dispositivo no tiene «${shortcut.title}»"
    }

    // --- Seguir viendo y notificaciones ---------------------------------------

    /** Abre la pantalla de la app que está reproduciendo; si no la declara, abre la app. */
    fun openNowPlaying(item: NowPlaying) {
        val pending = item.sessionActivity
        val sent = pending != null && runCatching { pending.send() }.isSuccess
        if (!sent && !AppRepository.launch(context, item.packageName)) status = "No pude abrir ${item.appLabel}"
    }

    fun openNotification(item: NotifItem) {
        if (!NotificationHub.open(item) && !AppRepository.launch(context, item.packageName)) {
            status = "No pude abrir ${item.appLabel}"
        }
    }

    fun updateShowNotifText(on: Boolean) {
        showNotifText = on
        prefs.setShowNotifText(on)
    }

    // --- Voz ------------------------------------------------------------------

    fun handleVoice(spoken: String) {
        when (val action = VoiceCommands.parse(spoken, apps, profiles)) {
            is VoiceAction.OpenApp -> open(action.app)
            is VoiceAction.Pin -> {
                setFavorite(action.app, true)
                status = "${action.app.label} fijada en favoritos"
            }
            is VoiceAction.Unpin -> {
                setFavorite(action.app, false)
                status = "${action.app.label} quitada de favoritos"
            }
            is VoiceAction.Search -> {
                query = action.query
                tab = HomeTab.Search
                status = "Buscando «${action.query}»"
            }
            is VoiceAction.GoTo -> {
                tab = action.tab
                status = ""
            }
            is VoiceAction.OpenSystem -> openSystem(action.shortcut)
            is VoiceAction.SwitchProfile -> switchProfile(action.profile.id)
            is VoiceAction.ProfileNotFound -> status = "No encontré el perfil «${action.name}»"
            is VoiceAction.AppNotFound -> status = "No encontré «${action.name}»"
            is VoiceAction.Unknown -> status = "Escuché «${action.spoken}», pero no lo entendí. Probá «abrir…» o «buscar…»"
        }
    }
}
