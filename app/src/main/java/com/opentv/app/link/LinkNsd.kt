package com.opentv.app.link

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.opentv.app.proto.OtDiscovery
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/** Un emisor/receptor OpenTV visto en la red. */
data class LinkFound(val advert: OtDiscovery.Advert, val host: String)

/**
 * Anuncio y búsqueda DNS‑SD (`_opentv._tcp`) con NsdManager. Solo descubre: no conecta. El contenido del TXT no es
 * confiable y se valida con [OtDiscovery.parse]. NO verificado en hardware (hay redes que bloquean mDNS).
 */
class LinkNsd(context: Context) {
    private val nsd = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private val pending = ConcurrentLinkedQueue<NsdServiceInfo>()
    private val resolving = AtomicBoolean(false)

    fun register(advert: OtDiscovery.Advert, onResult: (String) -> Unit) {
        unregister()
        val info = NsdServiceInfo().apply {
            serviceName = "OpenTV-" + advert.deviceId.take(6)
            serviceType = OtDiscovery.SERVICE_TYPE
            port = advert.port
            for ((k, v) in OtDiscovery.txt(advert)) setAttribute(k, v)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) { onResult("Anunciado en la red como ${serviceInfo.serviceName}") }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) { onResult("No se pudo anunciar en la red (código $errorCode); usá la dirección manual.") }
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }
        registration = listener
        try {
            nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: RuntimeException) {
            registration = null
            onResult("No se pudo anunciar en la red (${e.javaClass.simpleName}); usá la dirección manual.")
        }
    }

    fun unregister() {
        registration?.let { runCatching { nsd.unregisterService(it) } }
        registration = null
    }

    fun discover(onFound: (LinkFound) -> Unit, onStatus: (String) -> Unit) {
        stopDiscovery()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) { onStatus("Buscando equipos OpenTV…") }
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                pending.add(serviceInfo)
                resolveNext(onFound)
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { onStatus("No se pudo buscar en la red (código $errorCode).") }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        discovery = listener
        try {
            nsd.discoverServices(OtDiscovery.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: RuntimeException) {
            discovery = null
            onStatus("No se pudo buscar en la red (${e.javaClass.simpleName}).")
        }
    }

    fun stopDiscovery() {
        discovery?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        discovery = null
        pending.clear()
    }

    /** Android resuelve de a un servicio por vez. */
    private fun resolveNext(onFound: (LinkFound) -> Unit) {
        if (!resolving.compareAndSet(false, true)) return
        val next = pending.poll()
        if (next == null) { resolving.set(false); return }
        val done = {
            resolving.set(false)
            resolveNext(onFound)
        }
        try {
            nsd.resolveService(next, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) { done() }
                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val txt = HashMap<String, String>()
                    val attrs = serviceInfo.attributes
                    if (attrs != null) for ((k, v) in attrs) txt[k] = if (v == null) "" else String(v, Charsets.UTF_8)
                    val host = serviceInfo.host?.hostAddress
                    val advert = OtDiscovery.parse(txt, serviceInfo.port)
                    if (advert != null && host != null) onFound(LinkFound(advert, host))
                    done()
                }
            })
        } catch (e: RuntimeException) {
            done()
        }
    }
}
