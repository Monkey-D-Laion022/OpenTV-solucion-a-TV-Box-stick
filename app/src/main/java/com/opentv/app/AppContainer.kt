package com.opentv.app

import android.content.Context
import com.opentv.app.apps.AppIconLoader
import com.opentv.app.apps.AppLauncher
import com.opentv.app.apps.AppsRepository
import com.opentv.app.apps.FavoritesStore
import com.opentv.app.connect.CapabilityDetector
import com.opentv.app.connect.CastDiscovery
import com.opentv.app.connect.CastSender
import com.opentv.app.connect.DlnaController
import com.opentv.app.connect.ExternalDisplayMonitor
import com.opentv.app.connect.SocketLanHttp
import com.opentv.app.connect.SystemCastLauncher
import com.opentv.app.connect.UdpSsdpSearch
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Dependencias compartidas, creadas solo cuando se usan (instanciación perezosa).
 * Inyección manual: alcanza para este tamaño de proyecto y evita sumar un framework.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val repository: AppsRepository by lazy { AppsRepository(appContext) }
    val favorites: FavoritesStore by lazy { FavoritesStore(appContext) }
    val launcher: AppLauncher by lazy { AppLauncher(appContext) }

    // Conectividad (Fase 3). Nada de esto abre red ni registra escuchas hasta que se usa.
    val systemCast: SystemCastLauncher by lazy { SystemCastLauncher(appContext) }
    val displayMonitor: ExternalDisplayMonitor by lazy { ExternalDisplayMonitor(appContext) }
    val capabilityDetector: CapabilityDetector by lazy { CapabilityDetector(appContext, systemCast) }
    val castDiscovery: CastDiscovery by lazy { CastDiscovery(appContext) }
    val dlna: DlnaController by lazy { DlnaController(UdpSsdpSearch(), SocketLanHttp()) }
    val castSender: CastSender by lazy { CastSender(appContext) }

    /** Último enlace escrito en el reproductor o en «Conectar TV»; se comparte entre ambas pantallas. */
    val sharedLink = MutableStateFlow("")

    private val iconLoaderHolder = lazy { AppIconLoader(appContext) }
    val iconLoader: AppIconLoader get() = iconLoaderHolder.value

    /** Libera la caché de íconos cuando el sistema pide memoria. No crea nada si no se usó. */
    fun trimMemory() {
        if (iconLoaderHolder.isInitialized()) iconLoaderHolder.value.clear()
    }
}
