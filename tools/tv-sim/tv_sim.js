// Simula el navegador de un TV viejo ejecutando el script de LandingPage.kt (sin DOM real). Uso: node tv_sim.js
var fs=require('fs'),vm=require('vm'),path=require('path');
var src=fs.readFileSync(path.join(__dirname,'../../app/src/main/java/com/opentv/app/iptv/LandingPage.kt'),'utf8');
var script=src.substring(src.indexOf('SCRIPT = """')+12,src.lastIndexOf('"""'));
if(script.length>3500){console.log('FAIL script demasiado grande',script.length);process.exit(1);}
['=>','let ','const ','fetch(','JSON.','`','async ','Promise'].forEach(function(b){if(script.indexOf(b)>=0){console.log('FAIL token prohibido',b);process.exit(1);}});
var fails=0;function check(n,c){console.log((c?'ok   ':'FAIL ')+n);if(!c)fails++;}
function run(caps,scn){
 var log=[],timers=[],now=0,id=0,video={currentTime:0,played:false,error:null,srcs:[],
  pause:function(){},load:function(){},play:function(){},removeAttribute:function(){},getAttribute:function(){return null;},
  set src(x){this.srcs.push(x);this._s=x;},get src(){return this._s||'';}};
 var probe={canPlayType:function(m){return caps[m.indexOf('mpegurl')>=0?'hls':m.indexOf('mp4')>=0?'mp4':'ts']||'';}};
 var sb={U:{hls:'/tv/index.m3u8',mp4:'/tv/live.mp4',ts:'/tv/live.ts',m3u:'/tv/channel.m3u'},
  document:{getElementById:function(i){return i==='v'?video:{};},createElement:function(){return probe;}},
  navigator:{userAgent:'NetCast'},screen:{width:1920,height:1080},location:{host:'x'},
  Image:function(){var o=this;Object.defineProperty(o,'src',{set:function(u){log.push(decodeURIComponent(u.split('k=')[1].split('&')[0])+'='+decodeURIComponent(u.split('&v=')[1].split('&')[0]));}});},
  setTimeout:function(f,ms){timers.push({id:++id,f:f,at:now+ms});return id;},clearTimeout:function(i){timers=timers.filter(function(t){return t.id!==i;});},
  setInterval:function(f,ms){timers.push({id:++id,f:f,at:now+ms,every:ms});return id;},clearInterval:function(i){timers=timers.filter(function(t){return t.id!==i;});},
  encodeURIComponent:encodeURIComponent,Date:Date};
 vm.runInNewContext(script,sb);
 function adv(ms){var end=now+ms;for(;;){timers.sort(function(a,b){return a.at-b.at;});var t=timers[0];if(!t||t.at>end)break;now=t.at;if(t.every)t.at+=t.every;else timers.shift();scn.tick&&scn.tick(now,video);t.f();}now=end;}
 scn.go(video,adv,sb);return {log:log,video:video};
}
// 1) MP4 'probably' se prueba antes que HLS 'maybe'; arranca y avanza: no cambia de método
var r1=run({hls:'maybe',mp4:'probably',ts:''},{go:function(v,adv){check('1 primer intento MP4',v.srcs[0]==='/tv/live.mp4');v.onplaying();for(var i=0;i<10;i++){v.currentTime+=2;adv(2000);}check('1 sigue en MP4 mientras avanza',v.srcs.length===1);}});
// 2) ts=no: nunca se ofrece TS
var r2=run({hls:'',mp4:'maybe',ts:''},{go:function(v,adv){v.onerror();v.error={code:4};v.onerror();v.error={code:4};}});
check('2 sin TS si el navegador lo descartó',r2.video.srcs.indexOf('/tv/live.ts')<0);
// 3) corte tras reproducir: reintenta 2 veces y luego cambia de método
var r3=run({hls:'maybe',mp4:'probably',ts:'maybe'},{go:function(v,adv){v.onplaying();adv(60000);}});
check('3 reintento 1',r3.video.srcs[1]==='/tv/live.mp4');
check('3 reintento 2 y luego cambia',r3.video.srcs[2]==='/tv/live.mp4'&&r3.video.srcs.length>=4&&r3.video.srcs[3]!=='/tv/live.mp4');
// 4) no arranca en 12 s: pasa al siguiente
var r4=run({hls:'maybe',mp4:'probably',ts:''},{go:function(v,adv){adv(13000);}});
check('4 timeout de arranque cambia de método',r4.video.srcs.length>=2&&r4.video.srcs[1]!=='/tv/live.mp4');
// 5) todos fallan: mensaje final, sin bucle infinito
var r5=run({hls:'maybe',mp4:'maybe',ts:'maybe'},{go:function(v,adv){for(var i=0;i<12;i++){adv(13000);}}});
check('5 termina sin bucle ('+r5.video.srcs.length+' cargas)',r5.video.srcs.length<=3&&r5.log.indexOf('failed=all')>=0);
console.log(fails?'FALLAS: '+fails:'TODO OK');process.exit(fails?1:0);
