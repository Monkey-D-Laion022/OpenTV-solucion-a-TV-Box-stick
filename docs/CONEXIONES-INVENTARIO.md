# Inventario de conexiones, descubrimiento y transmisión

Tres capas que no hay que mezclar: **descubrimiento** (¿qué hay en la red?), **transporte** (cómo viajan los bytes) y **protocolo multimedia** (qué significan). «Sin verificar» = no se probó en el Redmi 14C ni en el LG 42LB5800-SB. Lo que dicen las fuentes de Android viene de la documentación pública; lo del LG NetCast viene de tus pruebas físicas anteriores (DLNA con TS H.264 funciona; espejo nativo Miracast funciona).

## Matriz
Columnas: **App normal** = ¿una app sin privilegios puede usarlo? · **A/V** = ¿transporta audio y video? · **Espejo** = ¿duplica pantalla o solo medios?

| Tecnología | Capa | App normal / permisos | Hardware | A/V | Espejo | Latencia esperable | LG NetCast | IPTV / receptor propio | Prueba concreta | Decisión |
|---|---|---|---|---|---|---|---|---|---|---|
| Wi-Fi Direct (P2P) | transporte | Sí; `NEARBY_WIFI_DEVICES` (13+) / ubicación (≤12, **no autorizada**) | Wi-Fi con P2P | Sí (sockets) | No por sí solo | baja | Posible (Miracast usa P2P) | Receptor propio sin router | Diagnóstico A/B existente (`connect()` error 0 sin resolver) | Mantener el diagnóstico; no apostar |
| Wi-Fi local TCP/UDP | transporte | Sí; INTERNET | Wi-Fi | Sí | No | baja | Sí como cliente HTTP | **Base de IPTV, navegador y OpenTV Link** | Lo implementado | **Usar** |
| mDNS / DNS-SD | descubrimiento | Sí | — | No | No | — | Sin verificar | Descubrir receptores OpenTV | Anunciar `_opentv._tcp` | **Usar** (con OpenTV Link) |
| NSD de Android | descubrimiento | Sí (API de mDNS) | — | No | No | — | — | Idem | Idem | **Usar** (con OpenTV Link) |
| Multicast/broadcast | descubrimiento | Sí; bloqueo multicast | Wi-Fi sin aislamiento | No | No | — | SSDP/DLNA ya lo usa | SSDP ya implementado | Descubrimiento DLNA actual | Mantener |
| Wi-Fi Aware (NAN) | transporte | API 26+; permisos de Wi-Fi cercano | Solo algunos teléfonos; TVs casi no | Sí (sockets) | No | baja | No | Solo entre teléfonos compatibles | `isAvailable()` | Descartar por ahora |
| Bluetooth clásico | transporte | Sí; permisos Bluetooth | BT | Poco ancho de banda | No | media | No para video | No (video) | — | Descartar para video |
| Bluetooth LE | descubrimiento / control | Sí; permisos Bluetooth | BLE | No video | No | baja | No | Descubrir/emparejar receptores propios | — | Opcional futuro |
| Bluetooth PAN | transporte | No (anclaje requiere privilegios de sistema) | — | — | No | — | — | — | — | Descartar |
| Nearby Connections | transporte | Sí, pero con **Google Play Services** (dependencia cerrada); ambos lados deben usarla | Android | Sí (bytes) | No | baja | No | Solo entre equipos Android | — | Descartar (dependencia, no sirve al LG) |
| Google Cast | protocolo | Sí (SDK, ya incluido) | Receptor Cast | Sí | Medios; espejo vía panel del sistema | media | No | No | Ya integrado | Mantener |
| Miracast / Wi-Fi Display | protocolo | Iniciar desde app: **no** (API de sistema); sí abrir el panel del sistema | Emisor y TV | Sí | **Sí** | baja–media | **Funciona (tu prueba)** | No | Espejo nativo existente | **Mantener tal cual** |
| DLNA / UPnP | protocolo | Sí | TV con DMR | Sí (medios) | No: medios; en vivo con TS | media–alta (búfer del TV) | Sí (TS H.264); ajustes ya probados | No | Existente | Mantener |
| AirPlay | protocolo | Sí (sockets) | Receptor AirPlay | Sí | Sí | baja | No | No | — | Descartar (cerrado, sin receptor) |
| WebRTC | protocolo | Requiere librería nativa (~decenas de MB) | Navegador/app moderna | Sí | Sí | **la más baja** | Navegador del LG NetCast: improbable | Receptor Android TV | — | **Evaluar** en un receptor moderno; no ahora |
| HLS | protocolo | Sí | Reproductor HLS | Sí | Sí (como vivo) | alta (≈3 segmentos) | **Sin verificar** | **IPTV (implementado)** | `compat.m3u` | **Usar** |
| MPEG-DASH | protocolo | Sí | Reproductor DASH | Sí | Sí | alta | Poco probable | No | — | Descartar |
| RTSP/RTP | protocolo | Sí (no hay servidor en Android; habría que escribirlo) | VLC/cámaras | Sí | Sí | baja | Sin verificar | Posible con apps IPTV | — | Opcional |
| SRT / RIST | protocolo | Requiere librería nativa | Receptores específicos | Sí | Sí | baja | No | No | — | Descartar por ahora |
| MPEG-TS sobre HTTP | protocolo | Sí | Casi cualquiera | Sí | Sí | media | **Sí** (DLNA actual) | **IPTV `live.ts` y OpenTV Link** | Lo implementado | **Usar** |
| MPEG-TS sobre UDP/multicast | transporte | Sí | Apps IPTV | Sí | Sí | baja | Sin verificar | Posible | — | Opcional (frágil en Wi-Fi) |
| HTTP progresivo / MJPEG | protocolo | Sí | Navegador | Imagen sin sonido | Sí | baja–media | Sí (navegador) | No | Existente | Mantener |
| WebSocket | control | Sí | Navegador/app | Datos | — | baja | Sin verificar | Control/señalización de un receptor web | — | Opcional |
| QUIC | transporte | Solo con Cronet/librería | — | — | — | sin ventaja en LAN | No | No | — | Descartar |
| USB / OTG, HDMI externo | físico | Depende; salida de video por USB-C solo en teléfonos con DisplayPort | Adaptador y teléfono compatible | Sí | Sí | mínima | Entra por HDMI | No | Probar con adaptador | Informativo (el Redmi 14C: sin verificar) |
| Ethernet por adaptador / USB tethering | físico | Sí (el sistema lo gestiona) | Adaptador | — | — | — | — | Mejora la red | — | Informativo |

