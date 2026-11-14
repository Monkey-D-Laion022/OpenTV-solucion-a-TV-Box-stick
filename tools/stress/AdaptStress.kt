// Bucle cerrado en PC (tiempo lógico): codificador CBR → StreamHub real → enlace con capacidad variable, con el QualityAdapter REAL.
// Compara ANTES (cola fija de 1 MB, adaptador sin señal de pérdida) contra DESPUÉS (cola por tiempo + señal de pérdida). NO mide hardware.
import com.opentv.app.iptv.*
import com.opentv.app.stream.StreamHub

class Out(val lossPct: Double, val avgMbps: Double, val worstGapMs: Long, val finalLabel: String, val resDrops: Int, val lossLast60: Double)

fun run(after: Boolean, linkMbps: (Int) -> Double, stall: (Long) -> Boolean, seconds: Int): Out {
    val preset = IptvPreset.FULL_HD_60
    val hub = StreamHub(maxQueuedBytes = if (after) HubSizing.queueBytes(preset.bitrateBps) else 1024 * 1024)
    val sub = hub.subscribe(); val ad = QualityAdapter(preset, 1080)
    var bitrate = preset.bitrateBps; var fps = preset.fps
    var sent = 0L; var dropped0 = 0L; var credit = 0.0; var last = 0L; var worst = 0L; var bytesOut = 0L
    var sentLast60 = 0L; var dropLast60 = 0L
    var frame = 0L; var nextFrameNs = 0.0; var t = 0L
    while (t < seconds * 1000L) {
        val stepMs = 1; t += stepMs
        // cuadros del codificador a `fps`
        while (nextFrameNs <= t) {
            val key = frame % fps == 0L
            val per = bitrate / 8.0 / fps
            val size = (if (key) per * 6 else per * (fps - 6.0) / (fps - 1)).toInt().coerceAtLeast(400)
            hub.publish(ByteArray(size), key); sent += size; if (t > (seconds - 60) * 1000L) sentLast60 += size
            frame++; nextFrameNs += 1000.0 / fps
        }
        if (!stall(t)) credit += linkMbps((t / 1000).toInt()) * 1e6 / 8 / 1000
        credit = minOf(credit, 200_000.0)
        while (credit > 0) { val c = sub.poll(0) ?: break; credit -= c.size; bytesOut += c.size; if (t - last > worst) worst = t - last; last = t }
        if (t % 1000 == 0L) {
            val d = hub.droppedBytes.get(); val lostKbps = ((d - dropped0) * 8 / 1000).toInt()
            if (t > (seconds - 60) * 1000L) dropLast60 += d - dropped0
            dropped0 = d
            val produced = bitrate / 1000
            val act = ad.onSecond(QualityAdapter.Sample(fps.toDouble(), fps.toDouble(), produced, (bytesOut * 8 / 1000 / (t / 1000)).toInt().coerceAtMost(produced), 1, false, lostKbps = if (after) lostKbps else 0))
            if (act.changed) {  bitrate = act.bitrateBps; fps = act.fps; if (act.kind == QualityAdapter.Kind.RESOLUTION) bitrate = act.preset.bitrateBps }
        }
    }
    val lossPct = 100.0 * hub.droppedBytes.get() / hub.publishedBytes.get()
    return Out(lossPct, sent * 8 / 1e6 / seconds, worst, ad.current.shortLabel + " @" + fps + "fps " + bitrate / 1000 + "kbps", ad.resolutionDrops, 100.0 * dropLast60 / sentLast60.coerceAtLeast(1))
}

fun main() {
    data class Sc(val name: String, val link: (Int) -> Double, val stall: (Long) -> Boolean)
    val scs = listOf(
        Sc("Wi-Fi sano 40 Mbps + microcorte 300 ms/5 s", { 40.0 }, { it % 5000 < 300 }),
        Sc("Wi-Fi sano 40 Mbps + corte 1,2 s cada 10 s", { 40.0 }, { it % 10000 < 1200 }),
        Sc("Enlace justo 9 Mbps (película bajando a la vez)", { 9.0 }, { false }),
        Sc("Enlace 7 Mbps constante", { 7.0 }, { false }),
        Sc("Enlace 12 Mbps que cae a 6 Mbps entre 60-120 s y vuelve", { if (it in 60..119) 6.0 else 12.0 }, { false }),
    )
    println("%-56s | %-9s | %7s %7s %8s %-34s".format("escenario (1080p60 pedido, 300 s)", "versión", "perd%", "ultim60", "huecoMax", "estado final"))
    for (sc in scs) for (after in listOf(false, true)) {
        val r = run(after, sc.link, sc.stall, 300)
        println("%-56s | %-9s | %6.1f%% %6.1f%% %6d ms %-34s".format(sc.name, if (after) "DESPUÉS" else "antes", r.lossPct, r.lossLast60, r.worstGapMs, r.finalLabel))
    }
}
