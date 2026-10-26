package com.opentv.app.stream

/**
 * Páginas HTML que se sirven al navegador del TV. HTML 4 simple, sin JavaScript ni CSS moderno, para que las abra
 * hasta un navegador muy viejo. Lógica pura.
 */
object MjpegPages {
    const val BOUNDARY = "opentvframe"

    /** [prefix]: «/» (URL corta) o «/código/». Página principal: la imagen en vivo (MJPEG) ocupa toda la pantalla sobre fondo negro. */
    fun live(prefix: String): String =
        """<!DOCTYPE html>
<html><head><meta charset="utf-8"><title>OpenTV</title>
<meta name="viewport" content="width=device-width, initial-scale=1"></head>
<body style="margin:0;padding:0;background:#000;color:#fff;font-family:sans-serif">
<img src="${prefix}stream.mjpg" alt="OpenTV" style="display:block;width:100%;height:auto;margin:0 auto">
<p style="font-size:14px;padding:4px 8px;margin:0;color:#888">Si no ves imagen, probá el <a href="${prefix}lento" style="color:#8cf">modo compatible</a>.</p>
</body></html>"""

    /** Modo compatible: sin MJPEG; la página se recarga sola y muestra una foto fija. Sirve en navegadores muy viejos. */
    fun slow(prefix: String, refreshSeconds: Int = 1): String =
        """<!DOCTYPE html>
<html><head><meta charset="utf-8"><title>OpenTV</title>
<meta http-equiv="refresh" content="$refreshSeconds">
<meta name="viewport" content="width=device-width, initial-scale=1"></head>
<body style="margin:0;padding:0;background:#000">
<img src="${prefix}snapshot.jpg" alt="OpenTV" style="display:block;width:100%;height:auto;margin:0 auto">
</body></html>"""
}
