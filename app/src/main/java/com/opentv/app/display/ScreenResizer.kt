package com.opentv.app.display

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Ejecuta comandos `wm` con permisos de sistema (en la app: Shizuku). */
interface ShellRunner {
    /** Devuelve lo que imprimió el comando; lanza excepción si no pudo ejecutarlo. */
    suspend fun exec(command: String): String
}

/** Qué hay que deshacer. Se guarda en disco ANTES de cambiar nada, para poder restaurar tras un cierre brusco. */
data class PendingResize(
    /** Tamaño que había antes («1080x2400») o `null` si no había ninguno forzado. */
    val prevSize: String?,
    val prevDensity: Int?,
    val appliedSize: String,
    val appliedDensity: Int,
    /** Si se cambió también la densidad (si no, al restaurar no se toca). Los pendientes viejos la cambiaban. */
    val densityChanged: Boolean = true,
    /** Si se fijó la rotación en horizontal; en ese caso se guardan los valores previos para devolverlos. */
    val rotationLocked: Boolean = false,
    val prevAutoRotate: Int? = null,
    val prevUserRotation: Int? = null,
)

interface ResizeStore {
    var pending: PendingResize?
}

/** Lectura de la salida de `wm size` y `wm density`. Lógica pura. */
object WmParse {
    data class SizeInfo(val width: Int, val height: Int, val override: String?)
    data class DensityInfo(val physical: Int, val override: Int?)

    private val SIZE = Regex("""(Physical|Override) size:\s*(\d+)x(\d+)""")
    private val DENSITY = Regex("""(Physical|Override) density:\s*(\d+)""")

    fun size(output: String): SizeInfo? {
        var physical: Pair<Int, Int>? = null
        var override: String? = null
        for (m in SIZE.findAll(output)) {
            val w = m.groupValues[2].toInt()
            val h = m.groupValues[3].toInt()
            if (m.groupValues[1] == "Physical") physical = w to h else override = "${w}x$h"
        }
        return physical?.let { SizeInfo(it.first, it.second, override) }
    }

    fun density(output: String): DensityInfo? {
        var physical: Int? = null
        var override: Int? = null
        for (m in DENSITY.findAll(output)) {
            val d = m.groupValues[2].toInt()
            if (m.groupValues[1] == "Physical") physical = d else override = d
        }
        return physical?.let { DensityInfo(it, override) }
    }
}

/** Tamaño 16:9 en vertical al que se lleva el teléfono, con la densidad ajustada para que la interfaz no quede minúscula. */
data class ResizePlan(val width: Int, val height: Int, val density: Int) {
    val sizeArg: String get() = "${width}x$height"

    companion object {
        private const val MIN_WIDTH = 1080

        /**
         * @param physW ancho físico en vertical (el menor de los dos lados).
         * @param currentDensity densidad efectiva actual.
         * Mantiene el ancho en dp: densidad nueva = densidad × ancho nuevo / ancho físico.
         */
        fun sixteenByNine(physW: Int, currentDensity: Int): ResizePlan {
            val w = maxOf(physW, MIN_WIDTH) / 2 * 2
            val h = (w * 16L / 9).toInt() / 2 * 2
            val density = (currentDensity.toLong() * w / physW).toInt()
            return ResizePlan(w, h, density)
        }
    }
}

sealed interface ResizeResult {
    data class Applied(val plan: ResizePlan, val densityChanged: Boolean = false) : ResizeResult
    data object AlreadyApplied : ResizeResult
    data object NothingToRestore : ResizeResult
    data object Restored : ResizeResult
    data class Failed(val message: String) : ResizeResult
}

/**
 * Fuerza el teléfono a 16:9 vertical (para que, en horizontal, las apps rindan en 1920×1080 sin franjas al espejar)
 * y lo restaura. Todo pasa por [ShellRunner]; el estado a deshacer vive en [ResizeStore]. Lógica pura.
 */
