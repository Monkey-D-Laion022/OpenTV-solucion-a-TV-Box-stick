package com.opentv.app.media.dlna

import java.net.DatagramPacket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.SocketTimeoutException
import java.net.URL

/** Red de DLNA/UPnP con java.net puro (sin permisos extra). Bloquea: llamar desde un hilo de fondo. NO verificado contra un LG real. */
class DlnaClient(private val connectTimeoutMs: Int = 3_000, private val readTimeoutMs: Int = 6_000) {

    /** Busca renderizadores (MediaRenderer) por SSDP durante [windowMs] y lee la descripción de cada uno. */
    fun discover(windowMs: Long = 3_000): List<Renderer> {
        val locations = linkedSetOf<String>()
        val sock = MulticastSocket(null)
        try {
            sock.reuseAddress = true
            sock.bind(InetSocketAddress(0))
            sock.soTimeout = 500
            val msg = ("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\n" +
                "ST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n\r\n").toByteArray()
            val group = InetAddress.getByName("239.255.255.250")
            repeat(2) { sock.send(DatagramPacket(msg, msg.size, group, 1900)) }
            val end = System.currentTimeMillis() + windowMs
            val buf = ByteArray(2048)
            while (System.currentTimeMillis() < end) {
                try {
                    val p = DatagramPacket(buf, buf.size); sock.receive(p)
                    val text = String(p.data, 0, p.length)
                    text.lineSequence().firstOrNull { it.startsWith("LOCATION:", true) }?.substringAfter(':')?.trim()?.let { locations += it }
                } catch (_: SocketTimeoutException) {}
            }
        } finally { runCatching { sock.close() } }
        return locations.mapNotNull { loc -> runCatching { DlnaXml.parseRenderer(httpGet(loc), loc) }.getOrNull() }.distinctBy { it.udn }
    }

    private fun httpGet(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = connectTimeoutMs; c.readTimeout = readTimeoutMs
        try { return c.inputStream.bufferedReader().use { it.readText() } } finally { c.disconnect() }
    }

    /** Una orden SOAP. Nunca lanza: devuelve [SoapResult] con `failure` si algo salió mal. */
    fun soap(service: ServiceRef, action: String, body: String): SoapResult {
        return try {
            val c = URL(service.controlUrl).openConnection() as HttpURLConnection
            c.connectTimeout = connectTimeoutMs; c.readTimeout = readTimeoutMs
            c.requestMethod = "POST"; c.doOutput = true
            c.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
            c.setRequestProperty("SOAPACTION", DlnaXml.soapAction(service.type, action))
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            c.disconnect()
            if (code in 200..299) SoapResult(code, text) else SoapResult(code, text, "HTTP $code ${DlnaXml.errorDetail(text).orEmpty()}".trim())
        } catch (e: Exception) { SoapResult(0, "", e.javaClass.simpleName + (e.message?.let { ": $it" } ?: "")) }
    }

    /** Formatos que el televisor dice aceptar; `null` si no se pudo leer. */
    fun readSink(r: Renderer): List<ProtocolInfo>? {
        val cm = r.connectionManager ?: return null
        val res = soap(cm, "GetProtocolInfo", DlnaXml.protocolInfo(cm.type))
        return if (res.ok) DlnaXml.parseSink(res.body) else null
    }

    fun setAndPlay(r: Renderer, url: String, title: String, protocolInfo: String): SoapResult {
        val t = r.avTransport.type
        // Para cortar lo anterior; el error se ignora (puede no haber nada que parar).
        soap(r.avTransport, "Stop", DlnaXml.stop(t))
        val set = soap(r.avTransport, "SetAVTransportURI", DlnaXml.setUri(t, url, DlnaXml.didl(title, url, protocolInfo)))
        if (!set.ok) return set
        return soap(r.avTransport, "Play", DlnaXml.play(t))
    }

    fun transport(r: Renderer): TransportInfo? {
        val res = soap(r.avTransport, "GetTransportInfo", DlnaXml.transportInfo(r.avTransport.type))
        return if (res.ok) DlnaXml.parseTransportInfo(res.body) else null
    }

    fun stop(r: Renderer) = soap(r.avTransport, "Stop", DlnaXml.stop(r.avTransport.type))
    fun pause(r: Renderer) = soap(r.avTransport, "Pause", DlnaXml.pause(r.avTransport.type))
    fun play(r: Renderer) = soap(r.avTransport, "Play", DlnaXml.play(r.avTransport.type))
}
