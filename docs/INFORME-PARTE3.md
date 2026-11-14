# OpenTV — Informe de la entrega integrada (Parte 3)

Leyenda: **[PC]** comprobado ejecutando en PC · **[SIM]** comprobado con simulación · **[HW]** pendiente de dispositivo real. Nada marcado [PC]/[SIM] garantiza el funcionamiento en hardware.

## Qué cambió
| Área | Cambio | Estado |
|---|---|---|
| PS3 | Se conserva todo lo previo (opentv.log, etapas, autodiagnóstico, orden de includes mbedTLS 3.6.2). PKG reconstruido con el SDK real (9/9 verificaciones). 14 pruebas de Link (incl. réplica del emisor Android, fragmentación de 1 byte, no emparejado). | [PC]/[SIM]; video/audio en consola [HW] |
| Miracast | Se conserva servicio en primer plano de MediaProjection y negociación H.264 por nivel. 31 pruebas WFD. | [PC]; captura/RTP real [HW] |
| DLNA | `SessionSupervisor.onPollFailed(now, streamAlive)`: si el TV sigue descargando el flujo (clientes>0) un fallo de consulta SOAP **no** se toma como corte ni dispara reenvío/baja de calidad. Cableado en `DlnaSession` e `IptvViewModel`. Prueba nueva. | [PC] lógica; TV real [HW] |
| `/tv` | Script ES3 reescrito: orden por `canPlayType` (MP4 «probably» antes que HLS «maybe»; TS solo si no fue descartado), vigilante de `currentTime` cada 2 s, reintento (2) y luego siguiente método, mensaje final con alternativas reales. Sin MSE ni APIs modernas. Simulador `tools/tv-sim/tv_sim.js` (7 comprobaciones). | [SIM]; LG NetCast real [HW] |
| IPTV | **Sin cambios.** Análisis y simulación de carga en `docs/IPTV-CARGA-PESADA.md`; propuestas A/B aparte. | análisis [SIM] |
| Conexión unificada | `connect/MethodAdvisor.kt`: une un TV que aparece por varios protocolos y elige el mejor método (OpenTV Link > Miracast > Cast > DLNA > navegador), degradando los fallidos. 9 pruebas. **La pantalla aún no lo usa**: no pude compilar Compose/Android aquí y no hago refactors de UI a ciegas. | lógica [PC]; UI pendiente |

## Pruebas ejecutadas
- JVM: 517 pruebas OK (todas las de lógica pura del repo; se excluye solo `OutputController.kt` que necesita Cast). [PC]
- Link PS3 (C + réplica Java del emisor): todas OK. [PC]
- `/tv` en Node: 7/7. [SIM]
- No se pudo compilar la app Android (sin SDK): **la compilación real la hace GitHub Actions**; los archivos Android tocados (`DlnaSession.kt`, `IptvViewModel.kt`, `WfdSourceStreamer.kt`) no fueron compilados.

## Tecnologías investigadas
- Viables y ya presentes: Miracast, DLNA, IPTV/HLS/TS/MP4, OpenTV Link v1 (PS3).
- No viables sin cambiar la premisa: AirPlay espejo (FairPlay), Cast hacia TVs no certificados.
- Evolución de OpenTV Link (receptores propios para Linux/PC/Android TV con el mismo handshake) es la línea con sentido; **no se agregó ningún protocolo nuevo** porque no hay un receptor adicional que probar.

## Protegidos: no tocados
`ScreenResizer`, `PrefsResizeStore`, Shizuku, lógica 16:9, `CatalogLogicTest.kt`. Sin `wm density`. No se ejecutaron workflows ni se publicó nada.

## Pruebas físicas pendientes (en este orden)
1. PS3: instalar el PKG, emparejar; si falla, mandar `opentv.log` (`/dev_hdd0/game/OPENTV001/USRDIR/opentv.log`).
2. LG 42LB5800 → `http://IP:8090/tv` con IPTV activo; ver en el teléfono el registro `/diag` (qué método reprodujo y si hubo cortes).
3. DLNA al LG: ver que no baje a 480p mientras el TV sigue reproduciendo.
4. Miracast al TV: confirmar paquetes RTP > 0.
5. IPTV con Stremio/Super TV: anotar `lateTicks` y cuadros capturados/mostrados, y si Stremio sale en negro (protección de contenido).