## Arquitecturas que valen la pena
1. **Espejo nativo Miracast** (ya funciona): se conserva sin cambios.
2. **IPTV (HLS + TS directo por HTTP)**: implementada; falta la prueba con el LG/SmartOne.
3. **OpenTV Link (TCP + MPEG-TS cifrado + DNS-SD)**: protocolo listo y probado en loopback entre dos implementaciones; falta emisor/receptor Android.
4. **DLNA en vivo** y **espejo por navegador**: ya existen; mejorarlos exige pruebas en el televisor (ver límites abajo).
5. **WebRTC** solo si aparece un receptor moderno que lo justifique.

## Lo que NO se pudo hacer en esta entrega (sin hardware ni Android SDK)
- **Perfeccionar DLNA y navegador**: sin el LG delante cualquier ajuste sería una apuesta; no se cambió código de esos módulos. Quedan por medir: resolución/FPS efectivos, latencia, estabilidad prolongada, reconexión.
- **Wi-Fi Direct `connect()` error 0**: sin diagnóstico nuevo (hace falta tu registro de «Fases» con la etapa B). No está resuelto.
- **Emisión nativa desde la app**: Android no permite iniciar Miracast desde una app normal (solo el panel del sistema); lo automatizable ya está (abrir el panel).
- 1080p60: no probado; no se promete.
