package com.opentv.app.ui.shell

import com.opentv.app.navigation.Routes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellLogicTest {
    @Test
    fun barHasTheFiveAgreedSectionsInOrder() {
        assertEquals(listOf("Inicio", "Aplicaciones", "Multimedia", "Conexiones", "Ajustes"), TopSection.values().map { it.label })
        assertEquals(TopSection.values().size, TopSection.values().map { it.route }.toSet().size)
    }

    @Test
    fun routesMapBackToSections() {
        TopSection.values().forEach { assertEquals(it, TopSection.forRoute(it.route)) }
        assertNull(TopSection.forRoute(Routes.PLAYER))
        assertNull(TopSection.forRoute(null))
    }

    private val all = NavCatalog.mirroring + NavCatalog.playback + NavCatalog.connectionDiagnostics + NavCatalog.diagnostics + NavCatalog.explore

    @Test
    fun everyEntryHasExactlyOneDestination() {
        all.forEach { assertTrue(it.id, (it.route != null) != (it.action != null)) }
    }

    @Test
    fun idsAreUniqueWithinEachList() {
        listOf(NavCatalog.mirroring, NavCatalog.playback, NavCatalog.diagnostics, NavCatalog.explore, NavCatalog.homeTransmit).forEach { l ->
            assertEquals(l.size, l.map { it.id }.toSet().size)
        }
    }

    @Test
    fun theThreeMovedCardsKeepTheirOriginalDestinations() {
        val byId = NavCatalog.diagnostics.associateBy { it.id }
        assertEquals(Routes.BENCH, byId.getValue("bench").route)
        assertEquals(Routes.P2P_DISCOVERY, byId.getValue("p2p").route)
        assertEquals(Routes.SHIZUKU_GUIDE, byId.getValue("shizuku").route)
        assertEquals(Routes.DEVICE, byId.getValue("device").route)
        assertEquals(Routes.WFD_DIAGNOSTIC, byId.getValue("wfd").route)
    }

    @Test
    fun homeTransmitKeepsTheFiveOriginalCardsInTheOriginalOrder() {
        assertEquals(listOf("hub", "mirror", "browser", "own", "native"), NavCatalog.homeTransmit.map { it.id })
        assertEquals(Routes.CONNECT, NavCatalog.homeTransmit[0].route)
        assertEquals(Routes.MIRROR, NavCatalog.homeTransmit[1].route)
        assertEquals(Routes.BROWSER, NavCatalog.homeTransmit[2].route)
        assertEquals(Routes.REMOTE, NavCatalog.homeTransmit[3].route)
        assertEquals(EntryAction.NATIVE_CAST, NavCatalog.homeTransmit[4].action)
    }

    @Test
    fun noPreExistingRouteIsLostFromTheNavigationCatalog() {
        val reachable = all.mapNotNull { it.route }.toSet() + setOf(Routes.TOOLS, Routes.PLAYER) // estos dos se abren desde Multimedia
        assertTrue("faltan: ${NavCatalog.everyPreservedRoute - reachable}", reachable.containsAll(NavCatalog.everyPreservedRoute))
    }

    @Test
    fun settingsCategoriesMatchTheRequestedList() {
        assertEquals(
            listOf("Red e Internet", "Pantalla", "Sonido", "Aplicaciones", "Sistema e información", "Diagnósticos"),
            SettingsCategory.values().map { it.title },
        )
        assertEquals(SettingsCategory.NETWORK, SettingsCategory.fromId("zzz"))
        assertEquals(SettingsCategory.DIAGNOSTICS, SettingsCategory.fromId("diagnostics"))
    }

    @Test
    fun signalLevelFollowsAndroidsFormula() {
        assertEquals(0, NetworkText.signalLevel(-100))
        assertEquals(0, NetworkText.signalLevel(-120))
        assertEquals(4, NetworkText.signalLevel(-55))
        assertEquals(4, NetworkText.signalLevel(-30))
        assertEquals(1, NetworkText.signalLevel(-78)) // (−78+100)×4/45 = 1 con división entera
        assertEquals(2, NetworkText.signalLevel(-70)) // (30×4)/45 = 2
    }

    @Test
    fun networkRowsNeverInventData() {
        val unknown = NetworkText.rows(NetworkSnapshot(false, null, null, null, null, null, null)).toMap()
        assertEquals("Sin conexión", unknown["Estado"])
        assertEquals(NetworkText.NA, unknown["Dirección IP"])
        assertEquals(NetworkText.NA, unknown["Intensidad de señal"])
        assertEquals(NetworkText.NA, unknown["Velocidad del enlace"])
        val full = NetworkText.rows(NetworkSnapshot(true, "Wi-Fi", true, "192.168.1.45", true, -62, 433)).toMap()
        assertEquals("Conectado", full["Estado"])
        assertEquals("192.168.1.45", full["Dirección IP"])
        assertEquals("3 de 4 (-62 dBm)", full["Intensidad de señal"])
        assertEquals("433 Mbps", full["Velocidad del enlace"])
        assertFalse(full.keys.any { it.contains("Nombre") })
    }
}
