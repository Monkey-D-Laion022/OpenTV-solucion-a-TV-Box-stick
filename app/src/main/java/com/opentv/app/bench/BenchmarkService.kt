package com.opentv.app.bench

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
 * Servicio en primer plano propio de la medición (Android exige uno de tipo «captura de pantalla» para capturar).
 * Es independiente de `ProjectionService`: su notificación dice la verdad (mide, no transmite) y su botón «Detener»
 * solo corta la medición. No toca la resolución ni ninguna otra función.
 */
class BenchmarkService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            BenchHub.stop()
            stopSelf()
            return START_NOT_STICKY
        }
        createChannel()
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, BenchmarkService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("OpenTV está midiendo la captura de pantalla")
            .setContentText("Prueba local: no transmite nada ni guarda imágenes. «Detener» la corta.")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_media_pause, "Detener", stop)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
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
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Medición de captura", NotificationManager.IMPORTANCE_LOW),
        )
    }

    companion object {
        private const val CHANNEL = "opentv_bench"
        private const val ID = 4102
        private const val ACTION_STOP = "com.opentv.app.action.STOP_BENCH"

        @Volatile var ready = false
            private set

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, BenchmarkService::class.java))
            } catch (e: RuntimeException) {
                // se informa en awaitReady
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, BenchmarkService::class.java))
            } catch (e: RuntimeException) {
                // ya detenido
            }
        }

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
