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

    private fun flow(timeoutMs: Long = 5_000) = NativeMirrorFlow(
        ScreenResizer(phone, NmStore()),
        CastOpener { castOpened++; castAvailable },
        { screens },
        scope,
        settleMs = 10,
        connectTimeoutMs = timeoutMs,
    )

    private fun await(what: String, cond: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 3_000
        while (!cond() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue(what, cond())
    }

    @Test
    fun resolutionIsForcedBeforeTheCastPanelOpensAndRestoredWhenTheScreenGoes() {
        val f = flow()
        f.start()
        await("espera al TV") { f.state.value is NativeMirrorState.WaitingForTv }
        assertEquals("1080x1920", phone.overrideSize)
        assertEquals(480, phone.overrideDensity)
        assertEquals(1, castOpened)

        screens.value = true
        await("espejando") { f.state.value is NativeMirrorState.Mirroring }
        screens.value = false
        await("restaurado") { f.state.value is NativeMirrorState.Idle }
        assertNull(phone.overrideSize)
        assertNull(phone.overrideDensity)
        assertTrue((f.state.value as NativeMirrorState.Idle).note!!.contains("restaurada"))
    }

    @Test
    fun ifTheResolutionCannotBeForcedTheCastPanelIsNotOpened() {
        phone.broken = true
        val f = flow()
        f.start()
        await("falló") { f.state.value is NativeMirrorState.Failed }
        assertEquals(0, castOpened)
        assertTrue((f.state.value as NativeMirrorState.Failed).message.contains("no se pudo forzar 16:9"))
    }

    @Test
    fun phoneWithoutCastScreenIsRestored() {
        castAvailable = false
        val f = flow()
        f.start()
        await("falló") { f.state.value is NativeMirrorState.Failed }
        assertNull(phone.overrideSize)
        assertTrue((f.state.value as NativeMirrorState.Failed).message.contains("no tiene pantalla de transmisión"))
    }

    @Test
    fun noTvConnectingInTimeRestoresTheResolution() {
        val f = flow(timeoutMs = 150)
        f.start()
        await("restaurado por tiempo") { f.state.value is NativeMirrorState.Idle }
        assertNull(phone.overrideSize)
        assertTrue((f.state.value as NativeMirrorState.Idle).note!!.contains("a tiempo"))
    }

    @Test
    fun stopWhileWaitingRestores() {
        val f = flow()
        f.start()
        await("espera al TV") { f.state.value is NativeMirrorState.WaitingForTv }
        f.stop()
        await("detenido") { f.state.value is NativeMirrorState.Idle }
        assertNull(phone.overrideSize)
        assertFalse(f.state.value is NativeMirrorState.Failed)
    }

    @Test
    fun startingTwiceWhileBusyDoesNothing() {
        val f = flow()
        f.start()
        await("espera al TV") { f.state.value is NativeMirrorState.WaitingForTv }
        f.start()
        Thread.sleep(100)
        assertEquals(1, castOpened)
    }
}
