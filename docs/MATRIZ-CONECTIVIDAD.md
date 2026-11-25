# Matriz de conectividad inalámbrica (2026-11-14)
Estados: APROBADA (integrada + compila + pruebas disponibles OK) · PARCIAL · FALLIDA (error reproducible) · PENDIENTE DE HARDWARE.
Regla aplicada: **nada simulado se marca APROBADA.** Android no se pudo compilar aquí (lo compila GitHub Actions); por eso ninguna tecnología Android llega a APROBADA todavía.

| Tecnología | Estado | Base de la calificación | Pendiente |
|---|---|---|---|
| APK / app base | APROBADA (usuario, ronda 1) | prueba física del usuario | — |
| IPTV | PARCIAL | HW: 1080p60 bien; cae bajo multitarea extrema. Cambios (cola por bitrate, adaptación por pérdida) solo PC/SIM | repetir carga extrema en el Redmi |
| Espejo / DLNA | PARCIAL | HW: 1080p30, baja latencia, tirones y ≈5 FPS capturados. Cambio en `FramePump` sin compilar/medir | medir FPS capturado/codificado/mostrado; logcat de microcierres |
| /tv (navegador) | FALLIDA en ronda 1 (HW); corrección PENDIENTE DE HARDWARE | script v2 + Plan B probados solo en simulador | probar en la LG |
| Miracast / Wi-Fi Direct | FALLIDA (M4) | causa desconocida sin el texto de M3 | enviar el texto M3 del diagnóstico |
| OpenTV Link (PS3) | PARCIAL | HW: cifrado y audio OK, imagen negra. Protocolo: 14 pruebas PC OK. Diagnóstico agregado | ejecutar en la PS3 y enviar `opentv.log` |
| Web Video Caster / alternativa | PARCIAL (investigación) | cubierta por DLNA + Plan B de /tv; sin función "enviar URL" dedicada | decidir si se agrega |
| Tecnología propia nueva | NO JUSTIFICADA | ver `INVESTIGACION-TECNOLOGIA-PROPIA.md` | — |

## Auditoría (hecha por revisión de código y tests PC)
- Receptores PC eliminados (receivers/pc, tools/receiver-py); docs y texto de la app corregidos.
- Interfaz: Miracast/Cast, /tv, IPTV, OpenTV Link y DLNA ya son grupos separados en Conexiones. `MethodAdvisor` existe y tiene tests, pero **no está conectado a la pantalla** (no se hizo a ciegas): pendiente.
- Funciones visibles sin validar: textos "Sin probar" ya presentes en la UI para IPTV y Link.
- Fugas/concurrencia/sesiones largas: NO medidas. Solo hay `HubStress` (PC) para el Hub. Requiere hardware.

## Actualización 2026-11-16
- PS3: START en el menú sale al XMB; «Salir del juego» (botón PS) se atiende con el callback sysUtil, que no existía. El botón PS no se puede interceptar. PKG compila; sin probar en consola.
- Interfaz de conexiones: tres apartados (DLNA, /tv, IPTV) y debajo «Otros métodos» (Miracast/Cast, OpenTV Link). Sin compilar Compose aquí.
- "Shisoku": no existe en el código ni en la documentación (ver `docs/AUDITORIA-SHISOKU-Y-HOME.md`).

## Actualización 2026-11-16 (noche) — 2.ª ronda de campo (informe de Gemini, sin logs adjuntos)
| Hallazgo informado | Qué se hizo | Estado |
|---|---|---|
| Miracast: M3 sin 1080p60; flujo RTP a 1920x1080p30 (11078 paquetes a los 41,4 s) | Nada que forzar: se respeta lo declarado. Contradice en parte la ronda 1 (fallo en M4): puede ser intermitente o haber mejorado. **No se sabe si se vio imagen** | PARCIAL, a confirmar |
| IPTV: 1080p60 estable, p50 16,7 ms por cuadro | — | PARCIAL (cortes al cambiar de app) |
| Microcortes al abrir/cerrar otras apps | El servicio de captura YA es un servicio en primer plano independiente de la Activity. Defecto real corregido: la sesión DLNA colgaba del `viewModelScope` (sin `onCleared`) y moría al salir de la pantalla/destruirse la Activity → ahora la posee `IptvDlna` (ámbito de proceso) y se detiene sola si el canal para. **Causa de los microcortes: hipótesis** (competencia por GPU al arrancar la otra app; `FramePump` ya repite el último cuadro a ritmo fijo) | PENDIENTE DE HARDWARE |
| Web Video Caster con `index.m3u8` funciona; cae a 30 fps con apps pesadas | Botón «Abrir el canal en otra app» (Intent ACTION_VIEW, application/vnd.apple.mpegurl). El televisor decodifica el HLS igual que antes; el teléfono sigue capturando y codificando, así que **no libera recursos del teléfono** | Implementado, sin probar |
| Espejo DLNA anidado en IPTV | Ahora es «Espejo de pantalla con sonido» dentro del apartado DLNA (pantalla propia, mismo motor); IPTV ya no muestra el bloque DLNA | Implementado, sin compilar Compose |
| PS3: cuelgue al perder foco / START / PS | El PKG probado no registraba el callback sysUtil. Ahora: callback + START; `sleep()` de 4–6 s reemplazados por esperas que atienden botones/callback; `send()` con tope de 3 s para que un teléfono desaparecido no cuelgue la consola. **Causa del cuelgue no confirmada** | PENDIENTE DE HARDWARE |

