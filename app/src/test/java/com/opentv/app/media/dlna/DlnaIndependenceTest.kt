package com.opentv.app.media.dlna

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** La capa DLNA nueva no puede depender de `connect/` (DLNA antiguo) ni de las tecnologías protegidas. */
class DlnaIndependenceTest {
    @Test fun noImportaCodigoProtegidoNiElDlnaAntiguo() {
        val dir = listOf("main/java/com/opentv/app/media/dlna", "src/main/java/com/opentv/app/media/dlna", "app/src/main/java/com/opentv/app/media/dlna").map(::File).firstOrNull { it.isDirectory }
        if (dir == null) return // la ruta depende del directorio de trabajo de la prueba
        val files: List<File> = dir.listFiles { f -> f.name.endsWith(".kt") }?.toList().orEmpty()
        assertTrue("no se encontraron fuentes", files.isNotEmpty())
        val forbidden = listOf("com.opentv.app.connect", "com.opentv.app.display", "com.opentv.app.stream.TvProjection", "com.opentv.app.ui.shizuku", "com.opentv.app.ui.mirror")
        for (f in files) {
            val text: String = f.readText()
            for (bad in forbidden) assertTrue("${f.name} importa $bad", !text.contains("import $bad"))
        }
    }
}
