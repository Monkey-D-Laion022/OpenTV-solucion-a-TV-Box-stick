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

## Informe de la ronda 4 (recibido 2026-11-18) — resultados
Emisor Redmi 14C (c2.mtk.avc.encoder); receptores LG 42LB5800-SB y PS3.
- **IPTV (LG):** informado 10/10 a 1080p60 con Stremio y Play TV Premium; el gobernador bajó el bitrate entre 42 % y 76 % ante cortes de red y se recuperó sin cerrar la transmisión. Pendiente: acortar el retardo del búfer HLS sin perder estabilidad; documentar el efecto del ahorro de energía sobre el codificador.
- **Web Video Caster:** respaldo válido. Con apps muy exigentes (Xtreamer) y microcongestión, el codificador acumuló ticks tardíos (>500) y la reproducción se cerró a los pocos segundos.
- **DLNA:** inestable. El LG no respondió `GetProtocolInfo`; cayó al perfil genérico 1080p30 con `SocketTimeoutException` y lag en multitarea. (No consta si el informe usó el APK con el intento HLS 1080p60; confirmar versión.)
- **Wi-Fi Direct / Miracast:** grupo formado (`p2p-p2p0-1`, GO 192.168.16.1, cliente 192.168.16.20); M1–M4 aceptados; RTP/TS sostenido hacia 192.168.16.1:19000, >14 000 paquetes sin errores de socket. El LG aceptó 60 Hz en la negociación. **No consta si se veía imagen ni los fps reales entregados.**
- **PS3:** «Recibiendo 1920x1080 a 60 fps», pantalla negra. HUD: `video 734 · AU enviadas 8 · AUDONE 0 · PICOUT 0 · err 0 (cod 0) · rc -2141126397 · reinicios 16 · descartes 13/713`. Congelada sin respuesta a START/PS.

### Lectura del dato de la PS3 (análisis, no confirmación)
- `rc -2141126397` = `0x80610103` = **`VDEC_ERROR_BUSY`** (verificado en `vdec.h` del SDK). No es «flujo rechazado»: la cola del decodificador está llena.
- Solo 8 AU aceptadas y **ningún AUDONE ni PICOUT ni error por callback**: el decodificador nunca terminó ni la primera unidad. A partir de ahí `vdecDecodeAu` devuelve BUSY; cada BUSY descarta hasta el siguiente IDR (de ahí 13 descartes por BUSY y 713 por falta de clave).
- Esto es coherente con un decodificador (SPU) atascado desde el inicio, o con callbacks que no llegan. **No** apunta a un fallo de red ni a un SPS inválido por sí solo (el verdicto de SPS está en `opentv.log`, que falta).
- Hipótesis para separar, sin causa atribuida: (a) 1080p60 nivel 4.2 excede lo que el decodificador de la PS3 maneja en este arranque; (b) forma del AU (SPS/PPS/AUD, ceros de relleno) que esta unidad no tolera; (c) configuración de `vdecOpen` (SPUs, prioridades, memoria); (d) el callback no se invoca.
- Que el reinicio contó 16 indica que el HUD corresponde a la compilación con recovery solo de diagnóstico; por tanto el **congelamiento persiste** aun sin `vdecClose` en el hilo principal. Sospecha nueva, sin confirmar: `sysProcessExit` con SPU del decodificador atascadas no termina.
- **Prueba decisiva (hardware):** transmitir a 720p30 (perfil Main, nivel ≤ 3.1). Si decodifica → es límite de nivel/resolución y se negocia un perfil PS3 en el emisor. Si no → es forma del AU o configuración de vdec. Junto con `opentv.log`.

