# OpenTV 6.7 — informe de la iteración integral

Leyenda de la lista de control: **I** implementado · **C** compilado · **TA** probado automáticamente · **TH** probado en hardware real · **P** pendiente.
Compilado = solo lo que de verdad se compiló aquí: lógica Android en JVM (sin SDK), receptor PS3 con toolchain clang. **Ningún módulo Android se compiló contra el SDK real y ninguna función se probó en hardware.** El APK debe salir de tu GitHub Actions.

## A. Diagnóstico
- **Microcortes IPTV**: causa **no identificada**. Se agregó instrumentación (intervalos, cuadros tardíos, cola del codificador, tiempo de codificación por cuadro, entrega por segmento, causa por etapa) para decidir con datos del LG. No se afirma ninguna causa.
- **Miracast «Obteniendo señal»**: causa identificada por lectura del propio flujo: el diagnóstico de 6.6 **se detenía a propósito tras M3** (nunca enviaba M4/M5/PLAY ni video), por lo que el televisor no podía mostrar nada. No se sabe si el LG mostrará imagen tras M4→PLAY: no probado.
- **Wi‑Fi Direct error «0»**: significado sin confirmar; el diagnóstico registra el estado previo.
- **Fragmentación visual a 1080p**: no reproducida ni explicada; queda cubierta por la instrumentación y la adaptación (si el codificador o el transporte no sostienen 1080p, el perfil baja con causa registrada).

## B. Cuellos de botella (hipótesis medibles, no confirmadas)
Codificador (tiempo por cuadro y cola), bomba EGL (cuadros dibujados vs codificados), entrega HTTP/HLS al LG (KB y ms por segmento), receptor/reproductor del LG, temperatura. Cada uno tiene su métrica y el adaptador atribuye la causa.

## C. Archivos modificados (vs 6.6; ver `diff -rq`)
Nuevos: `iptv/QualityAdapter.kt`, `iptv/FrameTiming.kt`, `media/dlna/*` (6 archivos), `miracast/WfdSource.kt`, `miracast/WfdSourceStreamer.kt`, `proto/OtAcceptCaps` (en `OtAdaptation.kt`), tests (`QualityAdapterTest`, `FrameTimingTest`, `WfdSourceTest`, `DlnaLogicTest`, `DlnaIndependenceTest`, `OtAcceptCapsTest`), `receivers/ps3/**`, docs.
Modificados: `iptv/CapturePipeline, FpsGovernor, FramePump, HlsSegmenter, IptvController, IptvServer`, `stream/ScreenEncoder, BrowserMirror, MjpegHttpServer`, `miracast/Wfd*`, `link/LinkSenderController`, `ui/iptv/*`, `ui/browser/BrowserMirrorScreen`, `core/BuildInfo`, `P2pDiscoveryLogicTest` (permite el socket del streamer), `docs/PROGRESS.md`.

## D. Mejoras del codificador
Medición de tiempo de codificación por cuadro (`onEncodeLatency`), cola (`backlog = dibujados − codificados`), bitrate real, cambio de perfil en vivo (`CapturePipeline.switchPreset`: nuevo codificador + `VirtualDisplay.resize`, sin pedir otra captura), línea base forzable. Efecto en el LG: **sin probar**.

## E. Motor de adaptación (`QualityAdapter`)
Lógica pura con histéresis: escalera de perfiles por altura (1080/720/480, sin los de seguridad), baja por causa sostenida, sube con sondeos espaciados, sin inflar búfer, sin bajada permanente a 720p, 720p60 como referencia. Distingue causas codificador/red/transporte/receptor/reproducción/sincronía/temperatura (`lastCause`). 15+ tests en JVM. **No validado en hardware.**

## F. IPTV
Controlador integrado con el adaptador, `requestPreset`, diagnóstico `timing` ampliado. Componentes de rendimiento compartidos con Link y DLNA.

## G. DLNA (`media/dlna/`, independiente de `connect/`)
Descubrimiento SSDP, descripción UPnP, `GetProtocolInfo`, selección de perfil, `SessionSupervisor` (reintentos con espera, siguiente formato, indicador de inestabilidad, recuperaciones **sin ocultar** la inestabilidad, corte por fallos de sondeo), sin sesiones duplicadas, acople DLNA→perfil de captura, UI en IPTV. AAC directo. Test de independencia (el paquete no importa `connect/`). **Con el LG: sin probar.**

## H. Miracast / Wi‑Fi Direct
Dónde funciona (evidencia de sesiones anteriores): descubrimiento y conexión P2P hasta M3. Dónde falla: sin imagen por la detención tras M3. Corrección (basada en esa evidencia, no en conjeturas): flujo de fuente M4 (formato único, audio AAC, URL de presentación, puertos RTP) → M5 → SETUP/PLAY → M16/M7 (idr) → TEARDOWN y TS en RTP/UDP; interruptor en la pantalla de diagnóstico. **Si el LG acepta o no: pendiente de prueba.**

## I. Pruebas — ver `docs/NOTA-PRUEBAS-6.7.md` (505 JVM K2 y K1.9; 0 errores de stubs; C: TS 3/3, mDNS 10/10, Link 11/11).

## J. Estado de compilación
- Android: **no compilado contra el SDK** (sin acceso a los repositorios de Google). Solo: lógica pura en JVM + type-check con stubs.
- Receptor PS3: **compila, enlaza y empaqueta** con toolchain clang no oficial (`docs/PS3-TOOLCHAIN-CLANG.md`); **nunca ejecutado**.
- APK: **no existe** en este ZIP (ver arriba).

