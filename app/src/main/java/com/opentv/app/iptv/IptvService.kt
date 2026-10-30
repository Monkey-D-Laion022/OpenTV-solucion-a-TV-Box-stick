package com.opentv.app.iptv

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
import kotlinx.coroutines.delay

/**
 * Servicio en primer plano del canal IPTV. Android exige uno (con el tipo «captura de pantalla» desde la versión 14)
 * para capturar la pantalla mientras OpenTV no está a la vista. Su notificación trae «Detener». No hace el trabajo:
 * eso lo hace [IptvController]. No usa permisos nuevos: son los mismos que ya declara la app.
 */
class IptvService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            IptvController.get(this).stop()
            stopSelf()
            return START_NOT_STICKY
        }
        createChannel()
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 2, Intent(this, IptvService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("OpenTV está emitiendo el canal IPTV")
            .setContentText("Cualquier equipo de tu Wi‑Fi con la dirección puede verlo. «Detener» corta la emisión.")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_media_pause, "Detener", stop)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(
                    ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
                )
            } else {
                startForeground(ID, notification)
            }
            ready = true
        } catch (e: RuntimeException) {
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
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "Canal IPTV", NotificationManager.IMPORTANCE_LOW))
    }

    companion object {
        private const val CHANNEL = "opentv_iptv"
        private const val ID = 4102
        private const val ACTION_STOP = "com.opentv.app.action.STOP_IPTV"

        @Volatile var ready = false
            private set

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, IptvService::class.java)) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, IptvService::class.java)) }
        }

        suspend fun awaitReady() {
            var waited = 0
            while (!ready && waited < 3_000) { delay(50); waited += 50 }
            check(ready) { "Android no dejó iniciar el servicio en primer plano (necesario para capturar la pantalla)." }
        }
    }
}
