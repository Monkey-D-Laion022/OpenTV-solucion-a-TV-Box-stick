package com.opentv.app.display

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class NmPhone(var broken: Boolean = false) : ShellRunner {
    var overrideSize: String? = null
    var overrideDensity: Int? = null
    override suspend fun exec(command: String): String {
        if (broken) throw IllegalStateException("Shizuku no responde")
        val p = command.split(" ")
        return when {
            command == "wm size" -> "Physical size: 720x1640\n" + (overrideSize?.let { "Override size: $it\n" } ?: "")
            command == "wm density" -> "Physical density: 320\n" + (overrideDensity?.let { "Override density: $it\n" } ?: "")
            command == "wm size reset" -> { overrideSize = null; "" }
            p[1] == "size" -> { overrideSize = p[2]; "" }
            command == "wm density reset" -> { overrideDensity = null; "" }
            else -> { overrideDensity = p[2].toInt(); "" }
        }
    }
}

private class NmStore : ResizeStore {
    override var pending: PendingResize? = null
}

class NativeMirrorFlowTest {
    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Default + job)
    private val phone = NmPhone()
    private val screens = MutableStateFlow(false)
    private var castOpened = 0
    private var castAvailable = true

    @After
    fun tearDown() = job.cancel()

    private fun flow(restoreOnDisconnect: Boolean = false) = NativeMirrorFlow(
        ScreenResizer(phone, NmStore()),
        CastOpener { castOpened++; castAvailable },
        { screens },
        scope,
        restoreOnDisconnect = restoreOnDisconnect,
    )

    private fun await(what: String, cond: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 3_000
        while (!cond() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue(what, cond())
    }

    private fun forced(f: NativeMirrorFlow) = f.state.value as NativeMirrorState.Forced

    @Test
    fun step1ForcesTheResolutionAndDoesNotOpenTheCastPanel() {
        val f = flow()
        f.forceResolution()
        await("forzada") { f.state.value is NativeMirrorState.Forced }
        assertEquals("1080x1920", phone.overrideSize)
        assertNull(phone.overrideDensity)
        Thread.sleep(200)
        assertEquals(0, castOpened) // el panel lo abre el usuario en el paso 2
    }

    @Test
    fun step2OpensThePanelOnlyWhenForced() {
        val f = flow()
        assertFalse(f.openPanel())
        assertEquals(0, castOpened)
        f.forceResolution()
        await("forzada") { f.state.value is NativeMirrorState.Forced }
        assertTrue(f.openPanel())
        assertEquals(1, castOpened)
        assertNull(forced(f).panelNote)
    }

    @Test
    fun aPanelThatCannotOpenKeepsTheResolutionAndExplainsWhatToDo() {
        castAvailable = false
        val f = flow()
        f.forceResolution()
        await("forzada") { f.state.value is NativeMirrorState.Forced }
        assertFalse(f.openPanel())
        assertEquals("1080x1920", phone.overrideSize) // no se restaura sola
        assertTrue(forced(f).panelNote!!.contains("panel rápido"))
    }

    @Test
    fun stayStaysForcedAcrossConnectAndDisconnect() {
        val f = flow()
        f.forceResolution()
        await("forzada") { f.state.value is NativeMirrorState.Forced }
        screens.value = true
        await("conectada") { (f.state.value as? NativeMirrorState.Forced)?.tvConnected == true }
        screens.value = false
        await("desconectada") { (f.state.value as? NativeMirrorState.Forced)?.tvConnected == false }
        Thread.sleep(300)
        assertEquals("1080x1920", phone.overrideSize)
    }

    @Test
    fun optionalRestoreOnDisconnect() {
        val f = flow(restoreOnDisconnect = true)
        f.forceResolution()
        await("forzada") { f.state.value is NativeMirrorState.Forced }
        screens.value = true
        await("conectada") { (f.state.value as? NativeMirrorState.Forced)?.tvConnected == true }
        screens.value = false
        await("restaurada") { f.state.value is NativeMirrorState.Idle }
        assertNull(phone.overrideSize)
    }

    @Test
    fun ifTheResolutionCannotBeForcedThereIsNothingToOpen() {
        phone.broken = true
        val f = flow()
        f.forceResolution()
        await("falló") { f.state.value is NativeMirrorState.Failed }
        assertFalse(f.openPanel())
        assertEquals(0, castOpened)
        assertTrue((f.state.value as NativeMirrorState.Failed).message.contains("No se pudo forzar 16:9"))
    }

    @Test
    fun stopRestores() {
        val f = flow()
        f.forceResolution()
        await("forzada") { f.state.value is NativeMirrorState.Forced }
        f.stop()
        await("detenida") { (f.state.value as? NativeMirrorState.Idle)?.note != null }
        assertNull(phone.overrideSize)
        assertFalse(f.state.value is NativeMirrorState.Failed)
    }

    @Test
    fun forcingTwiceWhileBusyDoesNothing() {
        val f = flow()
        f.forceResolution()
        f.forceResolution()
        await("forzada") { f.state.value is NativeMirrorState.Forced }
        assertEquals("1080x1920", phone.overrideSize)
    }
}