## K. Zona protegida
`ScreenResizer`, `PrefsResizeStore`, `ShizukuShell/Guide/*`, `ShellUserService`, `AspectRatio`, todo `display/`, `CatalogLogicTest`, `AspectRatioTest`, `build.gradle.kts` (raíz y app), `settings.gradle.kts`, `libs.versions.toml`, `AndroidManifest.xml`: **idénticos a 6.6 (`cmp`)**. Sin `ACCESS_FINE_LOCATION`. `.github` no tocado. Sin claves TMDB/RPDB en código ni docs.

## L. Pendiente de hardware (lo que solo tú puedes comprobar)
Todo lo de `docs/NOTA-PRUEBAS-6.7.md` §B y todo el receptor PS3 en consola (arranque, gcm/vdec/audio, sincronía, 720p60 y si 1080p30/60 es sostenible).

## M. Instrucciones Redmi 14C → LG 42LB5800
1. Misma red Wi‑Fi. 2. OpenTV → IPTV → «Iniciar emisión» (aceptar la captura). 3. En el LG abrir la dirección HLS/DLNA que muestra la pantalla, o usar «Buscar televisores DLNA» → «Enviar a». 4. Dejar correr 5 min en 720p60 y luego permitir 1080p; anotar la línea de tiempos y la causa de cada cambio. 5. Para Miracast: diagnóstico con «Transmitir» activado; anotar el último mensaje alcanzado.
PS3: `docs/PS3-RECEPTOR.md`.

## N. PROGRESS.md actualizado (sección 6.7).

---
# Lista de control de requisitos
| # | Requisito | I | C | TA | TH | P / motivo |
|---|---|---|---|---|---|---|
| 1 | Motor de calidad adaptativa central con histéresis | sí | JVM/stubs | sí (JVM) | no | efecto en LG/Redmi sin medir |
| 2 | 1080p60 priorizado si es sostenible; 720p60 pulido como referencia | sí | JVM/stubs | parcial (lógica) | no | requiere hardware |
| 3 | Sin inflar búfer; sin bajada permanente a 720p | sí | JVM/stubs | sí (lógica) | no | — |
| 4 | Distinguir causas (codificador/red/transporte/receptor/reproducción/sincronía) | sí | JVM/stubs | sí (lógica) | no | A/V sync solo medido en el receptor PS3; en Android no hay métrica de desincronía A/V |
| 5 | Instrumentación: tiempo de codificación, cola, bitrate real, latencias, cambios de perfil con motivo, recuperaciones DLNA, estado Miracast | sí | JVM/stubs | parcial | no | **P: CPU/GPU no se miden** (sin acceso fiable en Android 14 sin permisos adicionales); temperatura vía `ThermalProbe` |
| 6 | Marca diagnóstica B/N en esquina conservada | sí | — | — | validada antes | — |
| 7 | DLNA independiente en `media/dlna/`, AAC directo, supervisor | sí | JVM/stubs | sí | no | LG no probado |
| 8 | Recuperación exitosa no oculta la inestabilidad | sí | JVM/stubs | sí | no | — |
| 9 | Investigar fragmentación visual a 1080p | parcial | — | — | no | **P**: no reproducida; solo instrumentación |
| 10 | Miracast: identificar dónde funciona/falla y corregir con evidencia | sí | JVM/stubs | sí (formato mensajes) | no | LG no probado |
| 11 | Emisor integrado en la app OpenTV (no app separada) | sí (Link/IPTV/DLNA/Miracast dentro de OpenTV) | JVM/stubs | sí | no | — |
| 12 | Receptor PS3 completo (red, Link cifrado, decodificación, audio, pantalla, mando) | sí | **sí (clang)** | núcleo sí; capa PS3 no | **no** | **P**: nunca ejecutado en PS3; sin consola/emulador |
| 13 | PKG instalable | sí | **sí** | estructura (`pkg -l`) | **no** | **P**: no instalado en consola; requiere CFW/HEN |
| 14 | Link: aceptar topes del receptor (fps/bitrate en ACCEPT) | sí | JVM/stubs | sí | no | — |
| 15 | Link para no-Android: código en pantalla, reanudación | sí (en PS3) | sí | sí (PC) | no | capacidades extra en HELLO: no definidas más allá de `caps` v1 |
| 16 | Alternativa de navegador para LG NetCast | no | — | — | — | **P**: no implementado en esta iteración (el LG se cubre por HLS/DLNA ya existentes); sin evidencia de que el navegador NetCast sirva |
| 17 | ZIP único con proyecto, emisor, receptor PS3, PKG, docs, pruebas | sí | — | — | — | **APK ausente** (sin SDK) |
| 18 | APK compilado | no | no | no | no | **P**: imposible aquí (sin Android SDK); sale de GitHub Actions |
| 19 | Protección: ScreenResizer, PrefsResizeStore, Shizuku, 16:9, CatalogLogicTest | sin cambios | — | `cmp` idéntico | — | — |
| 20 | Sin root, sin `wm density`, sin FINE_LOCATION, claves fuera del código | sí | — | revisado | — | — |
| 21 | Histórico: F5 control remoto desde otro teléfono | no | — | — | — | **P**: función original aún sin hacer |
| 22 | Histórico: confirmar significado del error «0» de Wi‑Fi Direct | no | — | — | — | **P**: requiere correr el diagnóstico en el teléfono |
| 23 | Histórico: causa de microcortes con el LG | no | — | — | — | **P**: requiere medir con la nueva instrumentación |
| 24 | Histórico: compilación Android real | no | — | — | — | **P**: sin SDK aquí |
