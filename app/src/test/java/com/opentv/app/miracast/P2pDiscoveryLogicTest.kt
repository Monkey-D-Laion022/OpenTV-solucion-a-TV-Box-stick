package com.opentv.app.miracast

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class P2pDiscoveryLogicTest {
    private fun peer(name: String, status: Int = 3) = P2pPeer(name, "7-0050F204-1", null, status, false, false, true)

    @Test
    fun locationFreePathStartsAtApi33() {
        assertFalse(P2pPolicy.canRun(32))
        assertTrue(P2pPolicy.canRun(33))
        assertTrue(P2pPolicy.canRun(34))
    }

    @Test
    fun statusValuesMatchWifiP2pDevice() {
        assertEquals("conectado (0)", P2pText.status(0))
        assertEquals("disponible (3)", P2pText.status(3))
        assertEquals("no disponible (4)", P2pText.status(4))
        assertEquals("valor desconocido (9)", P2pText.status(9))
    }

    @Test
    fun lgDetectionIsNameBasedAndConservative() {
        assertTrue(P2pText.possibleLg("[LG] webOS TV LB5800"))
        assertTrue(P2pText.possibleLg("LG 42LB5800"))
        assertTrue(P2pText.possibleLg("LGTV"))
        assertFalse(P2pText.possibleLg("Samsung TV"))
        assertFalse(P2pText.possibleLg("Galaxy"))
        assertFalse(P2pText.possibleLg("flag-device"))
    }

    @Test
    fun peerLinesNeverInventData() {
        val lines = P2pText.peerLines(P2pPeer("TV", null, "", 3, false, false, false))
        assertTrue(lines.any { it == "Tipo declarado (primario): ${P2pText.NA}" })
        assertTrue(lines.any { it == "Tipo declarado (secundario): ${P2pText.NA}" })
        assertTrue(lines.last().contains("ya no aparece"))
    }

    @Test
    fun emptyResultIsInconclusiveAndNeverAbout60Fps() {
        val text = P2pText.conclusion(P2pUi(phase = P2pPhase.DONE))
        assertTrue(text.contains("INCONCLUSO"))
        assertTrue(text.contains("No prueba"))
        assertFalse(text.contains("no admite 1080p60"))
    }

    @Test
    fun foundLgStillDoesNotClaimConnectionOrFormats() {
        val text = P2pText.conclusion(P2pUi(phase = P2pPhase.DONE, peers = listOf(peer("[LG] TV"))))
        assertTrue(text.contains("solo muestra que una app normal puede VER"))
        assertTrue(text.contains("NO comprueba"))
    }

    @Test
    fun nonLgPeersAreInconclusiveForTheTv() {
        val text = P2pText.conclusion(P2pUi(phase = P2pPhase.DONE, peers = listOf(peer("Galaxy A16"))))
        assertTrue(text.contains("INCONCLUSO"))
    }

    @Test
    fun failureTextsCarryTheNumericCode() {
        val text = P2pText.conclusion(P2pUi(phase = P2pPhase.FAILED, failureCode = 2))
        assertTrue(text.contains("código 2"))
        assertTrue(text.contains("inconcluso"))
        assertTrue(P2pText.failure(77).contains("77"))
    }

    @Test
    fun shortIdShowsOnlyTheLastTwoBytes() {
        assertEquals("…3F:A1", P2pText.shortId("aa:bb:cc:dd:3f:a1"))
        assertEquals(P2pText.NA, P2pText.shortId(null))
        assertEquals(P2pText.NA, P2pText.shortId("zz"))
    }

    @Test
    fun oldAndroidIsReportedAsNotRun() {
        assertTrue(P2pText.conclusion(P2pUi(phase = P2pPhase.BLOCKED_OLD_ANDROID)).contains("No ejecutada"))
    }

    // ---- Guardas estáticas del alcance de la etapa A ----

    private fun strip(text: String): String =
        text.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .lines().joinToString("\n") { line ->
                val i = line.indexOf("//")
                if (i >= 0 && !line.substring(0, i).contains('"')) line.substring(0, i) else line
            }

    private fun appDir(): File = listOf(File("."), File("app")).first { File(it, "src/main/AndroidManifest.xml").isFile }

    private val stageAFiles = setOf("P2pDiscovery.kt", "P2pDiscoveryLogic.kt", "P2pDiscoveryViewModel.kt", "P2pDiscoveryScreen.kt")

    private fun productionFiles() =
        File(appDir(), "src/main/java/com/opentv/app/miracast").walkTopDown()
            .filter { it.isFile && it.extension == "kt" && !it.name.endsWith("Test.kt") }.toList()

    @Test
    fun stageADiscoveryNeverConnectsOrOpensSockets() {
        val forbidden = Regex("""\.connect\(|createGroup|removeGroup|cancelConnect|ServerSocket|java\.net\.Socket|Socket\(|7236""")
        val offenders = productionFiles().filter { it.name in stageAFiles && forbidden.containsMatchIn(strip(it.readText())) }.map { it.name }
        assertTrue("La etapa A (solo descubrimiento) no puede contener: $offenders", offenders.isEmpty())
    }

    @Test
    fun noMiracastFileUsesHiddenApisReflectionOrProtectedCode() {
        val forbidden = Regex(
            """setWfdInfo|getWfdInfo|wfdInfo|setMiracastMode|Class\.forName|getDeclaredMethod|getMethod\(|getDeclaredField|\bresizer\b|ScreenResizer|""" +
                """Shizuku|ProcessBuilder|Runtime\.getRuntime|su -c|wm size|wm density|settings put""",
        )
        val offenders = productionFiles().filter { forbidden.containsMatchIn(strip(it.readText())) }.map { it.name }
        assertTrue("El paquete miracast/ no puede contener: $offenders", offenders.isEmpty())
    }

    @Test
    fun onlyTheSessionFileOpensSockets() {
        val sockets = Regex("""ServerSocket|java\.net\.Socket|Socket\(""")
        val offenders = productionFiles().filter { sockets.containsMatchIn(strip(it.readText())) }.map { it.name }
        assertEquals(listOf("WfdDiagnosticSession.kt").filter { it in offenders }, offenders)
    }

    @Test
    fun manifestHasNoLocationPermissionAndOnlyTheJustifiedWifiOnes() {
        val manifest = File(appDir(), "src/main/AndroidManifest.xml").readText()
        assertFalse("ubicación sin aprobación", manifest.contains("ACCESS_FINE_LOCATION") || manifest.contains("ACCESS_COARSE_LOCATION"))
        val perms = Regex("""uses-permission\s+android:name="android\.permission\.([A-Z_]+)"""").findAll(manifest).map { it.groupValues[1] }.toList()
        assertEquals("sin duplicados", perms.size, perms.toSet().size)
        assertTrue(perms.containsAll(listOf("ACCESS_WIFI_STATE", "CHANGE_WIFI_STATE", "NEARBY_WIFI_DEVICES")))
        assertTrue(manifest.contains("NEARBY_WIFI_DEVICES\" android:usesPermissionFlags=\"neverForLocation\"") ||
            Regex("""NEARBY_WIFI_DEVICES"\s+android:usesPermissionFlags="neverForLocation"""").containsMatchIn(manifest))
    }
}
