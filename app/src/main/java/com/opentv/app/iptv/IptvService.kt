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
import com.opentv.app.transmission.TransmissionRegistry
import com.opentv.app.transmission.TransmissionText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Servicio en primer plano de las transmisiones de OpenTV (IPTV y OpenTV Link). Android exige uno (con el tipo «captura de
 * pantalla» desde la versión 14) para capturar la pantalla mientras OpenTV no está a la vista. Su notificación es el
 * control general: lee de [TransmissionRegistry] la tecnología activa, resolución, FPS, bitrate y audio, y su botón
 * «Detener» corta todas las transmisiones registradas. No hace el trabajo: eso lo hacen los controladores.
 * No usa permisos nuevos: son los mismos que ya declara la app.
 */
class IptvService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watching = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            TransmissionRegistry.stopAll()
            stopSelf()
            return START_NOT_STICKY
        }
        createChannel()
        try {
            val notification = build(TransmissionRegistry.state.value)
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
            return START_NOT_STICKY
        }
        if (!watching) {
            watching = true
            scope.launch {
                TransmissionRegistry.state.collect { list ->
                    getSystemService(NotificationManager::class.java).notify(ID, build(list))
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun build(list: List<com.opentv.app.transmission.TransmissionInfo>): Notification {
        val text = TransmissionText.render(list)
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 2, Intent(this, IptvService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val footer = "El espejo nativo del sistema y DLNA tienen su propio aviso y no aparecen aquí."
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(text.title)
            .setContentText(text.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.bigText + "\n" + footer))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_media_pause, "Detener", stop)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        ready = false
        // Si el servicio muere con una emisión viva, no fue una parada pedida (esas vacían antes el registro).
        if (!TransmissionRegistry.isEmpty) IptvController.get(this).onServiceLost()
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "Transmisiones de OpenTV", NotificationManager.IMPORTANCE_LOW))
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

        /** Apaga el servicio solo si ya no queda ninguna transmisión registrada (otra tecnología puede seguir activa). */
        fun stopIfIdle(context: Context) {
            if (TransmissionRegistry.isEmpty) runCatching { context.stopService(Intent(context, IptvService::class.java)) }
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
