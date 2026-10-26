package com.opentv.app.ui.shizuku

import android.app.Application
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import com.opentv.app.OpenTvApp
import com.opentv.app.display.GuideStep
import com.opentv.app.display.ShizukuGuide
import com.opentv.app.display.ShizukuShell
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Asistente de Shizuku: los pasos se marcan según el estado real, y se actualizan al volver a la pantalla. */
class ShizukuGuideViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as OpenTvApp).container

    private val _steps = MutableStateFlow(compute())
    val steps: StateFlow<List<GuideStep>> = _steps

    private val _status = MutableStateFlow(container.shizuku.status())
    val status: StateFlow<ShizukuShell.Status> = _status

    fun refresh() {
        _status.value = container.shizuku.status()
        _steps.value = compute()
    }

    fun requestPermission() {
        container.shizuku.requestPermission { refresh() }
    }

    private fun compute(): List<GuideStep> {
        val status = container.shizuku.status()
        val devOn = runCatching {
            Settings.Global.getInt(getApplication<Application>().contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
        }.getOrDefault(false)
        return ShizukuGuide.steps(
            installed = status != ShizukuShell.Status.NOT_INSTALLED,
            running = status == ShizukuShell.Status.NEEDS_PERMISSION || status == ShizukuShell.Status.READY,
            granted = status == ShizukuShell.Status.READY,
            developerOptionsOn = devOn,
            sdkInt = android.os.Build.VERSION.SDK_INT,
        )
    }
}
