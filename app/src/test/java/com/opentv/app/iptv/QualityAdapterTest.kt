package com.opentv.app.iptv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityAdapterTest {
    private fun ok(fps: Double) = QualityAdapter.Sample(fps, fps, 8000, 8000, 1, false)
    private fun slow(fps: Double) = QualityAdapter.Sample(fps, 60.0, 1580, 1580, 1, false)
    private fun run(a: QualityAdapter, seconds: Int, s: QualityAdapter.Sample): List<QualityAdapter.Action> =
        (1..seconds).map { a.onSecond(s) }.filter { it.changed }

    @Test fun laEscaleraVaDeMasACargaAMenos() {
        val l = QualityAdapter.buildLadder(IptvPreset.FULL_HD_60, 1080)
        assertEquals(listOf("1080p · 60 fps", "720p · 60 fps", "480p · 30 fps"), l.map { it.shortLabel })
    }
    @Test fun noProcesaMasDeLoQueElTelevisorMuestra() {
        val l = QualityAdapter.buildLadder(IptvPreset.FULL_HD_60, 720)
        assertEquals(IptvPreset.HD_60, l.first()); assertTrue(l.all { it.height <= 720 })
    }
    @Test fun unEncodificadorSanoNoCambiaNada() {
        val a = QualityAdapter(IptvPreset.FULL_HD_60)
        assertTrue(run(a, 300, ok(60.0)).isEmpty()); assertEquals(IptvPreset.FULL_HD_60, a.current)
    }
    @Test fun sobrecargaEn1080p60BajaPrimeroLosFpsYLuegoLaResolucion() {
        val a = QualityAdapter(IptvPreset.FULL_HD_60)
        val acts = run(a, 120, slow(24.0))
        assertEquals(QualityAdapter.Kind.FPS, acts.first().kind); assertEquals(30, acts.first().fps)
        val res = acts.first { it.kind == QualityAdapter.Kind.RESOLUTION }
        assertEquals(IptvPreset.HD_60, res.preset)
        assertEquals(FpsGovernor.Bottleneck.ENCODER, res.cause)
    }
    @Test fun elBitrateAcompanaALosFps() {
        val a = QualityAdapter(IptvPreset.FULL_HD_60)
        val first = run(a, 40, slow(24.0)).first { it.kind == QualityAdapter.Kind.FPS }
        assertEquals(5_000_000, first.bitrateBps)
    }
    @Test fun unPicoAisladoNoDegrada() {
        val a = QualityAdapter(IptvPreset.FULL_HD_60)
        run(a, 30, ok(60.0))
        assertFalse(a.onSecond(slow(10.0)).changed)
        repeat(10) { assertFalse(a.onSecond(ok(60.0)).changed) }
        assertEquals(IptvPreset.FULL_HD_60, a.current); assertEquals(60, a.targetFps)
    }
    @Test fun laRedSoloBajaElBitrate() {
        val a = QualityAdapter(IptvPreset.HD_60)
        val net = QualityAdapter.Sample(60.0, 60.0, 6000, 1000, 1, false)
        val acts = run(a, 30, net)
        assertTrue(acts.isNotEmpty()); assertTrue(acts.all { it.kind == QualityAdapter.Kind.BITRATE })
        assertEquals(IptvPreset.HD_60, a.current)
        assertTrue(acts.last().bitrateBps < IptvPreset.HD_60.bitrateBps)
        assertTrue(acts.last().bitrateBps >= IptvPreset.HD_60.bitrateBps / 4)
    }
    @Test fun elBitrateSeRecuperaCuandoLaRedMejora() {
        val a = QualityAdapter(IptvPreset.HD_60)
        run(a, 20, QualityAdapter.Sample(60.0, 60.0, 6000, 1000, 1, false))
        val up = run(a, 80, ok(60.0))
        assertTrue(up.any { it.note?.contains("recupera") == true })
    }
    @Test fun noSubeDeResolucionSinUnLargoPeriodoEstable() {
        val a = QualityAdapter(IptvPreset.FULL_HD_60)
        run(a, 40, slow(24.0))
        val down = a.current
        assertTrue(down.height < 1080 || down.fps < 60)
        val early = run(a, 60, ok(down.fps.toDouble()))
        assertTrue(early.none { it.kind == QualityAdapter.Kind.RESOLUTION })
    }
    @Test fun trasMuchoTiempoEstablePruebaSubirYSiFallaEsperaMas() {
        val a = QualityAdapter(IptvPreset.FULL_HD_60)
        run(a, 40, slow(24.0)); val low = a.current
        val log = StringBuilder(); val ups = (1..800).map { t -> a.onSecond(ok(a.targetFps.toDouble())).also { if (it.changed) log.append("t=$t ${it.kind} ${it.preset.shortLabel} ") } }.filter { it.changed }
        assertTrue("debía probar subir: low=${low.shortLabel} cur=${a.current.shortLabel} drops=${a.resolutionDrops} probes=${a.resolutionProbes} tgt=${a.targetFps} log=$log", ups.any { it.kind == QualityAdapter.Kind.RESOLUTION })
        val probes = a.resolutionProbes; assertTrue(probes >= 1)
    }
    @Test fun elCalorNoPermiteSubir() {
        val a = QualityAdapter(IptvPreset.FULL_HD_60)
        run(a, 120, slow(24.0))
        val hot = QualityAdapter.Sample(30.0, 30.0, 6000, 6000, 1, thermalLimited = true)
        assertTrue(run(a, 600, hot).none { it.kind == QualityAdapter.Kind.RESOLUTION && it.cause == FpsGovernor.Bottleneck.NONE })
    }
    @Test fun colaCrecienteDelCodificadorSeTrataComoSobrecarga() {
        val a = QualityAdapter(IptvPreset.FULL_HD_60)
        val backlog = QualityAdapter.Sample(59.0, 60.0, 8000, 8000, 1, false, backlog = 30)
        val acts = run(a, 200, backlog)
        assertTrue(acts.any { it.cause == FpsGovernor.Bottleneck.ENCODER && it.kind != QualityAdapter.Kind.BITRATE })
    }
    @Test fun retargetReiniciaLaEscalera() {
        val a = QualityAdapter(IptvPreset.FULL_HD_60)
        run(a, 120, slow(24.0)); a.retarget(IptvPreset.COMPAT_480)
        assertEquals(IptvPreset.COMPAT_480, a.current)
    }
    @Test fun elPisoDeFpsDelGobernadorMarcaAgotado() {
        val g = FpsGovernor(30, floorFps = 30)
        var exhausted = false
        repeat(40) { if (g.onSecond(FpsGovernor.Sample(10.0, 10.0, 1000, 1000, 1, false)).exhausted) exhausted = true }
        assertTrue(exhausted)
    }

    // ── pérdida real de transporte (enlace «justo» que entrega ~70-90 %) ──
    private fun lossy(fps: Double, lostKbps: Int) = QualityAdapter.Sample(fps, fps, 10000, 8000, 1, false, lostKbps = lostKbps)

    @Test fun laPerdidaSostenidaBajaElBitrateSinTocarResolucionNiFps() {
        val a = QualityAdapter(IptvPreset.FULL_HD_60)
        val acts = run(a, 12, lossy(60.0, 1500))
        val br = acts.filter { it.kind == QualityAdapter.Kind.BITRATE }
        assertTrue(br.isNotEmpty())
        assertTrue(acts.none { it.kind == QualityAdapter.Kind.RESOLUTION || it.kind == QualityAdapter.Kind.FPS })
        assertEquals(IptvPreset.FULL_HD_60, a.current); assertEquals(60, a.targetFps)
        assertTrue(br.first().bitrateBps < IptvPreset.FULL_HD_60.bitrateBps)
    }
    @Test fun unaPerdidaAisladaNoCambiaNada() {
        val a = QualityAdapter(IptvPreset.FULL_HD_60)
        val acts = run(a, 30, ok(60.0)) + a.onSecond(lossy(60.0, 1500)) + run(a, 20, ok(60.0))
        assertTrue(acts.none { it.kind == QualityAdapter.Kind.BITRATE })
    }
    @Test fun elBitrateNuncaBajaDelPisoPorPerdida() {
        val a = QualityAdapter(IptvPreset.FULL_HD_60)
        val acts = run(a, 120, lossy(60.0, 3000))
        assertTrue(acts.filter { it.kind != QualityAdapter.Kind.NONE }.all { it.bitrateBps >= IptvPreset.FULL_HD_60.bitrateBps / 4 })
        assertEquals(60, a.targetFps)
    }
    @Test fun trasUnCorteLaRecuperacionEsMasLenta() {
        val a = QualityAdapter(IptvPreset.FULL_HD_60)
        run(a, 3, lossy(60.0, 1500))                                // un solo recorte (3 s con pérdida)
        val up1 = (1..60).map { a.onSecond(ok(60.0)) }.indexOfFirst { it.kind == QualityAdapter.Kind.BITRATE }
        assertTrue("debe recuperar", up1 >= 0)
        assertTrue("no antes de 35 s (la espera se duplicó a 40 s)", up1 >= 35)
    }
}
