package com.opentv.app.iptv

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager

/**
 * Mantiene despiertos el procesador y la radio Wi‑Fi mientras se transmite, para que el ahorro de energía del sistema
 * no corte la entrega al televisor cuando OpenTV deja de estar a la vista. Se liberan siempre al detener. Solo requiere
 * el permiso normal WAKE_LOCK (no se pide al usuario). El efecto real sobre cada teléfono NO está medido.
 */
class TransmissionLocks(context: Context) {
    private val appContext = context.applicationContext
    private var cpu: PowerManager.WakeLock? = null
    private var wifi: WifiManager.WifiLock? = null

    @Suppress("DEPRECATION")
    fun acquire() {
        release()
        runCatching {
            val pm = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
            cpu = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OpenTV:transmision").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        runCatching {
            val wm = appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val mode = if (Build.VERSION.SDK_INT >= 29) WifiManager.WIFI_MODE_FULL_LOW_LATENCY else WifiManager.WIFI_MODE_FULL_HIGH_PERF
            wifi = wm.createWifiLock(mode, "OpenTV:wifi").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    fun release() {
        runCatching { cpu?.let { if (it.isHeld) it.release() } }
        runCatching { wifi?.let { if (it.isHeld) it.release() } }
        cpu = null
        wifi = null
    }
}
