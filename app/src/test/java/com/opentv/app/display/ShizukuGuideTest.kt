package com.opentv.app.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShizukuGuideTest {
    private enum class S { NOT_INSTALLED, NOT_RUNNING, NEEDS_PERMISSION, READY }

    private fun steps(s: S, dev: Boolean = false) = ShizukuGuide.steps(
        installed = s != S.NOT_INSTALLED,
        running = s == S.NEEDS_PERMISSION || s == S.READY,
        granted = s == S.READY,
        developerOptionsOn = dev,
    )

    @Test
    fun notInstalledOnlyOffersToInstall() {
        val s = steps(S.NOT_INSTALLED)
        assertEquals(5, s.size)
        assertEquals(GuideAction.INSTALL_SHIZUKU, s[0].action)
        assertTrue(s.none { it.done })
    }

    @Test
    fun installedButNotRunningMarksOnlyStep1() {
        val s = steps(S.NOT_RUNNING)
        assertTrue(s[0].done)
        assertFalse(s[2].done); assertFalse(s[3].done); assertFalse(s[4].done)
        assertEquals(GuideAction.OPEN_SHIZUKU, s[3].action)
    }

    @Test
    fun needsPermissionOffersThePermissionButton() {
        val s = steps(S.NEEDS_PERMISSION)
        assertTrue(s.take(4).all { it.done })
        assertFalse(s[4].done)
        assertEquals(GuideAction.REQUEST_PERMISSION, s[4].action)
    }

    @Test
    fun readyMarksEverythingDone() {
        val s = steps(S.READY)
        assertTrue(s.all { it.done })
        assertNull(s[4].action)
    }

    @Test
    fun developerOptionsFlagMarksStep2() {
        assertTrue(steps(S.NOT_RUNNING, dev = true)[1].done)
        assertFalse(steps(S.NOT_RUNNING, dev = false)[1].done)
    }

    @Test
    fun nextIsTheFirstPendingStepAndNullWhenAllDone() {
        assertEquals(1, ShizukuGuide.next(steps(S.NOT_INSTALLED))!!.number)
        assertEquals(2, ShizukuGuide.next(steps(S.NOT_RUNNING))!!.number)
        assertEquals(5, ShizukuGuide.next(steps(S.NEEDS_PERMISSION))!!.number)
        assertNull(ShizukuGuide.next(steps(S.READY)))
    }

    @Test
    fun androidBefore11HasNoWirelessDebuggingAndSaysSo() {
        val s = ShizukuGuide.steps(installed = true, running = false, granted = false, developerOptionsOn = true, sdkInt = 29)
        assertNull(s[2].action)
        assertTrue(s[2].body.contains("anterior al 11"))
        val ok = ShizukuGuide.steps(installed = true, running = false, granted = false, developerOptionsOn = true, sdkInt = 34)
        assertEquals(GuideAction.OPEN_DEVELOPER_OPTIONS, ok[2].action)
    }
}
