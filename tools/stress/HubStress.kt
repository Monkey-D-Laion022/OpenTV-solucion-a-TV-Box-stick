// Simulación determinista (tiempo lógico, sin red ni teléfono) del reparto del flujo TS: cuánto video se pierde cuando el televisor
// o el Wi-Fi se atrasan. Usa el StreamHub REAL de la app. NO mide CPU, GPU, codificador ni Wi-Fi reales.
import com.opentv.app.stream.StreamHub

class Result(val label: String, val sent: Int, val delivered: Int, val resyncs: Int, val worstGapMs: Long, val peakQueueKb: Int)

fun simulate(label: String, fps: Int, mbps: Double, maxBytes: Int, drainMbps: Double, stallEveryMs: Long, stallMs: Long, seconds: Int): Result {
    val hub = StreamHub(maxQueuedChunks = 300, maxQueuedBytes = maxBytes)
    val sub = hub.subscribe()
    val frameBytes = (mbps * 1e6 / 8 / fps).toInt()
    var sent = 0; var delivered = 0; var resyncs = 0; var lastDeliverMs = 0L; var worst = 0L; var peak = 0
    var credit = 0.0; var wasActive = true
    val stepMs = 1000L / fps
    var t = 0L
    while (t < seconds * 1000L) {
        val key = (t / stepMs) % fps == 0L
        // el codificador hace un cuadro clave ~6 veces más grande y reparte el resto (CBR)
        val size = if (key) frameBytes * 6 else (frameBytes * (fps - 6.0) / (fps - 1)).toInt().coerceAtLeast(500)
        hub.publish(ByteArray(size), key); sent++
        val stalled = stallEveryMs > 0 && (t % stallEveryMs) < stallMs
        if (!stalled) credit += drainMbps * 1e6 / 8 * stepMs / 1000.0
        while (credit > 0) {
            val c = sub.poll(0) ?: break
            credit -= c.size; delivered++
            if (t - lastDeliverMs > worst) worst = t - lastDeliverMs
            lastDeliverMs = t
        }
        if (credit > frameBytes * 12) credit = frameBytes * 12.0
        val q = reflectQueued(sub); if (q > peak) peak = q
        t += stepMs
    }
    return Result(label, sent, delivered, resyncs, worst, peak / 1024)
}
fun reflectQueued(s: StreamHub.Subscriber): Int { val f = s.javaClass.getDeclaredField("queuedBytes"); f.isAccessible = true; return (f.get(s) as java.util.concurrent.atomic.AtomicInteger).get() }

fun main() {
    println("%-58s %6s %6s %7s %9s %8s".format("escenario (1080p60, 10 Mbps CBR, 60 s)", "enviad", "entreg", "perdid%", "huecoMax", "colaKB"))
    val cases = listOf(
        Triple("Wi-Fi sano (drena 40 Mbps), sin cortes", 40.0, 0L to 0L),
        Triple("Wi-Fi sano con microcorte de 300 ms cada 5 s", 40.0, 5000L to 300L),
        Triple("Wi-Fi sano con microcorte de 800 ms cada 5 s", 40.0, 5000L to 800L),
        Triple("Wi-Fi sano con corte de 1,5 s cada 10 s", 40.0, 10000L to 1500L),
        Triple("Enlace justo (drena 12 Mbps) con corte de 300 ms/5 s", 12.0, 5000L to 300L),
        Triple("Enlace insuficiente (drena 8 Mbps)", 8.0, 0L to 0L),
    )
    for ((l, d, st) in cases) {
        val r = simulate(l, 60, 10.0, (System.getProperty("cap","1024").toInt()) * 1024, d, st.first, st.second, 60)
        println("%-58s %6d %6d %6.1f%% %7d ms %7d".format(r.label, r.sent, r.delivered, 100.0 * (r.sent - r.delivered) / r.sent, r.worstGapMs, r.peakQueueKb))
    }
}
