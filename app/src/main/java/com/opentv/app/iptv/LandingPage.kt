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
     * ES3: sin let/const, flechas, fetch, JSON ni MSE. Elige el orden según lo que el navegador dice poder reproducir, pero
     * `canPlayType` miente en televisores viejos («hls=maybe» no garantiza nada): MP4 progresivo «probably» va antes que HLS «maybe»,
     * y TS directo solo se prueba si el navegador no lo descartó (ts=no). Un vigilante comprueba cada 2 s que `currentTime` avance:
     * si el video arrancó y luego se corta (el caso de «reproduce 2 s y se detiene»), reintenta el mismo método hasta 2 veces y, si no
     * sirve, pasa al siguiente. Antes, tras el primer «playing», cualquier corte posterior se ignoraba y la página se quedaba muerta.
     * Todo se informa al teléfono con imágenes pedidas con `new Image()`.
     */
    private const val SCRIPT = """
(function(){
var d=document.getElementById('d'),v=document.getElementById('v'),t=document.createElement('video');
function q(m){try{return t.canPlayType?t.canPlayType(m):''}catch(e){return ''}}
function r(x){return x==='probably'?2:(x==='maybe'?1:0);}
var H=q('application/vnd.apple.mpegurl'),M=q('video/mp4; codecs="avc1.4D401F, mp4a.40.2"'),T=q('video/mp2t');
function send(k,x){try{(new Image()).src='/diag?k='+encodeURIComponent(k)+'&v='+encodeURIComponent(x)+'&ua='+encodeURIComponent(navigator.userAgent||'')+'&t='+(new Date()).getTime();}catch(e){}}
function say(s){d.innerHTML=s;}
send('caps','hls='+(H||'no')+',mp4='+(M||'no')+',ts='+(T||'no')+',mse='+(typeof MediaSource!=='undefined'?1:0)+',screen='+(screen.width||0)+'x'+(screen.height||0));
if(!v){say('Modo solo enlaces: copiá una de las direcciones en tu reproductor.');return;}
var P=[{n:'MP4 progresivo',u:U.mp4,s:r(M)*2+1},{n:'HLS',u:U.hls,s:r(H)*2},{n:'TS directo',u:U.ts,s:r(T)*2-1}],L=[],i;
if(v.getAttribute('data-forced')==='1'){for(i=0;i<P.length;i++){if(v.src.indexOf(P[i].u)>=0){L.push(P[i]);}}}
else{for(i=0;i<P.length;i++){if(P[i].u!==U.ts||T){L.push(P[i]);}}L.sort(function(a,b){return b.s-a.s;});}
var n=-1,ok=false,played=false,tries=0,last=-1,stall=0,tm=null,wd=null;
function stopWd(){if(wd){clearInterval(wd);wd=null;}if(tm){clearTimeout(tm);tm=null;}}
function load(){
stopWd();ok=false;last=-1;stall=0;
say('Probando '+L[n].n+(tries?' (reintento '+tries+')':'')+'…');send('try',L[n].n);
try{v.pause();}catch(e){}
v.src=L[n].u;try{v.load();}catch(e){}try{v.play();}catch(e){}
tm=setTimeout(function(){if(!ok){send('timeout',L[n].n);fail('no arrancó en 12 s');}},12000);}
function next(){
stopWd();tries=0;played=false;n++;
if(n>=L.length){try{v.removeAttribute('src');v.load();}catch(e){}say('Este navegador no pudo reproducir ningún formato. Alternativas: 1) cargá http://'+location.host+U.m3u+' en una app IPTV del televisor (en un LG antiguo con Smart IPTV usá el canal de la prueba 2, TS directo); 2) el espejo nativo (Miracast) del sistema; 3) un receptor HDMI (TV box o stick Android) con OpenTV. Los navegadores de televisores muy antiguos suelen no reproducir HLS ni MP4 en vivo.');send('failed','all');return;}
load();}
function fail(why){
if(n<0||n>=L.length){return;}
stopWd();send('cut',L[n].n+': '+why);
if(played&&tries<2){tries++;load();}else{next();}}
v.onplaying=function(){
if(n<0||n>=L.length){return;}
ok=true;played=true;stall=0;if(tm){clearTimeout(tm);tm=null;}
say('Reproduciendo ('+L[n].n+').');send('playing',L[n].n);
if(!wd){wd=setInterval(function(){var c=v.currentTime||0;if(c>last+0.2){last=c;stall=0;}else{stall++;}if(stall>=3){fail('sin avance durante 6 s');}},2000);}};
v.onerror=function(){if(n<0||n>=L.length){return;}send('error',L[n].n+':'+((v.error&&v.error.code)||0));fail('error '+((v.error&&v.error.code)||0));};
v.onended=function(){if(n>=0&&n<L.length){fail('el flujo terminó');}};
v.onstalled=function(){if(n>=0&&n<L.length){send('stalled',L[n].n);}};
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
