# Pruebas físicas registradas (hechas por León; no repetidas por Claude)

## IPTV · Noblex X7 58" (Android TV) · IPTV Smarters Player
- Cargó la lista `http://192.168.1.100:8090/tv/channel.m3u` y accedió al canal. OpenTV registró 17 pedidos de 192.168.1.106; `seg-24.ts`…`seg-28.ts` con HTTP 200; 62 segmentos publicados; 365 KB/s (≈ 2,9 Mbit/s medio frente a 6 Mbit/s configurados: normal con pantalla poco cambiante y CBR del teléfono, no es un error por sí solo).
- Configuración: 1080p, 30 fps pedidos, 6 Mbit/s, segmentos de 2 s.
- **Demostrado**: comunicación y descarga correcta de segmentos. **No demostrado**: reproducción continua prolongada, audio.

## IPTV · LG 42LB5800-SB (NetCast) · Smart IPTV
- M3U, HLS y lista de compatibilidad TS: «error de conexión» en los tres; OpenTV vio pedidos de la LG para los tres enlaces.
- 1080p60: imagen entrecortada; audio no evaluable. Navegador integrado de la LG: pendiente.
- **Lectura honesta**: que lleguen pedidos prueba red y servidor, no decodificación. Hipótesis, sin causa atribuida: (a) la app puede no admitir HLS/TS de esta forma o exigir otro modo de carga de la lista; (b) límite del decodificador de la LG (nivel/perfil H.264, 1080p, bitrate); (c) segmentos de 2 s con búfer chico; (d) algo del TS (PCR/PSI/audio) que ese decodificador no tolere; (e) rendimiento del teléfono a 60 fps.
- **Cómo separarlas (Fase 6.2)**: perfiles 480p/360p a 30 fps (descarta decodificador/ancho de banda), TS directo (descarta HLS), página `/` en el navegador de la LG (informa qué formatos dice soportar y si el video arrancó), y FPS codificados/bitrate entregado medidos en el teléfono (descarta el codificador).

## Contradicción de audio reportada
Un mensaje decía «audio capturado» y otro «pista en silencio». Corregido en 6.1/6.2: una sola fuente de estado con 6 valores (pista de respaldo, sin permiso, no disponible, solo ceros, capturando sin sonido, capturando con sonido).

## Lista de pruebas para la versión 6.5 (por hacer; ver docs/NOTA-PRUEBAS-6.5.md)
Ver el archivo NOTA de la entrega: qué probar en el teléfono, el Noblex X7 y el LG 42LB5800-SB, resultado esperado y qué informar.

## Ronda 3 (2026-11-16)
1. **PS3:** instalar el PKG nuevo. (a) En el menú, START → vuelve al XMB. (b) Durante una transmisión, START → vuelve al menú sin reiniciar. (c) Con transmisión activa, botón PS → «Salir del juego» → debe volver al XMB. Si se cuelga: `opentv.log` y qué pantalla se veía.
2. **Redmi:** con IPTV emitiendo al LG, abrir y cerrar Stremio/Play TV 3 veces. Anotar la línea de temporización de la pantalla IPTV (ticks tardíos, FPS codificados) antes y después, y el logcat.
3. **DLNA:** Conexiones › «Espejo de pantalla con sonido»: iniciar, enviar al LG, salir de la pantalla y volver: la sesión debe seguir en estado reproduciendo.
4. **Miracast:** ¿se veía imagen cuando el log mostró RTP? Copiar el texto M3.
5. **IPTV › «Abrir el canal en otra app»:** elegir Web Video Caster y confirmar que reproduce en el LG.

## Ronda 4 (2026-11-17)
1. **PS3 (lo más importante):** instalar el PKG nuevo y abrirlo SIN transmitir: START debe salir al XMB. Luego transmitir (el teléfono muestra el código; en la PS3 aparece «Recibiendo…»). Si la imagen sigue negra: **foto del cartel «SIN IMAGEN»** y, después de salir con START, el archivo `opentv.log` (`/dev_hdd0/game/OPENTV001/USRDIR/opentv.log`). Si la consola vuelve a congelarse: anotar cuántos segundos pasaron y si el audio sonó.
2. Transmitir a la PS3 con **video en movimiento** (no la pantalla quieta): la prueba anterior produjo ~230 kbit/s.
3. **DLNA › «Espejo de pantalla con sonido»:** copiar el bloque de diagnósticos; anotar si el LG reproduce a 60 fps o cae al TS 1080p30 y cuántos timeouts hay.
4. **Navegador /tv:** Conexiones › «Espejo con sonido en el navegador (/tv)»; escribir la dirección en el navegador del LG; anotar qué se ve, si hay sonido y si se corta.
5. **Miracast:** copiar el texto M3 de la pantalla de diagnóstico WFD.
