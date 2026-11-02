package com.opentv.app.link

/** `192.168.1.100:8765` → (host, puerto); `null` si no tiene esa forma. Lógica pura. */
object LinkAddress {
    fun parse(text: String): Pair<String, Int>? {
        val t = text.trim()
        val i = t.lastIndexOf(':')
        if (i <= 0) return null
        val port = t.substring(i + 1).toIntOrNull() ?: return null
        val h = t.substring(0, i)
        if (port !in 1..65535 || h.isBlank() || h.any { it.isWhitespace() }) return null
        return h to port
    }
}
