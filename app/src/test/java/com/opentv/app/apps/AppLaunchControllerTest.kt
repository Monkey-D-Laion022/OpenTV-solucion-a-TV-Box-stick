package com.opentv.app.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLaunchControllerTest {

    private val landscapeApp = InstalledApp("com.video", "com.video.Main", "Video", screenOrientation = 0)
    private val portraitApp = InstalledApp("com.chat", "com.chat.Main", "Chat", screenOrientation = 1)

    private class Fixture(var result: LaunchResult = LaunchResult.Success) {
        val opened = mutableListOf<InstalledApp>()
        var missingCalls = 0
        val controller = AppLaunchController(
            opener = { app ->
                opened += app
                result
            },
            onAppMissing = { missingCalls++ },
        )
    }

    @Test
    fun normalAppOpensImmediately() {
        val f = Fixture()
        f.controller.onAppClick(landscapeApp)
        assertEquals(listOf(landscapeApp), f.opened)
        assertNull(f.controller.state.value.pendingWarning)
        assertNull(f.controller.state.value.message)
    }

    @Test
    fun portraitOnlyAppWaitsForConfirmation() {
        val f = Fixture()
        f.controller.onAppClick(portraitApp)
        assertTrue(f.opened.isEmpty())
        assertEquals(portraitApp, f.controller.state.value.pendingWarning)

        f.controller.confirmWarning()
        assertEquals(listOf(portraitApp), f.opened)
        assertNull(f.controller.state.value.pendingWarning)
    }

    @Test
    fun dismissingTheWarningDoesNotOpenTheApp() {
        val f = Fixture()
        f.controller.onAppClick(portraitApp)
        f.controller.dismissWarning()
        assertTrue(f.opened.isEmpty())
        assertNull(f.controller.state.value.pendingWarning)
    }

    @Test
    fun confirmWithoutPendingWarningDoesNothing() {
        val f = Fixture()
        f.controller.confirmWarning()
        assertTrue(f.opened.isEmpty())
    }

    @Test
    fun missingAppShowsMessageAndRequestsRefresh() {
        val f = Fixture(LaunchResult.NotFound)
        f.controller.onAppClick(landscapeApp)
        assertEquals("Video ya no está disponible.", f.controller.state.value.message)
        assertEquals(1, f.missingCalls)
    }

    @Test
    fun deniedAndFailedProduceReadableMessages() {
        val f = Fixture(LaunchResult.Denied)
        f.controller.onAppClick(landscapeApp)
        assertEquals("Android no permitió abrir Video.", f.controller.state.value.message)

        f.result = LaunchResult.Failed("boom")
        f.controller.onAppClick(landscapeApp)
        assertEquals("No se pudo abrir Video: boom", f.controller.state.value.message)
        assertEquals(0, f.missingCalls)
    }

    @Test
    fun successfulLaunchClearsPreviousMessage() {
        val f = Fixture(LaunchResult.Denied)
        f.controller.onAppClick(landscapeApp)
        f.result = LaunchResult.Success
        f.controller.onAppClick(landscapeApp)
        assertNull(f.controller.state.value.message)
    }
}
