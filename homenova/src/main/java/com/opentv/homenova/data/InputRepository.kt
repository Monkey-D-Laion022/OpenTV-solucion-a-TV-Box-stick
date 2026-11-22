package com.opentv.homenova.data

import android.content.Context
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.content.Intent
import com.opentv.homenova.core.InputEntry
import com.opentv.homenova.core.InputKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Enumerates TV inputs through the platform TV Input Framework.
 *
 * The framework API is optional on generic Android TV builds, so any failure is
 * converted into a small, clearly-labelled demo list instead of a crash.
 */
class InputRepository(private val context: Context) {

    suspend fun loadInputs(): List<InputEntry> = withContext(Dispatchers.IO) {
        val manager = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager
            ?: return@withContext demoInputs()

        runCatching {
            manager.tvInputList.map { info ->
                InputEntry(
                    id = info.id,
                    label = runCatching { info.loadLabel(context)?.toString() }.getOrNull()
                        ?: info.id,
                    kind = info.type.toInputKind(),
                    status = runCatching {
                        when (manager.getInputState(info.id)) {
                            TvInputManager.INPUT_STATE_CONNECTED -> "Conectado"
                            TvInputManager.INPUT_STATE_DISCONNECTED -> "Desconectado"
                            else -> "En espera"
                        }
                    }.getOrDefault("Disponible"),
                    isLaunchable = true,
                )
            }.ifEmpty { demoInputs() }
        }.getOrElse { demoInputs() }
    }

    /**
     * Opens a passthrough input. Requires the platform to expose a TV input
     * service; on devices without one the call simply fails and the UI reports it.
     */
    fun openInput(input: InputEntry): Boolean = runCatching {
        val uri = TvContract.buildChannelUriForPassthroughInput(input.id)
        context.startActivity(
            Intent(Intent.ACTION_VIEW, uri)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    }.getOrDefault(false)

    /** Best-effort shortcut to the system input picker for devices that provide one. */
    fun openSystemPicker(): Boolean = runCatching {
        context.startActivity(
            Intent("android.tv.action.VIEW_INPUTS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    }.getOrDefault(false)

    private fun Int.toInputKind(): InputKind = when (this) {
        TvInputInfo.TYPE_HDMI -> InputKind.HDMI
        TvInputInfo.TYPE_TUNER -> InputKind.TUNER
        TvInputInfo.TYPE_COMPOSITE, TvInputInfo.TYPE_SCART, TvInputInfo.TYPE_COMPONENT ->
            InputKind.COMPOSITE
        else -> InputKind.OTHER
    }

    private fun demoInputs() = listOf(
        InputEntry("demo-hdmi-1", "HDMI 1", InputKind.HDMI, "Demostración", false),
        InputEntry("demo-hdmi-2", "HDMI 2", InputKind.HDMI, "Demostración", false),
        InputEntry("demo-hdmi-3", "HDMI 3", InputKind.HDMI, "Demostración", false),
        InputEntry("demo-tuner", "Sintonizador", InputKind.TUNER, "Demostración", false),
    )
}
