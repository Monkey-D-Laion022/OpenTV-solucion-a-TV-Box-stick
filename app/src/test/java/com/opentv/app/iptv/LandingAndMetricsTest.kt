package com.opentv.app.iptv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LandingAndMetricsTest {
    private val status = object : IptvStatusSource {
        override val channelName = "OpenTV <Pantalla>"
        override val width = 1280; override val height = 720; override val fps = 30
        override val bitrateBps = 3_000_000; override val withAudio = true
        override val clients = 0; override val startedAtMs = 0L
    }
    private fun router(token: String) = IptvRouter(token, HlsSegmenter(), status) { 1_000L }
    private fun body(r: IptvResponse) = String((r as IptvResponse.Body).body, Charsets.UTF_8)

    @Test fun raizConDireccionCortaDevuelveHtml() {
        val r = router("tv").route("GET", "/", "x") as IptvResponse.Body
        assertEquals(200, r.status); assertTrue(r.contentType.startsWith("text/html"))
        val html = body(r)
        assertTrue(html.contains("/tv/index.m3u8")); assertTrue(html.contains("/tv/live.ts")); assertTrue(html.contains("/tv/live.mp4"))
        assertTrue(html.contains("<video")); assertFalse(html.contains("data-forced=\"1\"")) // automático: el script elige el método
        assertTrue(html.contains("&lt;Pantalla&gt;")) // el nombre se escapa
    }
    @Test fun raizConCodigoAleatorioNoRevelaNada() {
        assertEquals(404, (router("a1b2c3d4e5f60718").route("GET", "/", "x") as IptvResponse.Body).status)
        assertEquals(404, (router("a1b2c3d4e5f60718").route("GET", "/diag?k=a", "x") as IptvResponse.Body).status)
    }
    @Test fun paginaSinRecursosExternosNiFrameworks() {
        val html = body(router("tv").route("GET", "/?m=none", "x"))
        val markup = html.substringBefore("<script>") // el script arma la dirección del propio teléfono con location.host
        assertFalse(markup.contains("http://") || markup.contains("https://") || markup.contains("<img") || html.contains("<script src"))
        assertFalse(html.contains("<video")) // modo sin reproductor
        assertTrue(html.length < 6_000)
    }
    @Test fun modosDeReproduccion() {
        assertEquals(LandingPage.Mode.AUTO, LandingPage.mode(null))
        assertEquals(LandingPage.Mode.MP4, LandingPage.mode("m=mp4"))
        assertEquals(LandingPage.Mode.TS, LandingPage.mode("m=ts"))
        assertEquals(LandingPage.Mode.NONE, LandingPage.mode("x=1&m=none"))
        assertEquals(LandingPage.Mode.AUTO, LandingPage.mode("m=raro"))
        assertTrue(body(router("tv").route("GET", "/?m=ts", "x")).contains("src=\"/tv/live.ts\""))
    }
    @Test fun scriptEsEs3SinSintaxisModerna() {
        val html = body(router("tv").route("GET", "/", "x"))
        for (bad in listOf("=>", "let ", "const ", "fetch(", "JSON.", "`", "async ", "Promise")) assertFalse(bad, html.contains(bad))
    }
    @Test fun diagRegistraYLimpia() {
        val got = mutableListOf<String>()
        val rt = router("tv"); rt.onDiag = { got += it }
        val r = rt.route("GET", "/diag?k=caps&v=hls%3Dmaybe%2Cts%3Dno&ua=Mozilla%2F5.0%20(NetCast)%0A&t=1", "x") as IptvResponse.Body
        assertEquals(204, r.status)
        assertEquals(1, got.size)
        assertTrue(got[0].startsWith("caps: hls=maybe,ts=no")); assertFalse(got[0].contains('\n')); assertTrue(got[0].contains("NetCast"))
    }
    @Test fun diagAcotaLongitudYTolerarEntradaRota() {
        assertTrue(DiagText.clean("k=a&v=" + "x".repeat(5000)).length <= 220)
        assertNotNull(DiagText.clean("%%%&=&k"))
        assertTrue(DiagText.clean("v=%ZZ").contains("%ZZ"))
    }
    @Test fun rutasAnterioresSiguenIgual() {
        val rt = router("tv")
        assertEquals(404, (rt.route("GET", "/otro/index.m3u8", "x") as IptvResponse.Body).status)
        assertEquals(503, (rt.route("GET", "/tv/index.m3u8", "x") as IptvResponse.Body).status)
        assertEquals(200, (rt.route("GET", "/tv/channel.m3u", "h:1") as IptvResponse.Body).status)
        assertEquals(IptvResponse.LiveTs, rt.route("GET", "/tv/live.ts", "x"))
    }

    @Test fun rateMeter() {
        val m = RateMeter()
        assertEquals(0.0, m.sample(0, 1_000), 0.0)
        assertEquals(30.0, m.sample(30, 2_000), 0.001)
        assertEquals(120.0, m.sample(90, 2_500), 0.001)
        assertEquals(0.0, m.sample(90, 2_500), 0.0) // sin tiempo transcurrido
    }
    @Test fun veredictoDeFps() {
        assertNull(FpsVerdict.describe(60, 10.0, 3)) // muy pronto para juzgar
        assertNull(FpsVerdict.describe(30, 29.0, 20))
        assertTrue(FpsVerdict.describe(60, 31.0, 20)!!.contains("31"))
        assertTrue(FpsVerdict.describe(30, 0.0, 20)!!.contains("no está entregando"))
    }
    @Test fun estadosDeCaptura() {
        assertEquals(IptvAudio.CAPTURING_ZEROS, AudioActivityMeter.captureState(false, 0, 10_000))
        assertEquals(IptvAudio.CAPTURING_NO_SOUND, AudioActivityMeter.captureState(true, 1_000, 20_000))
        assertEquals(IptvAudio.CAPTURING_SOUND, AudioActivityMeter.captureState(true, 19_000, 20_000))
        assertFalse(AudioActivityMeter.hasAnySignal(ByteArray(100)))
        assertTrue(AudioActivityMeter.hasAnySignal(byteArrayOf(0, 0, 1)))
    }
    @Test fun presetsCompatibles() {
        assertEquals(480, IptvPreset.COMPAT_480.height); assertEquals(360, IptvPreset.COMPAT_360.height)
        assertEquals(AvcLevels.L31, AvcLevels.forStream(854, 480, 30))
        assertEquals(IptvPreset.SAFE, IptvPreset.COMPAT_360.next())
        // el selector baja desde 720p60 a un perfil menor cuando solo se admite 360p
        assertEquals(IptvPreset.COMPAT_360, IptvProfilePicker.pick(IptvPreset.BALANCED) { w, _, _ -> w <= 640 }.preset)
    }
}