## Actualización 2026-11-17 — 3.ª ronda física (Redmi + LG + PS3) y cambios
**Resultados informados (hardware real):** IPTV/HLS al LG 10/10 a 1080p60 (también con Web Video Caster y con Stremio abierto; bajadas de bitrate al 40% y recuperación solas). DLNA: conexión mejor pero 30 fps y lag con multitarea; el LG no responde a `GetProtocolInfo`; 2 timeouts antes de reproducir. PS3: «Recibiendo 1920×1080», pantalla negra, corte a los 2 s, y la consola quedó **congelada (ni START ni PS)**; el log del teléfono muestra `ECONNREFUSED` en el puerto 8766. Miracast: no se probó.

| Módulo | Estado | Qué se cambió (sin compilar Android; PS3 sí compila) | Pendiente de hardware |
|---|---|---|---|
| IPTV/HLS | **PARCIAL alto** (10/10 informado; no se marca APROBADA: los cambios de esta ronda no están compilados y hay microcortes al cambiar de app) | Sin cambios de comportamiento | repetir con el APK nuevo |
| DLNA | PARCIAL | (1) Si el TV no declara formatos se prueba primero **HLS 1080p60** (evidencia: HLS 1080p60 ya funcionó en este LG con su reproductor nativo); el TS 1080p30 genérico queda de respaldo. (2) `GetProtocolInfo` espera 2 s, no 6. (3) La pantalla DLNA muestra los mismos diagnósticos que IPTV (FPS capturados/entregados/codificados, bitrate, gobernador, tiempos). | ¿reproduce a 60? ¿siguen los timeouts? |
| /tv navegador | PENDIENTE DE HARDWARE | **Se eliminó el espejo por navegador anterior (MJPEG)** con su pantalla, sus clases y sus tests. «Espejo con sonido en el navegador (/tv)» usa ahora el motor de IPTV (dirección corta, HLS/TS con audio, Plan B de enlaces directos) | probar en el navegador del LG |
| Miracast / Wi-Fi Direct | PENDIENTE DE HARDWARE | Nada que forzar: ver `docs/MIRACAST-60FPS.md` | M3 del TV |
| OpenTV Link / PS3 | **FALLIDA** (pantalla negra + congelamiento) | Ver sección PS3 abajo | `opentv.log` |

### PS3: causa probable del congelamiento (hipótesis fuerte, NO confirmada)
Tres lugares del hilo principal podían esperar para siempre, y ahí no se atiende ni START ni PS:
1. `vdec_recover()` (agregado por mí en la ronda 1) cerraba y reabría el decodificador tras 1,5 s sin respuesta; **`vdecClose` sobre un decodificador atascado no vuelve nunca**. El corte «a los 2 s» y la consola congelada encajan con esto. Es mi propio cambio: pudo convertir un decodificador mudo en un congelamiento total.
2. `ps3av_shutdown()` (al salir con START/PS) llamaba al mismo `vdecClose`.
3. Esperas de GPU sin tope (`flip`, `wait_finish`).
**Cambios:** el recovery quedó **solo de diagnóstico**; el cierre del decodificador se hace en un hilo aparte con tope de 4 s y, si no responde, se abandona (el video queda desactivado, el audio sigue); la salida al XMB ya no cierra el decodificador; esperas de GPU acotadas (1 s / 100 ms); contadores de decodificador atómicos. El PKG compila y verifica. **No resuelve la pantalla negra**: sigue sin causa confirmada. El `ECONNREFUSED` indica que la app dejó de escuchar (se colgó o terminó) y es consistente con lo anterior.
**Telemetría del emisor:** a 1080p60 el codificador entregó ~230 kbit/s a 30 fps frente a 7–10 Mbit/s pedidos: con la pantalla casi estática un codificador CBR por hardware puede producir muy poco, así que no prueba un fallo por sí solo; conviene repetir con video en movimiento.

