package com.opentv.app.iptv

/**
 * Página de entrada para navegadores de TV viejos: `http://IP:8090/`. Solo HTML 4/5 básico y un script ES3 opcional que
 * únicamente informa al teléfono qué formatos dice soportar el navegador y si el video arrancó. Sin librerías, sin
 * fuentes ni imágenes, sin Internet. Si el navegador no ejecuta JavaScript, los enlaces funcionan igual.
 * No se asume que el navegador soporte HLS ni TS: se ofrecen las variantes para probar y se registra el resultado.
 */
object LandingPage {
    enum class Mode(val key: String, val label: String) {
        AUTO("auto", "automático"),
        HLS("hls", "HLS (m3u8)"),
        MP4("mp4", "MP4 progresivo"),
        TS("ts", "TS directo"),
        NONE("none", "solo enlaces"),
    }

    fun mode(query: String?): Mode =
        Mode.values().firstOrNull { m -> query?.split('&')?.any { it == "m=${m.key}" } == true } ?: Mode.AUTO

    /** [info]: resumen de la emisión actual (p. ej. «1280×720 · 30 fps») para que el usuario sepa qué recibe. */
    fun render(token: String, mode: Mode, channel: String, info: String = ""): String {
        val hls = "/$token/index.m3u8"
        val ts = "/$token/live.ts"
        val mp4 = "/$token/live.mp4"
        val forced = when (mode) { Mode.HLS -> hls; Mode.TS -> ts; Mode.MP4 -> mp4; else -> "" }
        val video = if (mode == Mode.NONE) "" else
            "<video id=\"v\" controls autoplay width=\"640\" height=\"360\"" + (if (forced.isNotEmpty()) " src=\"$forced\" data-forced=\"1\"" else "") + "></video>"
        val link = "style=\"color:#8cf\""
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width\">" +
            "<title>OpenTV</title></head><body style=\"background:#000;color:#fff;font-family:sans-serif;margin:8px\">" +
            "<h1 style=\"font-size:20px\">OpenTV · ${esc(channel)}</h1>$video" +
            "<p id=\"d\" style=\"font-size:15px\">Probando el navegador…</p>" +
            (if (info.isNotBlank()) "<p style=\"font-size:13px;color:#ccc\">Emisión: ${esc(info)}. Si se corta o va lento, en el teléfono elegí 480p o 360p.</p>" else "") +
            "<p style=\"font-size:13px\">Forzar: <a href=\"/?m=hls\" $link>HLS</a> · <a href=\"/?m=mp4\" $link>MP4</a> · <a href=\"/?m=ts\" $link>TS</a> · " +
            "<a href=\"/?m=auto\" $link>automático</a> · <a href=\"/?m=none\" $link>solo enlaces</a><br>" +
            "Abrir directo: <a href=\"$hls\" $link>HLS</a> · <a href=\"$mp4\" $link>MP4</a> · <a href=\"$ts\" $link>TS</a></p>" +
            "<script>var U={hls:\"$hls\",mp4:\"$mp4\",ts:\"$ts\",m3u:\"/$token/channel.m3u\"};" + SCRIPT + "</script></body></html>"
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    /**
     * ES3: sin let/const, flechas, fetch ni JSON. Prueba los métodos en orden (los que el navegador dice poder reproducir
     * primero; si no dice nada se prueban igual, porque `canPlayType` miente en televisores viejos) y pasa al siguiente si
     * hay error o no arranca en 12 s. Todo se informa al teléfono con imágenes pedidas con `new Image()`.
     */
    private const val SCRIPT = """
(function(){
var d=document.getElementById('d'),v=document.getElementById('v'),t=document.createElement('video');
function q(m){try{return t.canPlayType?t.canPlayType(m):''}catch(e){return ''}}
var H=q('application/vnd.apple.mpegurl'),M=q('video/mp4; codecs="avc1.4D401F, mp4a.40.2"'),T=q('video/mp2t');
function send(k,x){try{(new Image()).src='/diag?k='+encodeURIComponent(k)+'&v='+encodeURIComponent(x)+'&ua='+encodeURIComponent(navigator.userAgent||'')+'&t='+(new Date()).getTime();}catch(e){}}
function say(s){d.innerHTML=s;}
send('caps','hls='+(H||'no')+',mp4='+(M||'no')+',ts='+(T||'no')+',mse='+(typeof MediaSource!=='undefined'?1:0)+',screen='+(screen.width||0)+'x'+(screen.height||0));
if(!v){say('Modo solo enlaces: copiá una de las direcciones en tu reproductor.');return;}
var P=[['HLS',U.hls,H],['MP4 progresivo',U.mp4,M],['TS directo',U.ts,T]],L=[],R=[],i;
if(v.getAttribute('data-forced')==='1'){for(i=0;i<P.length;i++){if(v.src.indexOf(P[i][1])>=0){L.push(P[i]);}}}
else{for(i=0;i<P.length;i++){if(P[i][2]){L.push(P[i]);}else{R.push(P[i]);}}for(i=0;i<R.length;i++){L.push(R[i]);}}
var n=-1,ok=false,tm=null;
function next(){
if(tm){clearTimeout(tm);}
n++;
if(n>=L.length){say('Este navegador no pudo reproducir ningún formato. Alternativas: 1) cargá http://'+location.host+U.m3u+' en una app IPTV del televisor (en un LG antiguo con Smart IPTV usá el canal de la prueba 2, TS directo); 2) el espejo nativo (Miracast) del sistema; 3) un receptor HDMI (TV box o stick Android) con OpenTV. Los navegadores de televisores muy antiguos suelen no reproducir HLS ni MP4 en vivo.');send('failed','all');return;}
say('Probando '+L[n][0]+'…');send('try',L[n][0]);
v.src=L[n][1];try{v.load();}catch(e){}try{v.play();}catch(e){}
tm=setTimeout(function(){if(!ok){send('timeout',L[n][0]);next();}},12000);}
v.onplaying=function(){ok=true;if(tm){clearTimeout(tm);}say('Reproduciendo ('+L[n][0]+').');send('playing',L[n][0]);};
v.onerror=function(){if(ok){return;}send('error',L[n][0]+':'+((v.error&&v.error.code)||0));next();};
v.onstalled=function(){send('stalled',L[n][0]);};
next();
})();
"""
}

/** Limpia el texto que manda un navegador de la red antes de mostrarlo en el teléfono (es dato no confiable). */
object DiagText {
    fun clean(query: String): String {
        val map = query.split('&').mapNotNull { kv ->
            val i = kv.indexOf('=')
            if (i <= 0) null else kv.substring(0, i) to decode(kv.substring(i + 1))
        }.toMap()
        val k = map["k"] ?: "?"
        val v = map["v"] ?: ""
        val ua = map["ua"] ?: ""
        return "$k: $v · ${ua.take(70)}".filter { it >= ' ' }.take(220)
    }

    private fun decode(s: String): String = try {
        java.net.URLDecoder.decode(s, "UTF-8")
    } catch (e: IllegalArgumentException) {
        s
    }
}