class LandingScriptTest {
    private fun script(): String {
        val html = LandingPage.render("tv", LandingPage.Mode.AUTO, "x", "1280×720 · 30 fps")
        return html.substringAfter("<script>").substringBefore("</script>")
    }
    @Test fun elScriptPruebaTresMetodosYAvisaAlTelefono() {
        val js = script()
        assertTrue(js.contains("'HLS'") && js.contains("'MP4 progresivo'") && js.contains("'TS directo'"))
        assertTrue(js.contains("/diag?k=") && js.contains("onplaying") && js.contains("onerror") && js.contains("setTimeout"))
    }
    @Test fun elScriptNoUsaSintaxisPosteriorAEs3() {
        val js = script()
        for (bad in listOf("=>", "let ", "const ", "fetch(", "JSON.", "`", "async ", "Promise", ".forEach", ".map(", ".bind(", "addEventListener", "classList"))
            assertFalse(bad, js.contains(bad))
    }
    @Test fun modoForzadoFijaElOrigenSinJavaScript() {
        val html = LandingPage.render("tv", LandingPage.Mode.MP4, "x")
        assertTrue(html.contains("src=\"/tv/live.mp4\"") && html.contains("data-forced=\"1\""))
    }
    @Test fun elNombreDelCanalSeEscapa() {
        assertTrue(LandingPage.render("tv", LandingPage.Mode.AUTO, "<b>&").contains("&lt;b&gt;&amp;"))
    }
    @Test fun direccionManual() {
        assertEquals("192.168.1.100" to 8765, com.opentv.app.link.LinkAddress.parse(" 192.168.1.100:8765 "))
        assertNull(com.opentv.app.link.LinkAddress.parse("192.168.1.100")); assertNull(com.opentv.app.link.LinkAddress.parse("a b:80"))
        assertNull(com.opentv.app.link.LinkAddress.parse("host:99999")); assertNull(com.opentv.app.link.LinkAddress.parse(":80"))
    }
}
