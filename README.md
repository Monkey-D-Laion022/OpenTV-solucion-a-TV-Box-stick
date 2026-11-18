# OpenTV

**Objetivo:** convertir un teléfono Android en una alternativa real a un TV Box/stick para televisores antiguos (por ejemplo LG NetCast 2014): lanzador tipo Android TV, reproductor, y varias formas de **llevar imagen y sonido del teléfono al televisor por Wi-Fi**.

> **Estado honesto (2026-11-16):** la app y el receptor PS3 se compilan en GitHub Actions. Las tecnologías de conexión **no están todavía aprobadas**: la primera ronda física (Redmi 14C + LG 42LB5800-SB + PS3) dejó resultados PARCIAL o FALLIDO, y las correcciones posteriores solo se probaron en PC/simulación. Ver [`docs/MATRIZ-CONECTIVIDAD.md`](docs/MATRIZ-CONECTIVIDAD.md).

## Arquitectura
- `app/`: app Android (Kotlin, Compose, Media3). Lanzador, apps, multimedia, conexiones, control remoto.
  - `iptv/`: captura de pantalla → H.264 por hardware → MPEG-TS/HLS (`StreamHub`, `QualityAdapter`, `FramePump`, `LandingPage` de `/tv`).
  - `media/dlna/`, `connect/`: descubrimiento SSDP y control UPnP/DLNA; `MethodAdvisor` recomienda un método (no oculta los demás).
  - `proto/`: emisor de OpenTV Link (ECDH P-256, HKDF, AES-256-GCM, código de emparejamiento).
  - Miracast/Wi-Fi Direct: diagnóstico y negociación RTSP (WFD).
  - Protegidos (no se tocan sin autorización): `ScreenResizer`, `PrefsResizeStore`, Shizuku/flujo de resolución, adaptación 16:9, `CatalogLogicTest.kt`; no se usa `wm density`.
- `receivers/ps3/`: **único receptor propio**: OpenTV Link para PlayStation 3 (C, ps3dev/PSL1GHT, mbedTLS, FAAD2). Núcleo compartido en `core/` (`ot_link`, `ot_ts`, `ot_h264`, `ot_mdns`). `test/link_host.c` es solo una herramienta interna de pruebas.
- `tools/`: simuladores y pruebas de estrés (`tv-sim` para navegador antiguo, `stress`).
- `docs/`: informes, protocolo y matriz.

## Dispositivos objetivo
Emisor: Android 7+ (probado: Redmi 14C, Android 16/HyperOS 3). Receptores: televisores con DLNA/navegador (probado: LG NetCast 2014), televisores con Miracast, PlayStation 3 (OpenTV Link), apps IPTV.

## Métodos de conexión (pantalla Conexiones)
| Apartado | Qué hace | Estado |
|---|---|---|
| 1. Receptores externos (DLNA/UPnP) | Detecta televisores en la red; envía una dirección o, con «Espejo de pantalla con sonido», la pantalla del teléfono | PARCIAL (ronda 3: conecta mejor pero 30 fps y lag con multitarea). Ahora intenta HLS 1080p60 primero si el TV no declara formatos; pantalla con diagnósticos como IPTV |
| 2. Servidor web local `/tv` | «Espejo con sonido en el navegador (/tv)»: usa el motor de IPTV; el TV abre una dirección corta; reproducción en navegador antiguo con recuperación automática y enlaces directos HLS (Plan B). El espejo por navegador anterior (MJPEG) se eliminó | FALLIDO en ronda 1 (se congelaba); corrección solo en simulación → PENDIENTE DE HARDWARE |
| 3. IPTV | Publica la pantalla como canal M3U/HLS; botón «Abrir el canal en otra app» (Web Video Caster u otro reproductor, por Intent) | PARCIAL alto (ronda 3: 10/10 a 1080p60 en el LG, incluso con otras apps; microcortes al cambiar de app) |
| Miracast / Cast | Espejo del sistema y diagnóstico WFD | PARCIAL: la 2.ª ronda informa RTP enviado a 1080p30 (el TV no declara 1080p60); falta confirmar que se vea imagen. Antes fallaba en M4 |
| OpenTV Link | Protocolo propio cifrado hacia un receptor OpenTV (PS3) | FALLIDO en ronda 3 (pantalla negra y congelamiento de la consola); se corrigieron bloqueos probables, sin probar en consola |
Detectar un dispositivo no es conectarse; la app muestra cada método por separado y el usuario elige.

## Receptor PS3: controles
Menú: ✕ emparejar · △ modo HTTP (respaldo sin cifrado) · □ datos en pantalla · **START = salir al XMB**. Durante una transmisión: **START = cortar y volver al menú**. El botón **PS** lo gestiona el sistema: la app no puede interceptarlo, solo atiende «Salir del juego» (callback `sysUtil`) y cierra limpio. **Sin probar en consola.**

## Compatibilidades y limitaciones conocidas
- LG NetCast: reproductor nativo sí abre HLS directo; el navegador (Chrome 22) no es confiable. No se asume MSE ni MP4 moderno.
- Miracast: se respeta lo que el TV declara; 1080p60 no se fuerza.
- Audio nativo: Android permite capturar solo el audio de apps que no lo bloqueen.
- PS3: 1080p60 no demostrado; por defecto pide 30 fps sobre 720p.
- La interfaz de conexiones y los cambios de `FramePump` no se compilaron fuera de GitHub Actions.

## Pruebas (separadas por tipo)
- **Automatizadas (PC):** 532 tests JVM; 14 pruebas de OpenTV Link (protocolo, fragmentación, manipulación); pruebas H.264/TS y de demux.
- **Compilación:** PKG PS3 compila y verifica (`make pkg verify`); el APK lo compila GitHub Actions (no compilado en el entorno de desarrollo).
- **Simulaciones (no equivalen a hardware):** `tools/tv-sim` (navegador antiguo), `HubStress`, `AdaptStress`.
- **Físicas:** solo la ronda 1 (ver [`docs/DIAGNOSTICO-RONDA1-REAL.md`](docs/DIAGNOSTICO-RONDA1-REAL.md)). Pendientes: ver [`docs/PRUEBAS-FISICAS.md`](docs/PRUEBAS-FISICAS.md).

## Generar el APK
`./gradlew :app:testDebugUnitTest :app:assembleDebug` (JDK 17+, Android SDK 34) → `app/build/outputs/apk/debug/app-debug.apk`. Sin computadora: GitHub Actions, workflow **Build**, artefacto `OpenTV-debug-apk`.

## Generar el PKG de PS3
GitHub Actions, trabajo `diagnose-ps3`: instala el SDK real (ps3dev), compila FAAD2 y mbedTLS, el receptor y el PKG; artefactos `opentv-ps3-pkg` y `opentv-ps3-debug`. Local: `cd receivers/ps3 && PS3DEV=<sdk> PSL1GHT=<sdk> make pkg verify` → `build/opentv-ps3.pkg`. Detalle en [`docs/PS3-BUILD-CI.md`](docs/PS3-BUILD-CI.md).

## Diagnósticos y registros
- PS3: `/dev_hdd0/game/OPENTV001/USRDIR/opentv.log` (etapas, SPS, contadores del decodificador) y el cartel «SIN IMAGEN» en pantalla.
- Miracast: pantalla de diagnóstico WFD de la app (copiar el texto de M3).
- Android: logcat / reporte de cierre.

## Pendiente y próximos pasos
Validar físicamente cada tecnología; conectar `MethodAdvisor` a la interfaz; medir FPS de captura/codificación/recepción/pantalla; sesiones largas. Otras funciones de la app se retoman cuando termine la etapa de conectividad.
