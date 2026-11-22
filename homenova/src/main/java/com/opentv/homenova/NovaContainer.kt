package com.opentv.homenova

import android.app.Application
import android.content.Context
import com.opentv.homenova.data.AppRepository
import com.opentv.homenova.data.ContentRepository
import com.opentv.homenova.data.InputRepository
import com.opentv.homenova.data.SettingsRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Contenedor manual de dependencias del Home 2. Antes vivía en una subclase de `Application`; aquí es un singleton perezoso para NO tocar la
 * `Application` de OpenTV. Los repositorios son del proceso y no guardan estado.
 */
class AppContainer(context: Context) {
    val settingsRepository = SettingsRepository(context)
    val appRepository = AppRepository(context)
    val inputRepository = InputRepository(context)
    val contentRepository = ContentRepository()

    private val packageEventSink = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emite cuando se instala, quita o actualiza una app. */
    val packageEvents: SharedFlow<Unit> = packageEventSink

    fun notifyPackagesChanged() {
        packageEventSink.tryEmit(Unit)
    }

    companion object {
        @Volatile private var instance: AppContainer? = null

        fun get(application: Application): AppContainer =
            instance ?: synchronized(this) { instance ?: AppContainer(application.applicationContext).also { instance = it } }
    }
}

/** Versión mostrada en Ajustes del Home 2 (de prueba). */
const val HOME2_VERSION = "0.1 (prueba)"
