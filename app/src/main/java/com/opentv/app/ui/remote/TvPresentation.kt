package com.opentv.app.ui.remote

import android.app.Presentation
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Ventana con interfaz Compose que se dibuja en la pantalla virtual de OpenTV. Una ventana que no
 * es de la Activity necesita que se le indiquen manualmente los «dueños» de ciclo de vida y estado.
 */
class TvPresentation(
    private val activity: ComponentActivity,
    display: Display,
    private val content: @Composable () -> Unit,
) : Presentation(activity, display) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val decor = window?.decorView ?: return
        decor.setViewTreeLifecycleOwner(activity)
        decor.setViewTreeViewModelStoreOwner(activity)
        decor.setViewTreeSavedStateRegistryOwner(activity)
        setContentView(ComposeView(context).apply { setContent(content) })
    }
}
