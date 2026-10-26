package com.opentv.app.display

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Teléfono simulado: responde a `wm size` / `wm density` como Android. */
private class FakePhone(
    val physW: Int = 720,
    val physH: Int = 1640,
    val physDensity: Int = 320,
    var overrideSize: String? = null,
    var overrideDensity: Int? = null,
    var failSizeChange: Boolean = false,
    val settings: MutableMap<String, String> = mutableMapOf("accelerometer_rotation" to "1"),
    var failSettings: Boolean = false,
) : ShellRunner {
    val commands = mutableListOf<String>()

    override suspend fun exec(command: String): String {
        commands += command
        require(ScreenResizer.isAllowedCommand(command)) { "comando no permitido: $command" }
        val parts = command.split(" ")
        if (parts[0] == "settings") {
            if (failSettings) throw IllegalStateException("settings no disponible")
            return when (parts[1]) {
                "get" -> settings[parts[3]] ?: "null"
                "put" -> { settings[parts[3]] = parts[4]; "" }
                else -> { settings.remove(parts[3]); "" }
            }
        }
        return when {
            command == "wm size" -> "Physical size: ${physW}x$physH\n" + (overrideSize?.let { "Override size: $it\n" } ?: "")
            command == "wm density" -> "Physical density: $physDensity\n" + (overrideDensity?.let { "Override density: $it\n" } ?: "")
            command == "wm size reset" -> { overrideSize = null; "" }
            parts[1] == "size" -> { if (!failSizeChange) overrideSize = parts[2]; "" }
            command == "wm density reset" -> { overrideDensity = null; "" }
            else -> { overrideDensity = parts[2].toInt(); "" }
        }
    }
}

private class MemoryStore : ResizeStore {
    override var pending: PendingResize? = null
}

class ScreenResizerTest {

    @Test
    fun parsesPhysicalAndOverride() {
        val s = WmParse.size("Physical size: 720x1640\nOverride size: 1080x1920\n")!!
        assertEquals(720, s.width); assertEquals(1640, s.height); assertEquals("1080x1920", s.override)
        assertNull(WmParse.size("Physical size: 720x1640")!!.override)
        assertNull(WmParse.size("basura"))
        val d = WmParse.density("Physical density: 320\nOverride density: 480")!!
        assertEquals(320, d.physical); assertEquals(480, d.override)
    }

    @Test
    fun planIsSixteenByNineAndKeepsWidthInDp() {
        val p = ResizePlan.sixteenByNine(720, 320)
        assertEquals("1080x1920", p.sizeArg)
        assertEquals(480, p.density) // 720/320 y 1080/480 dan el mismo ancho en dp
        assertEquals(16 * p.width, 9 * p.height)
        val big = ResizePlan.sixteenByNine(1440, 560)
        assertEquals(1440, big.width); assertEquals(2560, big.height); assertEquals(560, big.density)
    }

    @Test
    fun applyThenRestoreLeavesThePhoneAsItWas() = runBlocking {
        val phone = FakePhone()
        val store = MemoryStore()
        val resizer = ScreenResizer(phone, store)

        val r = resizer.apply()
        assertTrue(r.toString(), r is ResizeResult.Applied)
        assertEquals("1080x1920", phone.overrideSize)
        assertNull(phone.overrideDensity) // por defecto no se toca la densidad
        assertFalse(phone.commands.any { it.startsWith("wm density ") && it != "wm density" })
        assertTrue(resizer.pending.value)
        assertEquals(PendingResize(null, null, "1080x1920", 320, densityChanged = false), store.pending)

        assertEquals(ResizeResult.Restored, resizer.restore())
        assertNull(phone.overrideSize); assertNull(phone.overrideDensity)
        assertFalse(resizer.pending.value)
        assertNull(store.pending)
    }

    @Test
    fun withDensityChangeBothAreAppliedAndRestored() = runBlocking {
        val phone = FakePhone()
        val resizer = ScreenResizer(phone, MemoryStore())
        val r = resizer.apply(changeDensity = true)
        assertTrue(r is ResizeResult.Applied && r.densityChanged)
        assertEquals("1080x1920", phone.overrideSize)
        assertEquals(480, phone.overrideDensity)
        assertEquals(ResizeResult.Restored, resizer.restore())
        assertNull(phone.overrideSize)
        assertNull(phone.overrideDensity)
    }

    @Test
    fun sizeOnlyRestoreNeverTouchesTheUsersDensity() = runBlocking {
        val phone = FakePhone(overrideDensity = 300)
        val resizer = ScreenResizer(phone, MemoryStore())
        resizer.apply()
        resizer.restore()
        assertEquals(300, phone.overrideDensity)
        assertFalse(phone.commands.any { it == "wm density reset" || it == "wm density 300" })
    }