## Respuestas de la ronda 4 (2026-11-18): Miracast M3 y DLNA
- **M3 del LG (observado):** declara 1920x1080p30 = sí, **1920x1080p60 = no**; la mayor progresiva a 60 Hz está en resoluciones menores. Audio: LPCM, AAC, AC3. RTP 19000. M4 negoció 1080p30.
- **Cartel «1920x1080 60 Hz»:** es la temporización de la salida HDMI del TV, no prueba de que el flujo sea de 60 fps. Sin confirmar: si se vio imagen real y a cuántos fps.
- **Conclusión:** 1080p60 por Miracast no es posible con este TV (el sink no lo declara); lo máximo es 1080p30, o 60 fps en una resolución menor declarada. Hipótesis a probar, sin hardware: perfil Miracast 720p60.
- **DLNA (observado):** pedido 1080p30 a 6000 kbit/s. Reposo: capturados 2 fps (pantalla quieta), codificados 29. Tras Play TV Premium: capturados 22, codificados 30, bitrate 6677 kbit/s, 19 ticks tardíos, máx 130 ms, tiempo del codificador p50 ≈ 45–49 ms por cuadro (> 33 ms), gobernador: «el codificador no sostiene el ritmo pedido».
- **Sin resolver:** la línea «se intentó 1080p60» coexiste con un pedido de 30 fps; no consta qué transporte (HLS o TS) se usó. Falta la línea de `DlnaSession`/`ProfileSelector`. Comparar el tiempo del codificador con la línea de IPTV 1080p60 del mismo teléfono.

## opentv.log de la PS3 (2026-11-18) — resultado y variantes de decodificador
- **Observado:** 1080p60 (SPS perfil 77 nivel 4.2) y 720p30 (nivel 3.1) fallan igual: `vdecDecodeAu` acepta 8 AU, luego `0x80610103` (BUSY); `AUDONE 0`, `PICOUT 0`, sin error por callback; el cierre excede 4 s y queda `bloqueado=1`.
- **Derivado:** el nivel/resolución NO es la causa; ningún callback llegó nunca (ni siquiera de error). Las 8 AU coinciden con una cola de comandos llena de un decodificador que no procesa.
- **Desconocido:** qué parámetro de `vdecOpen`/SPU/memoria o qué módulo lo causa. Hipótesis: SPU sin inicializar, `num_spus`/prioridades, alineación de la memoria del decodificador.
- **Cambio (PKG compila y verifica; sin probar en consola):** el receptor ahora prueba una **variante distinta en cada arranque** mientras la anterior haya recibido video sin producir nunca un AUDONE. Variantes: 0 actual; 1 `sysSpuInitialize(6,0)`; 2 un SPU; 3 dos SPU; 4 prioridades/pila distintas; 5 seis SPU. Estado en `USRDIR/vdec_variant.txt`; HUD «SIN IMAGEN» muestra `var N`. La memoria del decodificador se pide alineada a 1 MiB (con respaldo). El log ahora registra `sysModuleLoad`, `cmd_depth`, versión y parámetros.
- **Cómo probar:** abrir la app, transmitir 720p30 ~20 s, salir con START (o apagar), repetir. En cada vuelta mirar `var N` en pantalla. Cuando alguna imagen aparezca, el log dirá «variante N: el decodificador respondió» y esa queda fija.


## Ronda 5 — informe físico (2026-11-20/22), resumen
- Miracast LG 720p60: +208,5 s sin cortes, keepalive M16 cada ~10 s con 200 OK, 87.401 paquetes RTP, 0 errores de envío, ~60 fps, imagen en el televisor.
- DLNA LG: pidiendo 720p arranca en 1080p30 y baja sin lograr conectar en varios intentos; 1080p60 por MPEG-TS con imagen firme pero con bajones/cortes bajo carga pesada (Play TV Premium). Otro Smart TV viejo: DLNA muy bien incluso con apps pesadas.
- IPTV con Stremio: excelente; cartel de ahorro de energía visible. Retardo: a revisar a futuro.
- WVC 720p60 6000 kbit/s con Stremio: excelente.
- PS3: pantalla negra. Log: `sysModuleLoad VDEC=0 VDEC_H264=0`, variante 0, emparejamiento OK, OFFER 1280x720 30 fps 3000 kbps aceptada, SPS perfil 77 nivel 3.1 compatible; `vdecDecodeAu` = 0x80610103 (BUSY), «decodificador sin respuesta (1,5 s…)», hasta 13 reinicios; al reconectar «no respondió al cierre (4 s): se abandona» y queda bloqueado. Log en `/dev_hdd0/game/OPENTV001/USRDIR/` y `/dev_hdd0/tmp/opentv.log`.
