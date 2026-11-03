# Compatibilidad por televisor (6.5): hechos, hipótesis y qué falta

Solo cuenta como «funciona» la reproducción efectiva de audio y video; recibir pedidos HTTP no lo prueba.

| Televisor | Hechos medidos (pruebas de la 6.4) | Hipótesis | Cómo se aclara en la 6.5 |
|---|---|---|---|
| LG 42LB5800-SB (NetCast ~2014) | SS IPTV carga la lista y empieza; audio solo en TV funciona; el navegador dice HLS no soportado; MP4 muestra el reproductor y no inicia; se corta al salir de OpenTV | El corte: cuadros que dejan de llegar (ver `IPTV-CORTE-Y-60FPS.md`). Navegador: sin HLS y sin MP4 fragmentado en vivo (típico de navegadores de 2014; **sin documentación de LG que lo confirme**) | Sin cuadros muertos el canal TS/HLS por SS IPTV debería sostenerse; la prueba lo dirá. Para navegador no hay formato probado: usar SS IPTV (canal TS de `compat.m3u`) o receptor HDMI |
| Noblex X7 (Android TV) | HLS y MP4 en navegador, M3U en app IPTV y audio solo en TV funcionan; se corta al salir | Mismo corte | Idem |
| Noblex 32" Linux (~2018) | La lista no empieza; navegador sin probar | No se sabe si rechaza la lista, no accede, no reconoce MIME/contenedor o no decodifica. **No se asume que sea lo del LG** | `plain.m3u` y `compat.m3u`; el historial muestra IP, agente de usuario, ruta, estado y bytes de cada pedido y si cortó: sin pedidos = no llega al teléfono; pide lista pero no el flujo = rechaza la lista; pide el flujo y corta = no decodifica |

## Auditoría de la entrega HTTP
- M3U: `audio/x-mpegurl`, UTF‑8, `#EXTINF:-1`; nueva variante mínima CRLF. HLS: `application/vnd.apple.mpegurl`, versión 3, TARGETDURATION entero, segmentos `video/MP2T` con `Content-Length` y soporte de rangos, conexión persistente. TS en vivo: `video/MP2T`, sin longitud, cierre al terminar. MP4: `video/mp4` fragmentado, sin longitud (probablemente la causa de que navegadores viejos no inicien: esperan archivo con tamaño conocido).
- Video H.264 Main (Baseline en 480p/360p), nivel según resolución y FPS; audio AAC‑LC 48 kHz estéreo 128 kbit/s.
- Sin redirecciones. Sin dependencias externas en la página `/`.

## Receptor para el LG
Ver `OPENTV-LINK-ARQUITECTURA.md`: un televisor NetCast no ejecuta un receptor nativo; la vía real es un receptor HDMI (TV box o stick Android con OpenTV).