    @Test
    fun pendingFromOlderVersionsRestoresDensityToo() = runBlocking {
        val phone = FakePhone(overrideSize = "1080x1920", overrideDensity = 480)
        val store = MemoryStore().apply { pending = PendingResize(null, null, "1080x1920", 480) } // densityChanged por defecto
        assertEquals(ResizeResult.Restored, ScreenResizer(phone, store).restore())
        assertNull(phone.overrideDensity)
    }

    @Test
    fun restoresThePreviousCustomOverrideInsteadOfResetting() = runBlocking {
        val phone = FakePhone(overrideSize = "720x1560", overrideDensity = 300)
        val resizer = ScreenResizer(phone, MemoryStore())
        resizer.apply(changeDensity = true)
        resizer.restore()
        assertEquals("720x1560", phone.overrideSize)
        assertEquals(300, phone.overrideDensity)
    }

    @Test
    fun secondApplyDoesNotOverwriteWhatToRestore() = runBlocking {
        val phone = FakePhone()
        val store = MemoryStore()
        val resizer = ScreenResizer(phone, store)
        resizer.apply()
        assertEquals(ResizeResult.AlreadyApplied, resizer.apply())
        assertNull(store.pending!!.prevSize) // sigue siendo el estado original, no el forzado
    }

    @Test
    fun pendingSurvivesAnAppRestart() = runBlocking {
        val phone = FakePhone()
        val store = MemoryStore()
        ScreenResizer(phone, store).apply()
        val afterCrash = ScreenResizer(phone, store) // proceso nuevo, mismo disco
        assertTrue(afterCrash.pending.value)
        assertEquals(ResizeResult.Restored, afterCrash.restore())
        assertNull(phone.overrideSize)
    }

    @Test
    fun refusedChangeIsRolledBack() = runBlocking {
        val phone = FakePhone(failSizeChange = true)
        val store = MemoryStore()
        val r = ScreenResizer(phone, store).apply()
        assertTrue(r.toString(), r is ResizeResult.Failed)
        assertNull(store.pending)
        assertNull(phone.overrideSize)
    }

    @Test
    fun restoreWithNothingPendingDoesNothing() = runBlocking {
        val phone = FakePhone()
        assertEquals(ResizeResult.NothingToRestore, ScreenResizer(phone, MemoryStore()).restore())
        assertTrue(phone.commands.isEmpty())
    }

    @Test
    fun onlyWmSizeAndDensityCommandsAreAllowed() {
        listOf("wm size", "wm size reset", "wm size 1080x1920", "wm density", "wm density reset", "wm density 480").forEach {
            assertTrue(it, ScreenResizer.isAllowedCommand(it))
        }
        listOf("rm -rf /", "wm size 1080x1920; reboot", "wm overscan 1,1,1,1", "sh -c ls", "wm size  1080x1920", "").forEach {
            assertFalse(it, ScreenResizer.isAllowedCommand(it))
        }
    }

    @Test
    fun rotationIsLockedToLandscapeAndRestored() = runBlocking {
        val phone = FakePhone()
        val resizer = ScreenResizer(phone, MemoryStore())
        resizer.apply(lockLandscape = true)
        assertEquals("0", phone.settings["accelerometer_rotation"])
        assertEquals("1", phone.settings["user_rotation"])
        resizer.restore()
        assertEquals("1", phone.settings["accelerometer_rotation"]) // valor previo
        assertEquals("0", phone.settings["user_rotation"]) // no existía: vuelve a vertical (0), no se borra
    }

    @Test
    fun aRotationFailureDoesNotBreakTheResolutionChange() = runBlocking {
        val phone = FakePhone(failSettings = true)
        val resizer = ScreenResizer(phone, MemoryStore())
        assertTrue(resizer.apply(lockLandscape = true) is ResizeResult.Applied)
        assertEquals("1080x1920", phone.overrideSize)
        assertEquals(ResizeResult.Restored, resizer.restore())
        assertNull(phone.overrideSize)
    }

    @Test
    fun rotationIsLeftAloneByDefault() = runBlocking {
        val phone = FakePhone()
        ScreenResizer(phone, MemoryStore()).apply()
        assertEquals("1", phone.settings["accelerometer_rotation"])
        assertTrue(phone.commands.none { it.startsWith("settings") })
    }

    @Test
    fun onlyRotationSettingsAreAllowed() {
        listOf("settings get system user_rotation", "settings put system accelerometer_rotation 0", "settings delete system user_rotation").forEach {
            assertTrue(it, ScreenResizer.isAllowedCommand(it))
        }
        listOf("settings put global adb_enabled 1", "settings put system user_rotation 1; reboot", "settings put secure x 1").forEach {
            assertFalse(it, ScreenResizer.isAllowedCommand(it))
        }
    }
}
