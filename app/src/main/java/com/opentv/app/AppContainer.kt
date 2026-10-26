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
import com.opentv.app.connect.OutputController
import com.opentv.app.connect.SocketLanHttp
import com.opentv.app.connect.SystemCastLauncher
import com.opentv.app.connect.UdpSsdpSearch
import com.opentv.app.display.CastOpener
import com.opentv.app.display.NativeMirrorFlow
import com.opentv.app.display.PrefsResizeStore
import com.opentv.app.display.ScreenResizer
import com.opentv.app.display.ShizukuShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.opentv.app.stream.TvProjection
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
    val output: OutputController by lazy { OutputController(castSender, dlna) }

    // Resolución 16:9 forzada con Shizuku (solo al espejar la pantalla).
    val shizuku: ShizukuShell by lazy { ShizukuShell(appContext) }
    val resizer: ScreenResizer by lazy { ScreenResizer(shizuku, PrefsResizeStore(appContext)) }

    // Espejo nativo del sistema con 16:9 forzado. Usa un alcance propio de la app: debe seguir vigilando aunque OpenTV
    // pase a segundo plano mientras el usuario elige el TV en el panel del sistema.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val nativeMirror: NativeMirrorFlow by lazy {
        NativeMirrorFlow(
            resizer = resizer,
            cast = CastOpener { systemCast.open() },
            displayConnected = { displayMonitor.observe().map { it != null } },
            scope = appScope,
        )
    }

    private val browserMirrorHolder = lazy { com.opentv.app.stream.BrowserMirror(appContext) }
    val browserMirror: com.opentv.app.stream.BrowserMirror get() = browserMirrorHolder.value

    private val projectionHolder = lazy { TvProjection(appContext, dlna, resizer) }
    val projection: TvProjection get() = projectionHolder.value

    /** Corta la proyección al TV si estaba activa; no crea nada si nunca se usó. */
    fun shutdownProjection() {
        if (projectionHolder.isInitialized()) projectionHolder.value.shutdown()
        if (browserMirrorHolder.isInitialized()) browserMirrorHolder.value.shutdown()
    }

    /** Último enlace escrito en el reproductor o en «Conectar TV»; se comparte entre ambas pantallas. */
    val sharedLink = MutableStateFlow("")

    private val iconLoaderHolder = lazy { AppIconLoader(appContext) }
    val iconLoader: AppIconLoader get() = iconLoaderHolder.value

    /** Libera la caché de íconos cuando el sistema pide memoria. No crea nada si no se usó. */
    fun trimMemory() {
        if (iconLoaderHolder.isInitialized()) iconLoaderHolder.value.clear()
    }
}
