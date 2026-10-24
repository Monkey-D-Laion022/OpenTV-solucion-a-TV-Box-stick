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

/**
 * Servicio en primer plano que mantiene viva la proyección mientras dura: sin él, Android puede frenar o
 * congelar la app al pasar a segundo plano y el TV se queda sin imagen. No hace nada por sí mismo: el
 * trabajo sigue en [TvProjection]. Muestra una notificación fija «OpenTV está proyectando».
 */
class ProjectionService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("OpenTV está proyectando en el TV")
            .setContentText("Tocá para volver al control remoto.")
            .setOngoing(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(ID, notification)
            }
        } catch (e: RuntimeException) {
            // Si el sistema no lo permite, la proyección sigue sin servicio.
            stopSelf()
        }
        return START_NOT_STICKY
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

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, ProjectionService::class.java)) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, ProjectionService::class.java)) }
        }
    }
}
