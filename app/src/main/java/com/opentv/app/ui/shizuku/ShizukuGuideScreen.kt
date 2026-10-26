package com.opentv.app.ui.shizuku

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.opentv.app.display.GuideAction
import com.opentv.app.display.ShizukuGuide
import com.opentv.app.ui.components.SafeArea
import com.opentv.app.ui.components.TvButton
import com.opentv.app.ui.theme.OpenTvColors

private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

/** Asistente paso a paso para activar Shizuku (opciones de desarrollador, depuración inalámbrica, permiso). */
@Composable
fun ShizukuGuideScreen(
    onBack: () -> Unit,
    viewModel: ShizukuGuideViewModel = viewModel(),
) {
    val steps by viewModel.steps.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    SafeArea {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Activar Shizuku, paso a paso", style = MaterialTheme.typography.headlineMedium, color = OpenTvColors.OnBackground)
            Text(
                "Shizuku solo hace falta para el 16:9 manual. Cada paso se tilda solo cuando Android confirma que está hecho.",
                style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.Muted,
            )
            Text(status.message, style = MaterialTheme.typography.bodyLarge, color = OpenTvColors.OnBackground)
            steps.forEach { step ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = (if (step.done) "✔ " else "") + "${step.number}. ${step.title}",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (step.done) OpenTvColors.Success else OpenTvColors.OnBackground,
                    )
                    if (!step.done) {
                        Text(step.body, style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.Muted)
                        step.action?.let { action ->
                            TvButton(
                                text = action.label,
                                onClick = { run(context, action, viewModel) },
                                horizontalPadding = 20.dp,
                            )
                        }
                    }
                }
            }
            Text(ShizukuGuide.LIMITS, style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.Warning)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvButton(text = "Volver", onClick = onBack, horizontalPadding = 20.dp, modifier = Modifier.focusRequester(firstFocus))
            }
        }
    }
}

private fun run(context: Context, action: GuideAction, viewModel: ShizukuGuideViewModel) {
    when (action) {
        GuideAction.INSTALL_SHIZUKU -> {
            val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$SHIZUKU_PACKAGE"))
            val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/RikkaApps/Shizuku/releases"))
            launch(context, market) || launch(context, web)
        }
        GuideAction.OPEN_DEVELOPER_OPTIONS ->
            launch(context, Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) ||
                launch(context, Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)) ||
                launch(context, Intent(Settings.ACTION_SETTINGS))
        GuideAction.OPEN_SHIZUKU -> {
            val intent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
            if (intent != null) launch(context, intent)
        }
        GuideAction.REQUEST_PERMISSION -> viewModel.requestPermission()
    }
}

private fun launch(context: Context, intent: Intent): Boolean = try {
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: ActivityNotFoundException) {
    false
} catch (e: SecurityException) {
    false
}
