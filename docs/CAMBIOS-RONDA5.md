# Cambios de la ronda 5 → entrega ronda 6 (2026-11-19 a 11-22) — qué es verificado y qué no
> Este archivo acumula las tres partes. La entrega actual es `OpenTV-unificada-ronda6.zip`: autotest PS3 + Home 2 opcional + resultados cerrados de la ronda 5.
Base: informe de pruebas de la ronda 4. Los tests JVM (532, OK) cubren la lógica pura; **nada de lo de abajo está probado en hardware ni compilado como APK** (el APK solo se compila en GitHub Actions).

| Área | Cambio | Verificado | Pendiente de hardware |
|---|---|---|---|
| PS3 | Variantes de `vdecOpen` rotativas por arranque, `vdec_variant.txt`, `var N` en el HUD, log de `sysModuleLoad`/`cmd_depth`. PKG compila y verifica. | compilación del PKG | que alguna variante haga responder al decodificador |
| DLNA | Se recuerda por televisor (UDN) el formato que funcionó y se prueba primero. Intento especulativo HLS 1080p60 sin reintentos (pasa al siguiente enseguida; antes ~50 s de espera). Segundo MIME de HLS (`x-mpegURL`). El diagnóstico muestra «Intento i de N · transporte · recuperaciones · última causa». | tests (`Ronda4Test`, `DlnaLogicTest`) | si el LG reproduce HLS60 con alguno de los dos MIME |
| Miracast | `preferFluidity` (activo en el flujo real): 1080p60 → 720p60 → 1080p30. Con el M3 real del LG negocia **720p60**. Solo elige modos DECLARADOS. El resumen informa cuadros codificados y fps promedio. | tests con el M3 real | si el LG muestra imagen y a cuántos fps |
| Web Video Caster / IPTV | El gobernador cuenta los tics atrasados de la bomba (≥ 3/s o 10 % del objetivo, 5 s seguidos) como sobrecarga y baja FPS antes de que el reproductor se quede sin datos. Un pico aislado no cambia nada. | tests | si evita los cierres con Xtreamer |
| IPTV | Aviso en pantalla si el ahorro de energía de Android está activo. **Retardo del búfer: no se tocó** (ver abajo). | tests (`PowerSaveJudge`) | — |
| Home v3 | Prueba aislada (ver `DECISION-HOME-V3.md` y `HOME-V3-QUITAR.txt`). | nada compilado | compilación y uso |

## Sobre el motor «independiente» de DLNA
No se creó un segundo motor de captura: Android permite **una sola** captura de pantalla a la vez y duplicar el codificador compite por el mismo hardware. Lo independiente es la sesión DLNA (`IptvDlna`, propia y de proceso), su selección/memoria de formato y su panel. Los datos de la ronda 4 muestran captura quieta (2 fps), 30 fps codificados y 45–49 ms por cuadro bajo carga: el cuello es el codificador del teléfono con apps pesadas, no el acoplamiento con IPTV.

## Retardo del búfer HLS (IPTV): no se cambia
Los perfiles de 60 fps usan segmentos de 4 s: los reproductores arrancan ~3 segmentos antes del final → ~12 s teóricos (6 s en los de 2 s). Acortar a 2 s en 60 fps duplicaría las peticiones y los cuadros clave por minuto sin evidencia de que el LG lo tolere; el IPTV está 10/10 con la configuración actual, así que se deja. Prueba sugerida si querés bajar el retardo: `1080p · 30 fps` (2 s) y comparar.


---
# Segunda parte de la ronda 5 — respuesta al informe físico (2026-11-20)
Todo lo de esta sección es **PENDIENTE DE HARDWARE** y no está compilado como APK (se compila en GitHub Actions). Tests JVM: 532 OK; PS3: `make pkg verify`, pruebas de link y de H.264 OK.

