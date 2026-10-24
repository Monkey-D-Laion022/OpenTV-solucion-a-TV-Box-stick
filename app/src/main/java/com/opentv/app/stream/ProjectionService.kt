package com.opentv.app.stream

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.opentv.app.MainActivity
import com.opentv.app.OpenTvApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Servicio en primer plano que mantiene viva la proyección mientras dura. En modo espejo declara además el tipo
 * «captura de pantalla», que Android exige (desde la 14) antes de empezar a capturar. No hace el trabajo por sí
 * mismo: sigue en [TvProjection]. Su notificación tiene el botón «Detener», imprescindible en el espejo porque
 * ahí el teléfono se usa con otras apps y OpenTV no está a la vista.
 */
class ProjectionService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            val projection = (application as OpenTvApp).container.projection
            CoroutineScope(Dispatchers.Main).launch { projection.stop() }
            stopSelf()
            return START_NOT_STICKY
        }
        val mirror = intent?.getBooleanExtra(EXTRA_MIRROR, false) == true
        createChannel()
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ProjectionService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(if (mirror) "OpenTV está espejando la pantalla en el TV" else "OpenTV está proyectando en el TV")
            .setContentText("Tocá «Detener» para cortar la transmisión.")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_media_pause, "Detener", stop)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                if (mirror) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                startForeground(ID, notification, type)
            } else {
                startForeground(ID, notification)
            }
            ready = true
        } catch (e: RuntimeException) {
            // Si el sistema no lo permite, la proyección sigue sin servicio (el espejo no puede empezar).
            ready = false
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        ready = false
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Proyección al TV", NotificationManager.IMPORTANCE_LOW),
        )
    }

    companion object {
        private const val CHANNEL = "opentv_projection"
        private const val ID = 4101
        private const val ACTION_STOP = "com.opentv.app.action.STOP_PROJECTION"
        private const val EXTRA_MIRROR = "mirror"

        /** El servicio ya está en primer plano (la captura de pantalla solo puede empezar después). */
        @Volatile var ready = false
            private set

        fun start(context: Context, mirror: Boolean = false) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, ProjectionService::class.java).putExtra(EXTRA_MIRROR, mirror),
                )
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, ProjectionService::class.java)) }
        }

        /** Espera a que el servicio esté en primer plano; falla si no lo logra en 3 s. */
        suspend fun awaitReady() {
            var waited = 0
            while (!ready && waited < 3_000) {
                delay(50)
                waited += 50
            }
            check(ready) { "Android no dejó iniciar el servicio en primer plano (necesario para capturar la pantalla)." }
        }
    }
}
