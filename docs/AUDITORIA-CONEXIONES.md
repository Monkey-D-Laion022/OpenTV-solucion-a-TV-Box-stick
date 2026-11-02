# Auditoría de las tecnologías de conexión (6.4)

Método: lectura del código real; los defectos marcados «corregido» tienen cambio en esta versión; «sin probar» = sin prueba física.
Los archivos protegidos (`TvProjection`, `ProjectionService`, `connect/`, `ui/connect`, `ui/mirror`, `display/`, etc.) **no se tocaron**: sus hallazgos quedan como observaciones.

| Tecnología | Estado en el código | Hallazgos | Cambio en 6.4 | Pendiente / límite |
|---|---|---|---|---|
| Miracast nativo (espejo del sistema) | Intacto (protegido) | Funciona en el LG (≈1920×1080 a 30 Hz observado; FPS no medido por OpenTV) | Ninguno | No afirmar 60 FPS sin medición |
| Wi‑Fi Direct / WFD experimental | `miracast/` | `connect()` daba error 0 (hipótesis: canal nuevo sin redescubrir) | 6.3: secuencia limpiar → descubrir → esperar peer → conectar con reintentos (`P2pConnectPlanner`) | Efecto sobre el error 0 sin probar; la API `WifiP2pWfdInfo` es oculta (una app no puede anunciarse como fuente WFD) |
| OpenTV Link | `link/`, `proto/` | 6.3 era teléfono↔teléfono (**error de dirección**) | **Rediseñado teléfono → televisor/receptor HDMI**, receptor que escucha, ventana de emparejamiento, textos corregidos, prueba con sockets | Sin probar en equipos; ver `OPENTV-LINK-ARQUITECTURA.md` |
| IPTV (M3U) | `iptv/` | Corte ~2 s tras salir de Conexiones (Noblex). No hallé ninguna causa en la pantalla: el estado vive en `IptvController` (singleton), el codificador ya repite cuadros si la pantalla es estática | **Candados de CPU y Wi‑Fi** durante la emisión (`TransmissionLocks`, permiso normal `WAKE_LOCK`) y **motivo real de cada parada** visible («Última parada: …») | Causa del corte NO confirmada; si se repite, la pantalla dirá quién cortó (usuario, notificación, Android cerró la captura, servicio detenido) |
| HLS | `HlsSegmenter` | Latencia ≈ 3 segmentos (6–8 s); funciona en Noblex | Sin cambios | Más latencia que MP4 |
| MP4 fragmentado | `Fmp4Muxer` | Funciona en el navegador del Noblex | Sin cambios | LG sin probar |
| TS directo | `live.ts` | — | Sin cambios | LG sin probar |
| Página `/` | `LandingPage` | ES3, sin dependencias | Sin cambios | Probar en LG |
| DLNA | `connect/` (protegido) | Es **envío de contenido**, no espejo; latencia de búfer del TV | Ninguno | Rótulos ya lo distinguen |
| Espejo por navegador | `stream/BrowserMirror` | Una sola captura a la vez con IPTV/Link | Ninguno | Reemplazable por la página `/` con audio |
| Audio duplicado (celular + TV) | `iptv/` | Android no separa la salida local de la captura | **«Audio solo en TV» experimental**: baja el volumen y **mide** si la captura sigue; si se apaga, restaura | No confirma qué oye el TV; con auriculares/Bluetooth o apps que bloquean la captura puede no servir |
| Notificación general | `IptvService` + `TransmissionRegistry` | Cubre IPTV y Link | Sin cambios | Miracast/DLNA nativos tienen su propio aviso (protegidos) |

## Pendientes de la hoja de ruta original (no implementados)
- **Fase 5 original: modo control remoto desde otro celular.** Sigue pendiente. No es DLNA, ni espejo, ni OpenTV Link de video, ni un control local. Siguiente etapa concreta: tipo de trama `CONTROL` de navegación (teclas) por el canal cifrado ya existente, receptor que las convierte en eventos de foco de OpenTV.
- Control remoto avanzado, reproductor Media3 propio completo, Stremio: sin cambios.
