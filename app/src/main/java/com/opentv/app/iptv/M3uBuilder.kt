package com.opentv.app.iptv

/** Un canal de la lista M3U: nombre visible y dirección. */
data class M3uChannel(val id: String, val name: String, val url: String, val group: String = "OpenTV")

/** Genera la lista M3U que se carga en una app IPTV. Lógica pura. */
object M3uBuilder {

    /** Quita comillas y saltos de línea, que romperían el formato de la línea `#EXTINF`. */
    fun clean(text: String): String = text.replace('"', '\'').replace(Regex("[\\r\\n,]"), " ").replace(Regex("\\s+"), " ").trim()

    fun build(channels: List<M3uChannel>): String {
        val sb = StringBuilder("#EXTM3U\n")
        for (c in channels) {
            require(c.url.startsWith("http://") || c.url.startsWith("https://")) { "URL no válida: ${c.url}" }
            val name = clean(c.name).ifEmpty { "Canal" }
            sb.append("#EXTINF:-1 tvg-id=\"").append(clean(c.id)).append("\" tvg-name=\"").append(name)
                .append("\" group-title=\"").append(clean(c.group)).append("\",").append(name).append('\n')
            sb.append(c.url).append('\n')
        }
        return sb.toString()
    }
}