| # | Hallazgo del informe | Cambio | Estado |
|---|---|---|---|
| 1 | Home sin táctil | `home/TouchWidgets.kt`: botones y tarjetas con toque (`detectTapGestures`, anti doble-toque 350 ms). Sin funciones extra. | no compilado |
| 2 | DLNA ignoraba el perfil elegido a mano (arrancaba 1080p30) | Si el LG no declara formatos, el 1.er intento es **TS con el perfil del canal** (`UserProfile`); HLS 60 queda después. Los formatos rechazados (HTTP 500 / 704) se recuerdan por televisor y se omiten (`dropRejected`). | tests JVM |
| 3 | Miracast: el LG corta a los ~30 s | El SETUP anuncia `timeout=30`; ahora la fuente envía **M16 (GET_PARAMETER con Session) cada 10 s** mientras transmite y tolera su respuesta. | tests JVM; efecto real sin probar |
| 4 | Ahorro de energía sin cartel | Detección ampliada (Android + interruptores de Xiaomi `POWER_SAVE_MODE_OPEN` / `power_supersave_mode_open`); el aviso se muestra en ambos diseños de la pantalla IPTV. | tests JVM; detección en HyperOS sin verificar |
| 5 | WVC 1080p60 con apps pesadas | Sin cambios de motor: es contención de GPU/codificador del teléfono (p50 35 ms > 16,7 ms de un cuadro a 60). Se mantiene el gobernador por tics atrasados. Recomendado: 720p60 o 1080p30 con apps pesadas. | sin cambio |
| 6 | PS3 negra (`var 0`, rc BUSY) y log no encontrado | El log y el estado de variante se escriben en `/dev_hdd0/tmp/` **y** en la carpeta de datos de la app (derivada de `argv[0]`). | PKG verificado |

## PS3: cómo probar las variantes
1. Instalar el PKG nuevo y abrir la app. Si queda sin imagen, **cerrarla y volver a abrirla**: cada arranque prueba la variante siguiente (HUD: `var N`, de 0 a 5).
2. Anotar en qué `var` aparece imagen (PICOUT > 0). Si ninguna, mandar el log.
3. Log: `/dev_hdd0/tmp/opentv.log` (también `<carpeta de la app>/USRDIR/opentv.log`). Estado: `/dev_hdd0/tmp/opentv_variant.txt`.
4. El audio se prueba después de lograr imagen.

## Lo que sigue abierto
- Causa raíz del decodificador de la PS3 (BUSY sin callbacks en 720p30 y 1080p60): sin identificar.
- Retardo HLS del IPTV: sin cambios.

## Home v3: orientación fija (2026-11-22)
`OpenTvHomeActivity` ahora es `sensorLandscape` con los mismos `configChanges` que `MainActivity` (no rota ni se recrea). Solo manifest. Análisis del Nova launcher: `ANALISIS-NOVA-LAUNCHER.md`.

## Ronda 5 — resultados del informe físico y cambios de la tercera parte (2026-11-22)
Resultados: Miracast LG 720p60 estable 208 s (keepalive M16 funcionó, aprobado en esa prueba); IPTV con Stremio excelente y ahorro de energía con cartel; WVC 720p60 excelente; DLNA LG sin cambios de fondo; PS3 sigue sin imagen.

### PS3: autotest del decodificador (PENDIENTE DE HARDWARE)
Causa raíz **sin identificar** (decodificador acepta unidades y nunca avisa). En vez de rotar variantes con reinicios de la app, ahora **al arrancar** la app:
1. Abre un decodificador por cada una de 4 configuraciones (4 SPU estándar; 1 SPU pila 64 KiB prioridades altas; 1 SPU con memoria en páginas de 1 MiB; 2 SPU).
2. Le entrega una **unidad H.264 de prueba incrustada** (320x240 Baseline, SPS+PPS+IDR, generada con x264) con 3 formatos: tal cual / sin marcas de tiempo / sin marcas de tiempo + AUD delante.
3. La primera combinación que responde (AUDONE/PICOUT en 0,7 s) se usa para el video real; el HUD muestra `var CF` (C = configuración, F = formato; `var 99` = ninguna respondió). Resultado en `opentv.log` y en `vdec_autotest.txt` (carpeta de la app y `/dev_hdd0/tmp/opentv_autotest.txt`).
4. Se registran los tipos de NAL y los primeros bytes de las primeras unidades reales del teléfono, para compararlos con la de prueba.
Cómo interpretar: si **ninguna** responde con el video de prueba, el problema es del decodificador/entorno de la PS3 (no del video del teléfono); si **alguna** responde y el video real sigue sin imagen, el problema está en el video real y el log de NAL lo muestra. Los decodificadores que no responden se abandonan sin cerrarlos (cerrarlos congelaba la consola).
Sospechas cubiertas: formato de marcas de tiempo (el encabezado de PSL1GHT declara `{low, hi}` y Sony `{upper, lower}`), AUD, número de SPU, pila/prioridades de los hilos, tipo de página de memoria.

### Home 2 de prueba
Ver `HOME2-NOVA.md` (módulo opcional, no incluido en el build por defecto).
