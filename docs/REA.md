# ¿Necesitamos REA para continuar?

**Respuesta: NO.**

Nada de lo pendiente está bloqueado por falta de información que solo la ingeniería inversa pueda dar:

| Pendiente | ¿Qué lo bloquea? | ¿REA ayudaría? |
|---|---|---|
| IPTV con LG/SmartOne | Una prueba en el televisor (`compat.m3u`) | No: es una prueba, no una investigación. SmartOne en un televisor NetCast no es un APK analizable. |
| Wi-Fi Direct `connect()` error 0 | Tu registro de la etapa B y pruebas P1–P5 | No: el código fuente de AOSP (`WifiP2pManager`, `WifiDisplayController`) es público y alcanza para leer el ciclo de vida. |
| DLNA «archivo no compatible» / búfer del LG | Mediciones con el televisor | No por ahora: los perfiles que acepta el LG ya se encontraron por prueba. |
| Receptor Android TV de OpenTV Link | Desarrollo y dos equipos reales | No: el protocolo es propio y está documentado. |
| Emisión nativa desde la app | Restricción de Android (API de sistema) | No: se descartó por diseño (no hay API pública; las rutas por ocultas/privilegios están excluidas). |

## Cuándo pasaría a SÍ (contingencia concreta)
Solo si **todas** las pruebas de IPTV fallan (HLS y TS directo, varias calidades) y a la vez se comprueba que otra app instalada en tu
televisor/teléfono sí logra reproducir en vivo en ese LG. Ahí valdría observar, de forma autorizada y sobre tus propios equipos, qué
formato pide el televisor. Prompt para un chat independiente en ese caso:

> Objetivo: entender qué formato de medios en vivo acepta mi televisor LG 42LB5800-SB (NetCast) cuando la app «X» (que sí reproduce) le envía video, para que mi app OpenTV lo reproduzca con su propia implementación. Solo análisis de comportamiento observable en mi red doméstica y mis dispositivos: captura de tráfico HTTP/UPnP entre la app y el televisor (Wireshark en una PC con puerto espejo o hotspot), lectura de los documentos de descripción UPnP públicos del televisor y de las respuestas `GetProtocolInfo`.
> Entregables: (1) lista de pedidos/respuestas SOAP y cabeceras HTTP (`contentFeatures.dlna.org`, `Content-Type`, rangos), (2) el DIDL-Lite exacto enviado, (3) parámetros del flujo (`ffprobe`: perfil/nivel H.264, GOP, audio, contenedor), (4) una prueba reproducible que yo pueda ejecutar y una conclusión de qué cambio concreto hacer en OpenTV.
> No hacer: copiar código de la app, extraer claves o credenciales, evadir DRM, vulnerar el televisor ni servicios de terceros, ni analizar tráfico de otras personas.

No se recomienda iniciarlo ahora: primero la prueba de `compat.m3u`.
