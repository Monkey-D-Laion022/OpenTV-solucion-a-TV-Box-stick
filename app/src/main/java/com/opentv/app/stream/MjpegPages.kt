package com.opentv.app.stream

/**
 * Páginas HTML que se sirven al navegador del TV. HTML simple y JavaScript básico (sin CSS moderno), para que las abra
 * hasta un navegador muy viejo. Lógica pura.
 */
object MjpegPages {
    const val BOUNDARY = "opentvframe"

    /**
     * Ajusta la imagen al tamaño de la ventana SIN deformarla (franjas negras donde sobre). Sin CSS moderno: calcula la
     * escala con JavaScript simple (compatible con navegadores de TV viejos) y recalcula si cambia la proporción o la
     * ventana. Hasta que sabe el tamaño real la imagen está oculta: así nunca se ve estirada de entrada.
     */
    private fun fitScript(): String =
        """<script type="text/javascript">
function fit(){var i=document.getElementById('v');var w=i.naturalWidth||0,h=i.naturalHeight||0;if(!w||!h)return;
var W=window.innerWidth||document.documentElement.clientWidth||screen.width;
var H=window.innerHeight||document.documentElement.clientHeight||screen.height;
var s=Math.min(W/w,H/h);var nw=Math.floor(w*s),nh=Math.floor(h*s);
i.style.width=nw+'px';i.style.height=nh+'px';i.style.marginTop=Math.floor((H-nh)/2)+'px';i.style.visibility='visible';}
document.getElementById('v').onload=fit;window.onresize=fit;setInterval(fit,700);
</script>"""

    private const val NOSCRIPT = """<noscript><style type="text/css">#v{visibility:visible !important;width:100%}</style></noscript>"""

    private fun page(extraHead: String, src: String): String =
        """<!DOCTYPE html>
<html><head><meta charset="utf-8"><title>OpenTV</title>
$extraHead<meta name="viewport" content="width=device-width, initial-scale=1"></head>
<body style="margin:0;padding:0;background:#000;overflow:hidden">
<img id="v" src="$src" alt="" style="display:block;margin:0 auto;visibility:hidden">
$NOSCRIPT
${fitScript()}
</body></html>"""

    /** [prefix]: «/» (URL corta) o «/código/». Página principal: imagen en vivo (MJPEG) ajustada a la ventana, fondo negro. */
    fun live(prefix: String): String = page("", "${prefix}stream.mjpg")

    /** Modo compatible: sin MJPEG; la página se recarga sola y muestra una foto fija. Sirve en navegadores muy viejos. */
    fun slow(prefix: String, refreshSeconds: Int = 1): String =
        page("<meta http-equiv=\"refresh\" content=\"$refreshSeconds\">\n", "${prefix}snapshot.jpg")
}
