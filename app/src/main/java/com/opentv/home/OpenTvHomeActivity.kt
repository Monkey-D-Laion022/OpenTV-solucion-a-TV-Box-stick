package com.opentv.home

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

class OpenTvHomeActivity : ComponentActivity() {

    private lateinit var state: HomeState
    private lateinit var voice: VoiceController

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) voice.start() else state.status = "Sin permiso de micrófono no puedo escucharte"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        state = HomeState(applicationContext, ProfileStore(applicationContext))
        voice = VoiceController(
            context = this,
            onListening = { state.listening = it },
            onPartial = { state.status = "… $it" },
            onText = { state.handleVoice(it) },
            onFailure = { state.status = it },
        )

        setContent {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()

            LaunchedEffect(Unit) { state.reload() }

            // Refresca la lista cuando se instala, actualiza o desinstala una app
            DisposableEffect(Unit) {
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(c: Context?, i: Intent?) {
                        scope.launch { state.reload() }
                    }
                }
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_PACKAGE_ADDED)
                    addAction(Intent.ACTION_PACKAGE_REMOVED)
                    addAction(Intent.ACTION_PACKAGE_REPLACED)
                    addDataScheme("package")
                }
                ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
                onDispose { context.unregisterReceiver(receiver) }
            }

            // Atrás desde otra pestaña vuelve a Inicio; en Inicio no hace nada (es el launcher)
            BackHandler(enabled = true) {
                if (state.tab != HomeTab.Home) state.tab = HomeTab.Home
            }

            OpenTvHome(state = state, onVoice = { startVoice() })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Botón Inicio estando ya en el launcher: volver a la pestaña Inicio
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            state.tab = HomeTab.Home
            state.menuApp = null
            state.quickPanelOpen = false
            state.profileDialogOpen = false
        }
    }

    override fun onResume() {
        super.onResume()
        // Puede haber concedido el acceso a notificaciones en Ajustes
        state.refreshAccess()
    }

    override fun onStop() {
        voice.stop()
        super.onStop()
    }

    override fun onDestroy() {
        voice.stop()
        super.onDestroy()
    }

    private fun startVoice() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) voice.start() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }
}
