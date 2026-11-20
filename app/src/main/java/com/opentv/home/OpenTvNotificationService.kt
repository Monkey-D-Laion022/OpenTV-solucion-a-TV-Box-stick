package com.opentv.home

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Lee las notificaciones y las sesiones de medios activas para las filas «Seguir viendo» y
 * «Notificaciones». Solo funciona si el usuario concede «Acceso a notificaciones» a la app
 * (Ajustes del sistema); sin eso Android no arranca este servicio.
 *
 * Nota: la lista «Watch Next» de Android TV no se puede leer desde una app común (el permiso es
 * signature|privileged). Por eso «Seguir viendo» sale de las sesiones de medios activas.
 */
class OpenTvNotificationService : NotificationListenerService() {

    private var sessions: MediaSessionManager? = null
    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { refresh() }

    private val listenerComponent: ComponentName
        get() = ComponentName(this, OpenTvNotificationService::class.java)

    override fun onListenerConnected() {
        super.onListenerConnected()
        NotificationHub.service = this
        val manager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        sessions = manager
        runCatching { manager?.addOnActiveSessionsChangedListener(sessionListener, listenerComponent) }
        refresh()
    }

    override fun onListenerDisconnected() {
        runCatching { sessions?.removeOnActiveSessionsChangedListener(sessionListener) }
        sessions = null
        NotificationHub.service = null
        NotificationHub.clear()
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    private fun refresh() {
        val notifs = runCatching { buildNotifications() }.getOrDefault(emptyList())
        val media = runCatching { buildNowPlaying() }.getOrDefault(emptyList())
        NotificationHub.publish(notifs, media)
    }

    private fun labelOf(pkg: String): String =
        runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }
            .getOrDefault(pkg)

    private fun buildNotifications(): List<NotifItem> {
        val active = activeNotifications ?: return emptyList()
        return active
            .asSequence()
            .filter { it.packageName != packageName }
            .filter { it.notification.flags and Notification.FLAG_ONGOING_EVENT == 0 }
            .filter { it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
            .filter { it.notification.category != Notification.CATEGORY_TRANSPORT }
            .sortedByDescending { it.postTime }
            .take(20)
            .map { sbn ->
                val extras = sbn.notification.extras
                NotifItem(
                    key = sbn.key,
                    packageName = sbn.packageName,
                    appLabel = labelOf(sbn.packageName),
                    title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
                    text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
                    postedAt = sbn.postTime,
                    contentIntent = sbn.notification.contentIntent,
                    clearable = sbn.isClearable,
                )
            }
            .toList()
    }

    private fun buildNowPlaying(): List<NowPlaying> {
        val manager = sessions ?: return emptyList()
        return manager.getActiveSessions(listenerComponent).mapNotNull { controller ->
            val state = controller.playbackState?.state
            if (state == null || state == PlaybackState.STATE_NONE || state == PlaybackState.STATE_STOPPED) {
                return@mapNotNull null
            }
            val meta = controller.metadata
            val label = labelOf(controller.packageName)
            val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty().ifBlank { label }
            val subtitle = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM)
                ?: ""
            NowPlaying(
                packageName = controller.packageName,
                appLabel = label,
                title = title,
                subtitle = subtitle,
                playing = state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING,
                sessionActivity = controller.sessionActivity,
                controller = controller,
            )
        }
    }
}
