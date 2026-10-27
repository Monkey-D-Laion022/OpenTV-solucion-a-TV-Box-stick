package com.opentv.app.bench

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Lo que muestra la pantalla de medición. */
data class BenchUi(
    val running: Boolean = false,
    val status: String = "Listo. Elegí una prueba.",
    val results: List<ConfigResult> = emptyList(),
    val thermalBefore: ThermalSnapshot? = null,
    val thermalAfter: ThermalSnapshot? = null,
    val refreshHz: Float? = null,
)

/**
 * Corre la medición. Solo arranca cuando el usuario pulsa un botón y acepta el permiso de captura de Android. Libera
 * todo (pantalla virtual, codificador, captura, servicio) al terminar, al detener, si se revoca el permiso o si hay
 * un error. Es independiente del resto de OpenTV: no usa `ScreenResizer` ni cambia ningún ajuste del teléfono.
 */
object BenchHub {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _ui = MutableStateFlow(BenchUi())
    val ui: StateFlow<BenchUi> = _ui.asStateFlow()

    @Volatile private var job: Job? = null
    @Volatile private var stopReason: String? = null
    @Volatile private var finishing = false

    fun start(context: Context, resultCode: Int, data: Intent, options: List<BenchPlan.Option>, phaseMillis: Long = 6_000L) {
        if (options.isEmpty() || job?.isActive == true) return
        val app = context.applicationContext
        stopReason = null
        finishing = false
        job = scope.launch {
            var projection: MediaProjection? = null
            var session: BenchSession? = null
            try {
                _ui.value = BenchUi(
                    running = true,
                    status = "Preparando…",
                    thermalBefore = ThermalProbe.read(app),
                    refreshHz = ThermalProbe.refreshRateHz(app),
                )
                BenchmarkService.start(app)
                BenchmarkService.awaitReady()
                val manager = app.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                val p = manager.getMediaProjection(resultCode, data)
                    ?: throw IllegalStateException("Android no entregó la captura de pantalla.")
                projection = p
                // Desde Android 14 hay que registrar el callback antes de crear la pantalla virtual.
                p.registerCallback(
                    object : MediaProjection.Callback() {
                        override fun onStop() {
                            if (!finishing) {
                                if (stopReason == null) stopReason = "La captura se detuvo (permiso revocado o cortado desde el sistema)."
                                job?.cancel()
                            }
                        }
                    },
                    Handler(Looper.getMainLooper()),
                )
                val s = BenchSession(app, p, AndroidEncoderLookup())
                session = s
                val first = options.first().config
                s.open(first.width, first.height)
                for ((i, option) in options.withIndex()) {
                    _ui.update { it.copy(status = "Midiendo ${i + 1} de ${options.size}: ${option.config.label} (primero captura, después codificación)…") }
                    val result = s.measure(option, phaseMillis)
                    _ui.update { it.copy(results = it.results + result) }
                }
                _ui.update { it.copy(thermalAfter = ThermalProbe.read(app), status = "Medición terminada.") }
            } catch (e: CancellationException) {
                _ui.update { it.copy(status = stopReason ?: "Medición detenida.") }
            } catch (e: Exception) {
                _ui.update { it.copy(status = "Error: ${e.message ?: e.javaClass.simpleName}") }
            } finally {
                finishing = true
                try {
                    session?.close()
                } catch (e: RuntimeException) {
                    // ya liberada
                }
                try {
                    projection?.stop()
                } catch (e: RuntimeException) {
                    // ya detenida
                }
                BenchmarkService.stop(app)
                _ui.update { it.copy(running = false) }
            }
        }
    }

    /** Detiene la medición y libera todo. Seguro de llamar varias veces o sin medición en curso. */
    fun stop() {
        job?.cancel()
    }
}
