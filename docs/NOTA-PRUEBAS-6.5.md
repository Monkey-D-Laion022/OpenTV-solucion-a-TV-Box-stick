# OpenTV Fase 6.5 · una sola tanda de pruebas

**Aviso:** esta versión NO fue compilada para Android en mi entorno (sin SDK). La 6.4 sí compiló en Actions (tu APK lo confirma). Subila y verificá que Actions compile; lo más probable de fallar es `FramePump.kt` (OpenGL). Si falla, pasame el error. Sin permisos nuevos respecto de la 6.4.

## 1) Teléfono + Noblex X7 + LG (lo principal: ¿sigue transmitiendo en segundo plano?)
1. IPTV → dejá «Ritmo fijo de cuadros: activado» → iniciar emisión. Cargá la lista en cada televisor.
2. Mirá en el teléfono: «Estado verificado», FPS capturados / entregados / codificados. Anotá los números.
3. Salí de OpenTV al inicio del teléfono (pantalla quieta) y esperá 2 minutos; luego abrí otra app; luego volvé a OpenTV. Esperado: el televisor sigue reproduciendo.
4. Si se corta: volvé a IPTV y anotá «Estado verificado/ATENCIÓN», «último cuadro hace…», «Última parada» y las últimas líneas del historial de pedidos.
5. Repetí 2–3 con «Ritmo fijo de cuadros: desactivado» (captura directa) y decime si en ese modo se corta y en el otro no.
6. «Audio solo en TV» activado: debe seguir funcionando.

## 2) 60 fps
«Medir mi teléfono» y copiá la lista. Probá a mano 1080p60 (calidad automática desactivada) y anotá FPS codificados reales.

## 3) Noblex 32" Linux
Probá en su app IPTV `http://IP:8090/tv/plain.m3u` y luego `compat.m3u`. Anotá el historial de pedidos (IP, agente, rutas, bytes) y qué pasó en pantalla.

## 4) LG navegador
`http://IP:8090/` → anotá el mensaje final. (Se espera que no reproduzca; el camino es SS IPTV.)

## 5) OpenTV Link y Wi‑Fi Direct
Si tenés un TV box: prueba de la nota 6.4. Wi‑Fi Direct: con el TV en «Screen Share», repetí el diagnóstico y anotá si `connect()` sigue dando error 0.

Informar siempre: modelo, esperado vs. observado y el texto exacto en pantalla si falla.
