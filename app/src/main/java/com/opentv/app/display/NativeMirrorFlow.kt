package com.opentv.app.display

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Abre la pantalla de transmisión del sistema; `false` si el teléfono no la tiene. */
fun interface CastOpener {
    fun open(): Boolean
}

sealed interface NativeMirrorState {
    /** Sin espejo. [note] explica cómo terminó el anterior, si corresponde. */
    data class Idle(val note: String? = null) : NativeMirrorState

    data object Applying : NativeMirrorState

    /** Resolución ya forzada; falta que el usuario toque el TV en el panel del sistema. */
    data class WaitingForTv(val size: String) : NativeMirrorState

    /** Hay una pantalla externa conectada (la transmisión del sistema está activa). */
    data class Mirroring(val size: String) : NativeMirrorState

    data class Failed(val message: String) : NativeMirrorState
}

/**
 * Espejo nativo del sistema (Miracast / pantalla inalámbrica) con el teléfono en 16:9:
 * 1) fuerza la resolución (obligatorio: si no se logra, NO se abre el panel de transmisión),
 * 2) abre el panel de transmisión del sistema, donde el usuario toca su TV,
 * 3) vigila la pantalla externa y, al desconectarse (o si nunca se conecta), restaura la resolución.
 * No transmite nada por su cuenta: la conexión la crea y la corta Android. Lógica pura.
 */
class NativeMirrorFlow(
    private val resizer: ScreenResizer,
    private val cast: CastOpener,
    private val displayConnected: () -> Flow<Boolean>,
    private val scope: CoroutineScope,
    private val settleMs: Long = 700,
    private val connectTimeoutMs: Long = 180_000,
) {
    private val _state = MutableStateFlow<NativeMirrorState>(NativeMirrorState.Idle())
    val state: StateFlow<NativeMirrorState> = _state.asStateFlow()

    private var job: Job? = null

    private val busy: Boolean
        get() = when (_state.value) {
            NativeMirrorState.Applying, is NativeMirrorState.WaitingForTv, is NativeMirrorState.Mirroring -> true
            else -> false
        }

    fun start() {
        if (busy) return
        _state.value = NativeMirrorState.Applying // antes de lanzar: un segundo toque no arranca otro flujo
        job = scope.launch { run() }
    }

    /** Corta la espera/vigilancia y restaura la resolución. */
    fun stop() {
        job?.cancel()
        job = scope.launch {
            _state.value = restoreToState("Espejo detenido.")
        }
    }

    private suspend fun run() {
        val size = when (val r = resizer.apply()) {
            is ResizeResult.Applied -> r.plan.sizeArg
            ResizeResult.AlreadyApplied -> "ya forzada"
            is ResizeResult.Failed -> {
                _state.value = NativeMirrorState.Failed("No se abrió la transmisión porque no se pudo forzar 16:9. ${r.message}")
                return
            }
            else -> {
                _state.value = NativeMirrorState.Failed("Respuesta inesperada al cambiar la resolución.")
                return
            }
        }
        delay(settleMs) // el sistema tarda un instante en reacomodar la pantalla

        if (!cast.open()) {
            val back = restoreToState(null)
            _state.value = NativeMirrorState.Failed(
                "Este teléfono no tiene pantalla de transmisión." + if (back is NativeMirrorState.Failed) " ${back.message}" else " Se restauró la resolución.",
            )
            return
        }
        _state.value = NativeMirrorState.WaitingForTv(size)

        val connected = withTimeoutOrNull(connectTimeoutMs) { displayConnected().first { it } }
        if (connected == null) {
            _state.value = restoreToState("No se conectó ninguna pantalla a tiempo.")
            return
        }
        _state.value = NativeMirrorState.Mirroring(size)

        displayConnected().first { !it }
        _state.value = restoreToState("La transmisión terminó.")
    }

    /** Restaura y devuelve el estado resultante: Idle con la nota, o Failed si no se pudo. */
    private suspend fun restoreToState(note: String?): NativeMirrorState =
        when (val r = resizer.restore()) {
            ResizeResult.Restored -> NativeMirrorState.Idle(listOfNotNull(note, "Resolución restaurada.").joinToString(" "))
            ResizeResult.NothingToRestore -> NativeMirrorState.Idle(note)
            is ResizeResult.Failed -> NativeMirrorState.Failed(r.message)
            else -> NativeMirrorState.Idle(note)
        }
}
