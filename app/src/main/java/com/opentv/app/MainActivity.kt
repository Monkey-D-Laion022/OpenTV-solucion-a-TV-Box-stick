package com.opentv.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.opentv.app.navigation.OpenTvNavHost
import com.opentv.app.ui.theme.OpenTvTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class MainActivity : ComponentActivity() {

    /** Sube cada vez que llega el botón Inicio con OpenTV como launcher (ver HomeAlias). */
    private val homeRequests = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Dibujar detrás de las barras del sistema y de la muesca: sin franjas negras propias.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        // Un centro multimedia conectado a un TV no debe apagar la pantalla mientras se usa.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Con launchMode singleTask, el botón Inicio llega como un intent nuevo a esta Activity.
        addOnNewIntentListener { intent ->
            if (intent.hasCategory(Intent.CATEGORY_HOME)) homeRequests.update { it + 1 }
        }

        setContent {
            OpenTvTheme {
                OpenTvNavHost(homeRequests = homeRequests)
            }
        }
    }

    override fun onDestroy() {
        // Al cerrar la app de verdad se corta la proyección: no queda ninguna ventana ni servidor vivos.
        if (isFinishing) (application as OpenTvApp).container.shutdownProjection()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /** Escape (teclado o mando) equivale a Atrás. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}
