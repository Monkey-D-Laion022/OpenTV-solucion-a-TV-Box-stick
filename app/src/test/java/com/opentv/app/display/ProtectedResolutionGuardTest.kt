package com.opentv.app.display

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guardia estática de la zona protegida (el 16:9 de H2): lee el código fuente y falla si algo que no esté en la
 * lista autorizada puede cambiar o restaurar la resolución, la densidad o la rotación del teléfono.
 *
 * No ejecuta nada en el teléfono: solo busca texto en los `.kt` de `src/main`. La lista de archivos permitidos es la
 * de H2 (más nada nuevo). Si este test falla, una función nueva está tocando la zona protegida: no se arregla
 * ampliando la lista, se revisa el cambio y se pide autorización.
 */
class ProtectedResolutionGuardTest {

    private fun sourceRoot(): File {
        val candidates = listOf("src/main/java", "app/src/main/java", System.getProperty("opentv.src") ?: "")
        return candidates.filter { it.isNotEmpty() }.map { File(it) }.firstOrNull { it.isDirectory }
            ?: error("No se encontró src/main/java (directorio de trabajo: ${File(".").absolutePath})")
    }

    private fun stripComments(text: String): String =
        text.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .lines().joinToString("\n") { line ->
                val i = line.indexOf("//")
                // No se recorta si el // parece estar dentro de un texto (por ejemplo una URL).
                if (i >= 0 && !line.substring(0, i).contains('"')) line.substring(0, i) else line
            }

    /** Ruta relativa → contenido sin comentarios. */
    private val sources: Map<String, String> by lazy {
        val root = sourceRoot()
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .associate { it.relativeTo(root).path.replace('\\', '/').substringAfter("com/opentv/app/") to stripComments(it.readText()) }
    }

    private fun filesMatching(pattern: Regex): Set<String> =
        sources.filter { pattern.containsMatchIn(it.value) }.keys

    private fun assertOnly(what: String, pattern: Regex, allowed: Set<String>) {
        val found = filesMatching(pattern)
        val extra = found - allowed
        assertTrue("$what aparece fuera de la lista autorizada: $extra", extra.isEmpty())
    }

    @Test
    fun sourcesAreFound() {
        assertTrue("el escaneo no encontró el código", sources.size > 50)
        assertTrue(sources.containsKey("display/ScreenResizer.kt"))
    }

    @Test
    fun onlyAuthorizedFilesApplyTheResolution() {
        assertOnly(
            "resizer.apply(",
            Regex("""resizer\.apply\("""),
            setOf("display/NativeMirrorFlow.kt", "stream/TvProjection.kt"),
        )
    }

    @Test
    fun onlyAuthorizedFilesRestoreTheResolution() {
        assertOnly(
            "resizer.restore(",
            Regex("""resizer\.restore\("""),
            setOf(
                "display/NativeMirrorFlow.kt", "stream/TvProjection.kt",
                "ui/remote/RemoteViewModel.kt", "ui/mirror/NativeMirrorViewModel.kt",
            ),
        )
    }

    @Test
    fun onlyTheMirrorScreenStartsTheNativeFlow() {
        assertOnly(
            "forceResolution() / nativeMirror.",
            Regex("""forceResolution\(|nativeMirror\."""),
            setOf("display/NativeMirrorFlow.kt", "ui/mirror/NativeMirrorViewModel.kt"),
        )
    }

    @Test
    fun screenCommandsLiveOnlyInTheProtectedFile() {
        assertOnly(
            "comandos wm / settings",
            Regex(""""wm (size|density)|"settings (put|delete) system|wm size \$|wm density \$"""),
            setOf("display/ScreenResizer.kt"),
        )
    }

    @Test
    fun densityIsNeverChangedByTheApp() {
        assertTrue(
            "changeDensity = true no se usa en el código de la app",
            filesMatching(Regex("""changeDensity\s*=\s*true""")).isEmpty(),
        )
    }

    @Test
    fun startupAndNetworkCodeNeverTouchTheResizer() {
        val mentions = Regex("""\bresizer\b|ScreenResizer|ResizeResult|nativeMirror""")
        val forbidden = sources.keys.filter { path ->
            path == "OpenTvApp.kt" || path == "MainActivity.kt" ||
                path.startsWith("connect/") || path.startsWith("ui/connect/") || path.startsWith("ui/browser/") ||
                path.startsWith("stream/Mjpeg") || path == "stream/BrowserMirror.kt" || path == "stream/HttpStreamServer.kt" ||
                path == "ui/home/HomeScreen.kt" || path.startsWith("bench/") || path.startsWith("miracast/") ||
                path.startsWith("ui/shell/") || path.startsWith("ui/connections/") || path.startsWith("ui/media/") ||
                path.startsWith("ui/settings/") || path.startsWith("ui/tv/") || path.startsWith("catalog/") || path.startsWith("iptv/") || path.startsWith("proto/") || path.startsWith("ui/iptv/")
        }
        val offenders = forbidden.filter { mentions.containsMatchIn(sources.getValue(it)) }
        assertTrue("Código de arranque, red o inicio menciona la zona protegida: $offenders", offenders.isEmpty())
    }
}
