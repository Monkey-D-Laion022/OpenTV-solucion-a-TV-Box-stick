package com.opentv.app.miracast

import android.annotation.SuppressLint
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Etapa A: busca dispositivos Wi-Fi Direct cercanos con la API PÚBLICA (discoverPeers / requestPeers).
 *
 * Solo descubre. No conecta, no forma grupos, no abre sockets y no usa APIs ocultas. Se detiene sola a los
 * [P2pPolicy.DISCOVERY_SECONDS] segundos o cuando se pide. No cambia ningún ajuste del teléfono.
 */
class P2pDiscovery(private val app: Application, private val scope: CoroutineScope) {
    private val _ui = MutableStateFlow(P2pUi())
    val ui: StateFlow<P2pUi> = _ui.asStateFlow()

    private var manager: WifiP2pManager? = null
    private var channel: WifiP2pManager.Channel? = null
    private var receiver: BroadcastReceiver? = null
    private var timeout: Job? = null
    private var startedAt = 0L
    private var running = false

    /** Dispositivos vistos durante la prueba, sin repetir (la dirección solo se usa para no duplicar y no se muestra). */
    private val seen = LinkedHashMap<String, P2pPeer>()

    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT >= P2pPolicy.FIRST_SDK_WITHOUT_LOCATION &&
            ContextCompat.checkSelfPermission(app, P2pPolicy.NEARBY_PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun permissionDenied() {
        _ui.update { it.copy(phase = P2pPhase.PERMISSION_DENIED) }
        log("El usuario no concedió el permiso de dispositivos Wi-Fi cercanos.")
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return
        seen.clear()
        _ui.value = P2pUi()
        startedAt = SystemClock.elapsedRealtime()

        if (!P2pPolicy.canRun(Build.VERSION.SDK_INT)) {
            _ui.update { it.copy(phase = P2pPhase.BLOCKED_OLD_ANDROID) }
            log("Android ${Build.VERSION.SDK_INT} (API): la búsqueda exige permiso de ubicación y no está declarado. No se ejecuta.")
            return
        }
        val m = app.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
        if (m == null || !app.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)) {
            _ui.update { it.copy(phase = P2pPhase.UNSUPPORTED) }
            log("El sistema no ofrece Wi-Fi Direct (servicio o característica ausente).")
            return
        }
        if (!hasPermission()) {
            _ui.update { it.copy(phase = P2pPhase.NEED_PERMISSION) }
            log("Falta el permiso NEARBY_WIFI_DEVICES.")
            return
        }

        val location = runCatching {
            LocationManagerCompat.isLocationEnabled(app.getSystemService(Context.LOCATION_SERVICE) as LocationManager)
        }.getOrNull()
        _ui.update { it.copy(locationOn = location) }
        when (location) {
            false -> log("La ubicación del teléfono está DESACTIVADA. Android la exige activa para Wi-Fi Direct: puede no entregar resultados.")
            true -> log("Ubicación del teléfono activada (interruptor del sistema).")
            null -> log("No se pudo consultar el interruptor de ubicación.")
        }

        manager = m
        val ch = m.initialize(app, Looper.getMainLooper(), null)
        channel = ch
        val rx = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                        val enabled = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1) == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                        _ui.update { it.copy(p2pEnabled = enabled) }
                        log("Wi-Fi Direct informado por el sistema: ${if (enabled) "habilitado" else "deshabilitado"}.")
                    }
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> requestPeers()
                }
            }
        }
        receiver = rx
        ContextCompat.registerReceiver(
            app, rx,
            IntentFilter().apply {
                addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        running = true
        _ui.update { it.copy(phase = P2pPhase.SEARCHING) }
        log("Buscando dispositivos (máximo ${P2pPolicy.DISCOVERY_SECONDS} s). No se conecta a ninguno.")
        try {
            m.discoverPeers(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    log("Android aceptó iniciar la búsqueda (no significa que haya encontrado algo).")
                }

                override fun onFailure(reason: Int) {
                    log("Android rechazó la búsqueda: ${P2pText.failure(reason)}.")
                    _ui.update { it.copy(failureCode = reason) }
                    finish(P2pPhase.FAILED)
                }
            })
        } catch (e: SecurityException) {
            log("Excepción de seguridad al buscar: ${e.message ?: "sin detalle"}.")
            finish(P2pPhase.FAILED)
            return
        }
        timeout = scope.launch {
            delay(P2pPolicy.DISCOVERY_SECONDS * 1000L)
            log("Se cumplió el tiempo de búsqueda.")
            stop()
        }
    }

    /** Detiene la búsqueda y libera todo. Se puede llamar varias veces. */
    fun stop() {
        if (!running) return
        finish(P2pPhase.DONE)
    }

    @SuppressLint("MissingPermission")
    private fun requestPeers() {
        val m = manager ?: return
        val ch = channel ?: return
        try {
            m.requestPeers(ch) { list -> onPeers(list) }
        } catch (e: SecurityException) {
            log("Excepción de seguridad al pedir la lista: ${e.message ?: "sin detalle"}.")
        }
    }

    private fun onPeers(list: WifiP2pDeviceList?) {
        val devices: Collection<WifiP2pDevice> = list?.deviceList ?: emptyList()
        seen.replaceAll { _, p -> p.copy(inLatestScan = false) }
        for (d in devices) {
            seen[d.deviceAddress ?: d.deviceName ?: "?"] = P2pPeer(
                name = d.deviceName ?: "",
                primaryType = d.primaryDeviceType,
                secondaryType = d.secondaryDeviceType,
                statusCode = d.status,
                isGroupOwner = d.isGroupOwner,
                serviceDiscoveryCapable = d.isServiceDiscoveryCapable,
                inLatestScan = true,
                address = d.deviceAddress,
            )
        }
        _ui.update { it.copy(peers = seen.values.toList()) }
        log("Lista entregada por Android: ${devices.size} dispositivo(s); vistos en total: ${seen.size}.")
    }

    private fun finish(phase: P2pPhase) {
        if (!running && phase == P2pPhase.DONE) return
        running = false
        timeout?.cancel()
        timeout = null
        val m = manager
        val ch = channel
        if (m != null && ch != null) {
            runCatching {
                m.stopPeerDiscovery(ch, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() = log("Búsqueda detenida.")
                    override fun onFailure(reason: Int) = log("No se pudo confirmar la detención: ${P2pText.failure(reason)}.")
                })
            }
        }
        receiver?.let { runCatching { app.unregisterReceiver(it) } }
        receiver = null
        if (ch != null && Build.VERSION.SDK_INT >= 27) runCatching { ch.close() }
        channel = null
        manager = null
        _ui.update { it.copy(phase = phase) }
    }

    private fun log(text: String) {
        val seconds = (SystemClock.elapsedRealtime() - startedAt) / 1000
        _ui.update { it.copy(log = (it.log + "+${seconds} s  $text").takeLast(60)) }
    }
}
