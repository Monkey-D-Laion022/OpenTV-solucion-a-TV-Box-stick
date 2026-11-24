package com.opentv.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteRequestTest {
    @Test fun aceptaSoloRutasConocidas() {
        assertEquals(Routes.IPTV, RouteRequest.accept(Routes.IPTV))
        assertEquals(Routes.CONNECTIONS, RouteRequest.accept(Routes.CONNECTIONS))
        assertNull(RouteRequest.accept("pending/local_media"))
        assertNull(RouteRequest.accept("cualquier-cosa"))
        assertNull(RouteRequest.accept(null))
    }

    @Test fun todasLasRutasPermitidasExistenEnRoutes() {
        val known = setOf(
            Routes.HOME, Routes.MEDIA, Routes.CONNECTIONS, Routes.CONNECT, Routes.APPS, Routes.SETTINGS, Routes.IPTV, Routes.DLNA_MIRROR,
            Routes.BROWSER, Routes.LINK_SEND, Routes.LINK_RECEIVE, Routes.PLAYER, Routes.MIRROR, Routes.REMOTE, Routes.TOOLS, Routes.DEVICE,
        )
        assertEquals(known, RouteRequest.ALLOWED)
        assertTrue(RouteRequest.EXTRA.isNotEmpty())
    }

    @Test fun elIdPermiteRepetirLaMismaRuta() {
        assertTrue(RouteRequest(1, Routes.IPTV) != RouteRequest(2, Routes.IPTV))
    }
}
