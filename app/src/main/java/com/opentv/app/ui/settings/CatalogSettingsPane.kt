package com.opentv.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.opentv.app.catalog.CatalogText
import com.opentv.app.catalog.CatalogViewModel
import com.opentv.app.catalog.TestResult
import com.opentv.app.ui.components.InfoRow
import com.opentv.app.ui.components.TvButton
import com.opentv.app.ui.theme.OpenTvColors
import java.text.DateFormat
import java.util.Date

/**
 * Ajustes › Catálogo: claves de TMDB y RPDB. Las claves se escriben acá, se guardan cifradas en el almacenamiento privado
 * de OpenTV y nunca se muestran de nuevo (ni en pantalla, ni en registros, ni en el repositorio). Los campos viven solo en
 * memoria: no usan `rememberSaveable` para que la clave no quede en el estado guardado de la pantalla.
 */
@Composable
fun CatalogSettingsPane(viewModel: CatalogViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tmdbInput by remember { mutableStateOf("") }
    var rpdbInput by remember { mutableStateOf("") }
    var tmdbMessage by remember { mutableStateOf<String?>(null) }
    var rpdbMessage by remember { mutableStateOf<String?>(null) }
    var reveal by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var cacheBytes by remember { mutableStateOf<Long?>(null) }
    var cacheNote by remember { mutableStateOf<String?>(null) }

    // Refleja el estado real guardado (también al abrir la pantalla por primera vez).
    LaunchedEffect(Unit) {
        viewModel.refresh()
        cacheBytes = viewModel.imageCacheBytes()
    }

    InfoRow("TMDB", credentialStatus(state.tmdbConfigured, state.tmdbTest))
    SecretField(
        value = tmdbInput,
        onValueChange = { tmdbInput = it; tmdbMessage = null },
        placeholder = if (state.tmdbConfigured) "Pegá una clave nueva para reemplazar la guardada" else "Clave v3 o token v4 de TMDB",
        reveal = reveal,
    )
    Row2 {
        TvButton(
            text = "Guardar clave de TMDB",
            onClick = {
                viewModel.saveTmdb(tmdbInput) { error ->
                    tmdbMessage = error ?: "Clave de TMDB guardada."
                    if (error == null) tmdbInput = ""
                }
            },
            horizontalPadding = 20.dp,
        )
        TvButton(text = "Probar conexión con TMDB", onClick = { viewModel.testTmdb() }, horizontalPadding = 20.dp)
    }
    tmdbMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.Muted) }

    InfoRow("RPDB", credentialStatus(state.rpdbConfigured, state.rpdbTest))
    SecretField(
        value = rpdbInput,
        onValueChange = { rpdbInput = it; rpdbMessage = null },
        placeholder = if (state.rpdbConfigured) "Pegá una clave nueva para reemplazar la guardada" else "Clave de RPDB (opcional)",
        reveal = reveal,
    )
    Row2 {
        TvButton(
            text = "Guardar clave de RPDB",
            onClick = {
                viewModel.saveRpdb(rpdbInput) { error ->
                    rpdbMessage = error ?: "Clave de RPDB guardada."
                    if (error == null) rpdbInput = ""
                }
            },
            horizontalPadding = 20.dp,
        )
        TvButton(text = "Probar conexión con RPDB", onClick = { viewModel.testRpdb() }, horizontalPadding = 20.dp)
    }
    rpdbMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.Muted) }
    state.rpdbNote?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.Warning) }
    if (state.testing) Text("Probando…", style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.Muted)

    Row2 {
        TvButton(text = if (reveal) "Ocultar lo que escribo" else "Mostrar lo que escribo", onClick = { reveal = !reveal }, horizontalPadding = 20.dp)
    }

    if (state.storageNote.isNotEmpty()) {
        Text("Almacenamiento: ${state.storageNote}", style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.Muted)
    }
    Text(
        "Las claves se guardan cifradas con el almacén de claves de Android y no salen de este teléfono, pero eso no las hace " +
            "imposibles de extraer: alguien con acceso root al teléfono podría obtenerlas mientras OpenTV las usa. Usá claves que puedas revocar.",
        style = MaterialTheme.typography.bodyMedium,
        color = OpenTvColors.Muted,
    )

    InfoRow(
        "Catálogo",
        when {
            !state.tmdbConfigured -> "Sin configurar"
            state.loading -> "Actualizando…"
            state.problem != null -> CatalogText.problem(state.problem!!, state.problemDetail)
            state.updatedAtMillis != null -> "Actualizado " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(state.updatedAtMillis!!))
            else -> "Sin datos todavía"
        },
    )
    Row2 {
        TvButton(text = "Actualizar catálogo ahora", onClick = { viewModel.refresh(force = true) }, horizontalPadding = 20.dp)
        TvButton(
            text = "Borrar caché de imágenes" + (cacheBytes?.let { " (${it / (1024 * 1024)} MB)" } ?: ""),
            onClick = {
                viewModel.clearImageCache {
                    cacheNote = "Caché de imágenes borrada."
                    cacheBytes = 0L
                }
            },
            horizontalPadding = 20.dp,
        )
    }
    cacheNote?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.Muted) }

    if (!confirmClear) {
        TvButton(text = "Borrar claves guardadas…", onClick = { confirmClear = true }, horizontalPadding = 20.dp)
    } else {
        Text(
            "Se borran las dos claves y todo lo descargado con ellas (catálogo e imágenes). Esto no se puede deshacer.",
            style = MaterialTheme.typography.bodyMedium,
            color = OpenTvColors.Warning,
        )
        Row2 {
            TvButton(text = "Cancelar", onClick = { confirmClear = false }, horizontalPadding = 20.dp)
            TvButton(
                text = "Sí, borrar claves",
                onClick = {
                    viewModel.clearKeys()
                    tmdbMessage = null
                    rpdbMessage = null
                    cacheBytes = 0L
                    confirmClear = false
                },
                horizontalPadding = 20.dp,
            )
        }
    }

    Text(CatalogText.ATTRIBUTION, style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.Muted)
    Text(CatalogText.RPDB_NOTE, style = MaterialTheme.typography.bodyMedium, color = OpenTvColors.Muted)
}

private fun credentialStatus(configured: Boolean, test: TestResult?): String {
    if (!configured) return "No configurada"
    if (test?.ok == null) return "Guardada · sin probar"
    val at = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(test.atMillis))
    return (if (test.ok == true) "Guardada · prueba correcta" else "Guardada · la prueba falló") + " ($at): ${test.message}"
}

/** Botones apilados: con el mando es más predecible que una fila que se desborda en paneles angostos. */
@Composable
private fun Row2(content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
private fun SecretField(value: String, onValueChange: (String) -> Unit, placeholder: String, reveal: Boolean) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(28.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(if (focused) OpenTvColors.SurfaceFocused else OpenTvColors.Surface, shape)
            .then(if (focused) Modifier.border(3.dp, OpenTvColors.Primary, shape) else Modifier)
            .onFocusChanged { focused = it.isFocused },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = OpenTvColors.OnBackground),
        cursorBrush = SolidColor(OpenTvColors.Primary),
        visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrect = false),
        decorationBox = { inner ->
            Box(modifier = Modifier.padding(horizontal = 20.dp), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = OpenTvColors.Muted, maxLines = 1)
                }
                inner()
            }
        },
    )
}
