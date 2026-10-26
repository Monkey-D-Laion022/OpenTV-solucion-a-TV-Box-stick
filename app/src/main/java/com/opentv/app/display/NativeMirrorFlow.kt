package com.opentv.app.display

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Abre la pantalla de transmisión del sistema; `false` si el teléfono no pudo abrirla. */
fun interface CastOpener {
    fun open(): Boolean
}

sealed interface NativeMirrorState {
    /** Sin resolución forzada. [note] explica cómo terminó la anterior, si corresponde. */
    data class Idle(val note: String? = null) : NativeMirrorState

    data object Applying : NativeMirrorState

    /**
     * Resolución 16:9 forzada (modo global): dura hasta que el usuario la restaure.
     * [tvConnected]: hay una pantalla externa conectada ahora. [panelNote]: resultado del último intento de abrir la transmisión.
     */
    data class Forced(val size: String, val tvConnected: Boolean, val panelNote: String? = null) : NativeMirrorState

    data class Failed(val message: String) : NativeMirrorState
}

/**
 * Espejo nativo del sistema (Miracast / pantalla inalámbrica) con el teléfono en 16:9, en DOS pasos que el usuario
 * controla por separado:
 * 1) [forceResolution]: fuerza 16:9 (si no se logra, queda en error y no hay nada que restaurar);
 * 2) [openPanel]: abre la transmisión del sistema, cuando el usuario quiera; ahí toca su TV.
 * La resolución queda forzada hasta [stop] (no se restaura sola, salvo que se active [restoreOnDisconnect]). La
 * pantalla externa se vigila solo para informar. No transmite nada por su cuenta: la conexión la crea y la corta
 * Android. Lógica pura.
 */
class NativeMirrorFlow(
    private val resizer: ScreenResizer,
    private val cast: CastOpener,
    private val displayConnected: () -> Flow<Boolean>,
    private val scope: CoroutineScope,
    private val restoreOnDisconnect: Boolean = false,
    /** Si al forzar 16:9 también se fija la rotación en horizontal (opción de la central; por defecto no). */
    private val lockLandscape: () -> Boolean = { false },
) {
    private val _state = MutableStateFlow<NativeMirrorState>(NativeMirrorState.Idle())
    val state: StateFlow<NativeMirrorState> = _state.asStateFlow()

    private var job: Job? = null

    private val busy: Boolean
        get() = _state.value is NativeMirrorState.Applying || _state.value is NativeMirrorState.Forced

    /** Paso 1. */
    fun forceResolution() {
        if (busy) return
        _state.value = NativeMirrorState.Applying // antes de lanzar: un segundo toque no arranca otro flujo
        job = scope.launch { run() }
    }

    /** Paso 2. Solo con la resolución ya forzada. Devuelve `false` si no se pudo abrir. */
    fun openPanel(opener: CastOpener = cast): Boolean {
        val current = _state.value as? NativeMirrorState.Forced ?: return false
        val opened = opener.open()
        _state.value = current.copy(
            panelNote = if (opened) {
                null
            } else {
                "No se pudo abrir el panel de transmisión. Probá con el botón «Emitir» / «Smart View» del panel rápido (deslizá desde arriba)."
            },
        )
        return opened
    }

    /** Restaura la resolución y corta la vigilancia. */
    fun stop() {
        job?.cancel()
        job = scope.launch {
            _state.value = restoreToState("Resolución restaurada.")
        }
    }

    private suspend fun run() {
        val size = when (val r = resizer.apply(lockLandscape = lockLandscape())) {
            is ResizeResult.Applied -> r.plan.sizeArg
            ResizeResult.AlreadyApplied -> "ya forzada"
            is ResizeResult.Failed -> {
                _state.value = NativeMirrorState.Failed("No se pudo forzar 16:9. ${r.message}")
                return
            }
            else -> {
                _state.value = NativeMirrorState.Failed("Respuesta inesperada al cambiar la resolución.")
                return
            }
        }
        _state.value = NativeMirrorState.Forced(size, tvConnected = false)

        // Solo informa si hay una pantalla externa conectada.
        var wasConnected = false
        displayConnected().collect { connected ->
            val s = _state.value as? NativeMirrorState.Forced ?: return@collect
            _state.value = s.copy(tvConnected = connected)
            if (connected) wasConnected = true
            if (!connected && wasConnected && restoreOnDisconnect) {
                _state.value = restoreToState("La transmisión terminó.")
                job?.cancel()
            }
        }
    }

    /** Restaura y devuelve el estado resultante: Idle con la nota, o Failed si no se pudo. */
    private suspend fun restoreToState(note: String?): NativeMirrorState =
        when (val r = resizer.restore()) {
            ResizeResult.Restored -> NativeMirrorState.Idle(note)
            ResizeResult.NothingToRestore -> NativeMirrorState.Idle(note)
            is ResizeResult.Failed -> NativeMirrorState.Failed(r.message)
            else -> NativeMirrorState.Idle(note)
        }
}
