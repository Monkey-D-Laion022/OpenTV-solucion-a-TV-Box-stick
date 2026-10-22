package com.opentv.app.connect

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

/** Lee del equipo, con API públicas, los datos que [CapabilityEvaluator] interpreta. */
class CapabilityDetector(
    private val context: Context,
    private val systemCast: SystemCastLauncher,
) {
    fun detect(externalDisplay: ExternalDisplay?): RawCapabilities = RawCapabilities(
        hasCastSettingsScreen = systemCast.isAvailable(),
        hasWifiDirectFeature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT),
        onLocalNetwork = isOnLocalNetwork(),
        playServicesAvailable = isPlayServicesAvailable(),
        externalDisplay = externalDisplay,
    )

    fun manufacturer(): String = Build.MANUFACTURER.orEmpty()

    /**
     * Wi‑Fi o Ethernet en la red activa. Si el Wi‑Fi no tiene internet y el sistema prefiere los
     * datos móviles, puede devolver `false` aunque haya red local: se explica en los textos.
     */
    fun isOnLocalNetwork(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    fun isPlayServicesAvailable(): Boolean = try {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) ==
            ConnectionResult.SUCCESS
    } catch (e: RuntimeException) {
        false
    }
}