class ScreenResizer(
    private val shell: ShellRunner,
    private val store: ResizeStore,
) {
    private val mutex = Mutex()
    private val _pending = MutableStateFlow(store.pending != null)

    /** `true` si el teléfono tiene la resolución modificada por OpenTV y falta restaurarla. */
    val pending: StateFlow<Boolean> = _pending.asStateFlow()
    val hasPending: Boolean get() = store.pending != null

    /**
     * @param changeDensity cambiar también la densidad. Por defecto NO: al pasar a 16:9 el alto en dp queda igual o mayor
     * que en 20:9, así que el launcher no pierde filas y mueve menos íconos. Con `true` el alto en dp baja y el
     * launcher reacomoda (y guarda) los íconos.
     */
    suspend fun apply(changeDensity: Boolean = false, lockLandscape: Boolean = true): ResizeResult = mutex.withLock {
        if (store.pending != null) return ResizeResult.AlreadyApplied
        try {
            val size = WmParse.size(shell.exec("wm size"))
                ?: return ResizeResult.Failed("No se pudo leer la resolución actual (`wm size`).")
            val density = WmParse.density(shell.exec("wm density"))
                ?: return ResizeResult.Failed("No se pudo leer la densidad actual (`wm density`).")
            val portraitWidth = minOf(size.width, size.height)
            val currentDensity = density.override ?: density.physical
            val plan = ResizePlan.sixteenByNine(portraitWidth, currentDensity)

            // Rotación: se lee antes de cambiar nada. Si no se puede leer, no se toca (el 16:9 sigue sin ella).
            val prevAuto = if (lockLandscape) readSetting("accelerometer_rotation") else null
            val prevUser = if (lockLandscape) readSetting("user_rotation") else null
            val lock = lockLandscape && (prevAuto != null || prevUser != null)

            // Primero se anota qué hay que deshacer, después se cambia.
            store.pending = PendingResize(
                size.override, density.override, plan.sizeArg,
                appliedDensity = if (changeDensity) plan.density else currentDensity,
                densityChanged = changeDensity,
                rotationLocked = lock,
                prevAutoRotate = prevAuto,
                prevUserRotation = prevUser,
            )
            _pending.value = true
            shell.exec("wm size ${plan.sizeArg}")
            if (changeDensity) shell.exec("wm density ${plan.density}")
            if (lock) {
                // Rotación fija en horizontal: con el tamaño forzado en vertical (1080x1920), girar a mano o que una
                // app la pida a mitad del espejo parte la pantalla. Es opcional: si falla no se cancela el 16:9.
                runCatching {
                    shell.exec("settings put system accelerometer_rotation 0")
                    shell.exec("settings put system user_rotation 1")
                }
            }

            val check = WmParse.size(shell.exec("wm size"))
            if (check?.override != plan.sizeArg) {
                restoreLocked()
                return ResizeResult.Failed("El sistema no aceptó el cambio de resolución; se dejó como estaba.")
            }
            ResizeResult.Applied(plan, changeDensity)
        } catch (e: Exception) {
            if (store.pending != null) runCatching { restoreLocked() }
            ResizeResult.Failed("No se pudo cambiar la resolución: ${e.message ?: e.javaClass.simpleName}.")
        }
    }

    suspend fun restore(): ResizeResult = mutex.withLock {
        try {
            restoreLocked()
        } catch (e: Exception) {
            ResizeResult.Failed("No se pudo restaurar la resolución: ${e.message ?: e.javaClass.simpleName}.")
        }
    }

    private suspend fun restoreLocked(): ResizeResult {
        val p = store.pending ?: return ResizeResult.NothingToRestore
        shell.exec(if (p.prevSize == null) "wm size reset" else "wm size ${p.prevSize}")
        if (p.densityChanged) {
            shell.exec(if (p.prevDensity == null) "wm density reset" else "wm density ${p.prevDensity}")
        }
        if (p.rotationLocked) {
            // Mejor esfuerzo: la rotación no debe impedir devolver la resolución.
            runCatching {
                restoreSetting("user_rotation", p.prevUserRotation)
                restoreSetting("accelerometer_rotation", p.prevAutoRotate)
            }
        }
        val now = WmParse.size(shell.exec("wm size"))
        if (now?.override == p.appliedSize) {
            return ResizeResult.Failed("La resolución sigue modificada. Probá «Restaurar resolución» otra vez.")
        }
        store.pending = null
        _pending.value = false
        return ResizeResult.Restored
    }

    /** Valor entero de un ajuste de sistema, o `null` si no existe o no se pudo leer. */
    private suspend fun readSetting(name: String): Int? =
        runCatching { shell.exec("settings get system $name").trim().toIntOrNull() }.getOrNull()

    private suspend fun restoreSetting(name: String, value: Int?) {
        shell.exec(if (value == null) "settings delete system $name" else "settings put system $name $value")
    }

    companion object {
        /** Comandos que el servicio con permisos de sistema acepta ejecutar (nada más). */
        private val ALLOWED = Regex("""^wm (size|density)( reset| \d+x\d+| \d+)?$""")
        private val ALLOWED_SETTINGS = Regex("""^settings (get|delete) system (accelerometer_rotation|user_rotation)$|^settings put system (accelerometer_rotation|user_rotation) [0-3]$""")

        fun isAllowedCommand(command: String): Boolean = ALLOWED.matches(command) || ALLOWED_SETTINGS.matches(command)
    }
}
