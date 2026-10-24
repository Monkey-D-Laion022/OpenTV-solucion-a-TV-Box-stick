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
) : ShellRunner {
    val commands = mutableListOf<String>()

    override suspend fun exec(command: String): String {
        commands += command
        require(ScreenResizer.isAllowedCommand(command)) { "comando no permitido: $command" }
        val parts = command.split(" ")
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
        assertEquals(480, phone.overrideDensity)
        assertTrue(resizer.pending.value)
        assertEquals(PendingResize(null, null, "1080x1920", 480), store.pending)

        assertEquals(ResizeResult.Restored, resizer.restore())
        assertNull(phone.overrideSize); assertNull(phone.overrideDensity)
        assertFalse(resizer.pending.value)
        assertNull(store.pending)
    }

    @Test
    fun restoresThePreviousCustomOverrideInsteadOfResetting() = runBlocking {
        val phone = FakePhone(overrideSize = "720x1560", overrideDensity = 300)
        val resizer = ScreenResizer(phone, MemoryStore())
        resizer.apply()
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
}
