package com.opentv.app.bench

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.view.Display
import androidx.annotation.RequiresApi

/**
 * Indicadores PÚBLICOS y de solo lectura: estado térmico de Android (desde la 10) y temperatura de la batería.
 * No es la temperatura del procesador; si una API no existe devuelve `null` («no disponible»).
 */
object ThermalProbe {

    fun read(context: Context): ThermalSnapshot {
        val status: String? = if (Build.VERSION.SDK_INT >= 29) thermalStatus(context) else null
        val battery: Double? = try {
            val intent: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val tenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
            if (tenths == Int.MIN_VALUE) null else tenths / 10.0
        } catch (e: RuntimeException) {
            null
        }
        return ThermalSnapshot(status, battery)
    }

    @RequiresApi(29)
    private fun thermalStatus(context: Context): String? = try {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        when (pm.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> "normal"
            PowerManager.THERMAL_STATUS_LIGHT -> "leve"
            PowerManager.THERMAL_STATUS_MODERATE -> "moderado"
            PowerManager.THERMAL_STATUS_SEVERE -> "severo"
            PowerManager.THERMAL_STATUS_CRITICAL -> "crítico"
            PowerManager.THERMAL_STATUS_EMERGENCY -> "emergencia"
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "apagado inminente"
            else -> null
        }
    } catch (e: RuntimeException) {
        null
    }

    /** Frecuencia de actualización que Android informa para la pantalla (solo lectura; no se cambia nunca). */
    fun refreshRateHz(context: Context): Float? = try {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        dm.getDisplay(Display.DEFAULT_DISPLAY)?.refreshRate
    } catch (e: RuntimeException) {
        null
    }
}
