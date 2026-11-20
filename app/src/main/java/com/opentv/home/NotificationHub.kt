package com.opentv.home

import android.app.PendingIntent
import android.content.Context
import android.media.session.MediaController
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat

/** Una notificación de otra app, ya reducida a lo que muestra el inicio. */
data class NotifItem(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    val contentIntent: PendingIntent?,
    val clearable: Boolean,
)

/** Una sesión de medios activa (algo reproduciéndose o en pausa en otra app). */
data class NowPlaying(
    val packageName: String,
    val appLabel: String,
    val title: String,
    val subtitle: String,
    val playing: Boolean,
    val sessionActivity: PendingIntent?,
    val controller: MediaController,
)

/**
 * Puente entre [OpenTvNotificationService] (que Android arranca por su cuenta) y la pantalla.
 * Todo vive en memoria y en este proceso: no se guarda ni se envía nada.
 * Sin el acceso a notificaciones concedido por el usuario, las dos listas quedan vacías.
 */
object NotificationHub {

    var notifications by mutableStateOf<List<NotifItem>>(emptyList())
        private set
    var nowPlaying by mutableStateOf<List<NowPlaying>>(emptyList())
        private set

    @Volatile
    internal var service: OpenTvNotificationService? = null

    /** ¿Concedió el usuario «Acceso a notificaciones» a esta app? */
    fun isAccessEnabled(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    internal fun publish(notifs: List<NotifItem>, media: List<NowPlaying>) {
        notifications = notifs
        nowPlaying = media
    }

    internal fun clear() = publish(emptyList(), emptyList())

    fun dismiss(item: NotifItem) {
        if (item.clearable) runCatching { service?.cancelNotification(item.key) }
    }

    /** Descarta todas las notificaciones descartables del dispositivo, no solo las visibles acá. */
    fun dismissAll() {
        runCatching { service?.cancelAllNotifications() }
    }

    fun togglePlayback(item: NowPlaying) {
        runCatching {
            if (item.playing) item.controller.transportControls.pause() else item.controller.transportControls.play()
        }
    }

    /** Abre la acción de la notificación; devuelve false si no tenía o ya no es válida. */
    fun open(item: NotifItem): Boolean {
        val pending = item.contentIntent ?: return false
        return runCatching { pending.send() }.isSuccess
    }
}
