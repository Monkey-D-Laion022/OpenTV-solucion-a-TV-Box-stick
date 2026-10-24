package com.opentv.app.display

import android.content.Context
import androidx.annotation.Keep
import kotlin.system.exitProcess

/**
 * Se ejecuta DENTRO del proceso que Shizuku lanza con permisos de shell (no en el de la app). Solo acepta
 * `wm size` y `wm density`: aunque otra app lograra llamarlo, no podría ejecutar nada más.
 */
class ShellUserService : IUserService.Stub {

    constructor() : super()

    @Keep
    constructor(@Suppress("UNUSED_PARAMETER") context: Context) : super()

    override fun destroy() {
        exitProcess(0)
    }

    override fun exec(command: String): String {
        if (!ScreenResizer.isAllowedCommand(command)) return "ERROR: comando no permitido"
        val process = ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        return output
    }
}
