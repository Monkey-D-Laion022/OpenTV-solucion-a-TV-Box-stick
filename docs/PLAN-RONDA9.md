# Plan y cambios — Ronda 9 (sobre los resultados de la ronda 8)

## Qué dicen los logs (resumen honesto)
- **PS3 720p30**: perfecto (buffer 34 ms, 0 descartes, cola 0 KB).
- **PS3 1080p60**: la PS3 recibió ~30 AU/s (~2,6 Mbit/s) y decodificó/presentó con holgura (llegó a 58–60 fps en ráfagas). El techo de 30 fps está **en el teléfono** (captura + codificador bajo carga: p50 de 23–28 ms ⇒ ≤36–43 fps). "Socket closed" es el cierre local al detener.
- **DLNA/LG**: ticks tardíos, encoder p50 23–28 ms, bitrate real 11,6 Mbit vs 10 pedidos, timeouts al arrancar.
- **Miracast**: 720p60 real (60,2 fps, 0 errores). El LG sólo declara 720p60 y 1080p30.
- **WVC**: fluido, pero el gobernador bajó a 30 fps por carga del codificador.

## Cambios de esta ronda
1. **Versión real**: `versionName = 6.7.9+yyyyMMdd.HHmm`; PS3 `OT_VERSION 6.7.9`, SFO 06.09. "Compilación y firma" sigue en Ajustes→Sistema.
2. **Miracast**: la sesión vive en `WfdSessionHolder` (nivel app); salir de la pantalla Wi‑Fi Direct ya no la corta. Opción **Forzar 1080p60** (modo no declarado por el TV, experimental): si el TV lo rechaza se reenvía automáticamente el mejor modo declarado.
3. **DLNA arranque**: `StreamHub` con *replay* del último GOP (opt‑in, sólo el hub TS): el LG recibe datos al instante en vez de esperar al próximo fotograma clave; ataca los `SocketTimeoutException` iniciales. Tests nuevos (544 JVM en verde).
4. **PS3**: presentador en hilo propio, triple buffer, tablas de escala (ronda 8, ya en el PKG).

## Límites que no se pueden prometer
- **60 fps sostenidos en 1080p** dependen de que el codificador del Redmi 14C (MediaTek) entregue ≤16 ms/cuadro. Con contenido liviano lo logró (rondas 3–4); con apps pesadas en primer plano no. No es un límite de PS3 ni del protocolo. El gobernador baja a 30 antes que mostrar tirones: es intencional.
- **Miracast 1080p60** sólo existe si el TV lo acepta; forzarlo es experimental.
- **TV DLNA muy viejos** (primeras generaciones, ~2004–2010): decodifican típicamente H.264 hasta 1080p30/High@4.0 y TS/MPEG‑2; 60 fps no es esperable. Se mantiene como base conservadora el perfil 720p30 como "seguro" y 60 fps sólo opt‑in. (Sin fuente primaria verificada en esta ronda; tomarlo como criterio de diseño, no como dato.)

## Receptor para navegadores obsoletos (evaluación)
- Viable sólo como **alternativa de bajo nivel**: página HTML + `<video>`/MSE o HLS si el navegador del TV lo soporta. Los navegadores de TV viejos (NetCast, Opera/Presto) carecen de MSE/WebSocket binario fiable y su `<video>` rara vez acepta TS en vivo; latencia alta (HLS ≥ 6 s) y sin A/V sync fino.
- Recomendación: **no integrarlo en OpenTV Link**; mantenerlo como posible "receptor de emergencia" (HLS por HTTP) en una ronda futura, tras una página de sondeo que reporte `MediaSource`, `canPlayType` y `WebSocket` del TV. Nada implementado en esta ronda.

## Qué probar
- LG por DLNA: tiempo hasta la primera imagen y ausencia de reintentos/timeouts al iniciar.
- Miracast: salir de la pantalla y volver con la sesión activa; probar "Forzar 1080p60" y ver si hay fallback.
- Ajustes→Sistema: versión `6.7.9+fecha` y firma. Si al instalar encima falla, desinstalar una vez (la firma anterior difiere).