## Actualización 2026-11-18 (informe de la ronda 4)
| Módulo | Estado | Evidencia |
|---|---|---|
| IPTV/HLS | PARCIAL alto (10/10 informado; no APROBADA hasta compilar y repetir) | 1080p60, Stremio/Play TV, gobernador 42–76 % sin caídas |
| Web Video Caster | PARCIAL (respaldo válido) | cierres con Xtreamer ante microcongestión (>500 ticks tardíos) |
| DLNA | PARCIAL / inestable | sin respuesta a GetProtocolInfo → 1080p30 genérico, timeouts; versión del APK por confirmar |
| Miracast / Wi-Fi Direct | PARCIAL | enlace y RTP verificados (>14 000 paquetes, 60 Hz aceptado); imagen y fps reales sin confirmar |
| OpenTV Link / PS3 | **FALLIDA** | decodificador BUSY (0x80610103) tras 8 AU, AUDONE 0; congelamiento persiste |

## Cambios de la ronda 5 (2026-11-19) — todos PENDIENTES DE HARDWARE
Ver `CAMBIOS-RONDA5.md`: PS3 variantes de vdec; DLNA memoria de formato por TV y 2.º MIME HLS; Miracast 720p60 (el LG no declara 1080p60); gobernador por tics atrasados; aviso de ahorro de energía; Home v3 como prueba aislada. Ningún estado sube de categoría hasta la próxima ronda física.

## Actualización ronda 5 (informe físico 2026-11-20)
| Ruta | Resultado informado | Estado |
|---|---|---|
| IPTV (con y sin ahorro) | estable, sin cortes | APROBADA |
| Miracast LG 720p60 | negocia y envía ~61 fps, 0 errores; el LG cierra a los ~52 s | PARCIAL (M16 keepalive agregado: PENDIENTE DE HARDWARE) |
| DLNA LG | reproduce TS; rendimiento malo con apps pesadas; ignoraba el perfil elegido | PARCIAL (perfil del usuario primero: PENDIENTE DE HARDWARE) |
| Web Video Caster 1080p60 | sin cierre de Stremio; no sostiene 60 fps con apps pesadas | PARCIAL |
| PS3 | sin imagen (`var 0`, BUSY) | FALLIDA (variantes + log nuevos: PENDIENTE DE HARDWARE) |
| Home v3 | compila y se ve; faltaba táctil | PARCIAL (táctil agregado: no compilado) |

## Cierre de la ronda 5 (informe físico completo, 2026-11-22)
| Ruta | Resultado | Estado |
|---|---|---|
| Miracast LG 1280x720p60 | negoció M1–M5, PLAY OK, RTP/MPEG-TS con AAC; 87.401 paquetes, 0 errores, ~60 fps, GET_PARAMETER cada ~10 s con 200 OK; **+208,5 s sin cortes** (se cortó a mano) con imagen en el LG | APROBADA (720p60, LG 42LB5800-SB) |
| IPTV (LG) | Stremio excelente y estable; cartel de ahorro de energía visible | APROBADA |
| Web Video Caster | 720p60 / 6000 kbit/s con Stremio excelente; 1080p60 sigue costando | APROBADA a 720p60 · PARCIAL a 1080p60 |
| DLNA (LG) | 1080p60 TS con imagen firme; bajones y cortes bajo carga; al pedir 720p arranca en 1080p30 y baja (otro Smart TV viejo anda muy bien) | PARCIAL |
| PS3 | pantalla negra; decodificador no responde (BUSY); autotest del decodificador agregado | FALLIDA · autotest PENDIENTE DE HARDWARE |
| Home v3 / Home 2 | v3 anda y le falta ser horizontal (corregido); Home 2 integrado como módulo opcional | v3 PARCIAL · Home 2 PENDIENTE DE HARDWARE (no compilado) |


## Ronda 7 (físico, 2026-11-24)
| Tecnología | Estado | Nota |
|---|---|---|
| PS3 | **PARCIAL (imagen OK)** | Decodifica 720p sin errores; ~20 fps mostrados (presentación en el hilo de red), pausas del flujo cada ~20 s, imagen pixelada por el piso de bitrate (ronda 8: corregido, SIN verificar en consola) |
| Miracast / Wi‑Fi Direct | APROBADA (720p60) | Sin cambios |
| DLNA LG 42LB5800 | PARCIAL | 1080p60/720p60 con app pesada: lag, tics atrasados; ronda 8 adapta por ventana (SIN verificar) |
| IPTV | APROBADA | Sin cambios |
| WVC 1080p60 | APROBADA con microcortes | Sin cambios propios |
| Actualizar encima | FALLÓ | Causa no confirmada; diagnóstico en Ajustes → Sistema |
