# Registro de avances

## Fase 1: proyecto base

### Archivos creados
- Gradle: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`,
  `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/proguard-rules.pro`,
  `gradlew`, `gradlew.bat`, `gradle/wrapper/*` (Gradle 8.9), `.gitignore`.
- Android: `AndroidManifest.xml`, `res/values` (strings, colors, themes),
  `res/drawable` (`ic_launcher`, `banner`).
- Kotlin (`com.opentv.app`): `MainActivity`, `core/AspectRatio`, `device/DeviceInfo`,
  `navigation/{Routes,OpenTvNavHost}`, `ui/theme/*`, `ui/components/*`,
  `ui/home/*`, `ui/device/DeviceInfoScreen`, `ui/pending/PendingScreen`, `ui/settings/SettingsScreen`.
- Pruebas: `AspectRatioTest` (5), `RoutesTest` (4).

### Versiones elegidas (SIN verificar contra los repositorios)
AGP 8.5.2 · Kotlin 1.9.24 · Compose compiler 1.5.14 · Compose BOM 2024.06.00 · Activity 1.9.1 ·
Navigation 2.7.7 · Lifecycle 2.8.4 · Coroutines 1.8.1 · Gradle 8.9.
Combinación elegida por compatibilidad conocida (Kotlin 1.9.24 ↔ compilador Compose 1.5.14).
No se pudo resolver ninguna desde este entorno.

### Verificación real
| Qué | Resultado |
|---|---|
| Pruebas JUnit de `AspectRatio` y `Routes` (9) | **Pasan**, compiladas con Kotlin 2.0.21 (el del Gradle local) y ejecutadas con JUnit 4.13.2 en la JVM. No es la toolchain del proyecto (Kotlin 1.9.24). |
| Todos los XML (manifest, recursos) | Bien formados. |
| `./gradlew assembleDebug` | **NO ejecutado.** El entorno no tiene Android SDK y el proxy bloquea `dl.google.com`, `maven.google.com`, Maven Central y el Gradle Plugin Portal (403). |
| Código Compose/Android (UI, MainActivity, DeviceInfo) | **NO compilado ni probado.** Escrito con cuidado, pero puede tener errores de compilación. |
| Pruebas en teléfono o TV | **Ninguna.** No hay dispositivo físico. |
| APK | **No generado.** |

### Errores / riesgos pendientes
1. Primer `assembleDebug` puede revelar errores de compilación o de versiones: corregir y repetir.
2. Foco inicial con `LaunchedEffect` + `requestFocus()`: comprobar en dispositivo que no falle al
   abrir con teclado, mando y toque.
3. Comprobar en un TV/pantalla externa que el margen del 5 % y la muesca se ven bien.
4. Comprobar que el ícono vectorial del launcher se ve correcto en teléfonos con Android 7.

### Pasos exactos para continuar
1. En una máquina con Android SDK y red: `./gradlew :app:testDebugUnitTest :app:assembleDebug`.
2. Pegar aquí cualquier error de Gradle; se corrige antes de pasar de fase.
3. Instalar el APK y probar con teclado/mando: flechas, Enter, Escape.
4. Fase 2 (Launcher): apps instaladas con `<queries>` para `ACTION_MAIN`/`LAUNCHER`, búsqueda,
   favoritos, categoría `HOME` y apertura con `ActivityOptions`.


---

## Fase 2: launcher

### Archivos nuevos o modificados
- Datos: `apps/{InstalledApp,AppsRepository,FavoritesStore,AppLauncher,AppLaunchController,
  AppListLogic,AppIconLoader,AppsViewModel}`, `core/OrientationPolicy`, `AppContainer`, `OpenTvApp`.
- Launcher: `launcher/DefaultLauncherHelper`.
- UI: `ui/apps/{AppsScreen,AppTile,LaunchFeedback}`, `ui/components/SearchField`,
  `ui/components/FocusableSurface` (pulsación prolongada), `ui/components/TvComponents`
  (`horizontalPadding`), `ui/home/{HomeScreen,HomeViewModel}` (fila «Tus favoritas»),
  `ui/settings/SettingsScreen` (launcher predeterminado), `navigation/*`, `MainActivity`.
- Manifest: `<queries>` (LAUNCHER, LEANBACK_LAUNCHER, HOME), `OpenTvApp`, `launchMode=singleTask`,
  alias `.HomeAlias` con categoría HOME. Sin permisos nuevos.
- Compilación: `.github/workflows/build.yml` (GitHub Actions, para quien no tiene computadora).
- Pruebas: `AppListLogicTest`, `AppLaunchControllerTest`, `OrientationPolicyTest`.

### Incidente de la sesión (corregido)
Esta fase ya estaba escrita cuando se retomó. Se sobrescribieron por error 5 archivos con otra
versión y se crearon 4 duplicados. Se resolvió conservando el diseño original: se borraron los
duplicados y se reescribieron los 5 archivos para que coincidan con sus consumidores. Después se
verificó que no queden imports sin declaración ni tipos duplicados.

### Verificación real
| Qué | Resultado |
|---|---|
| Pruebas de lógica pura (27: aspecto, rutas, orientación, búsqueda/orden, controlador de apertura) | **Pasan** en la JVM, con Kotlin 2.0.21 (no el 1.9.24 del proyecto). |
| Imports internos, tipos duplicados, símbolos eliminados | **Auditados con script**: sin problemas. |
| XML del manifest y recursos, YAML del workflow | Válidos. |
| Compilación Gradle, UI Compose, `PackageManager`, alias HOME | **NO ejecutados.** Sin Android SDK ni red a Google/Maven en el entorno. |
| Workflow de GitHub Actions | **No ejecutado.** |
| Pruebas en teléfono o TV | **Ninguna.** |
| APK | **No generado.** |

Los fallos al abrir apps (no encontrada, denegada, error) se prueban con un doble del lanzador; el
`AppLauncher` real que llama a Android no tiene prueba.

### Riesgos conocidos
1. **Pulsación prolongada con mando**: `combinedClickable` puede no detectar mantener OK en un
   D-pad. Las teclas Menú y F siempre funcionan; en táctil la pulsación prolongada sí.
   Si en un mando no hay Menú, hay que añadir un botón visible de favorita.
2. **Foco inicial** en la grilla: usa un cuadro de espera + `requestFocus` dentro de `runCatching`;
   si falla, no hay foco inicial (no se cierra la app), pero hay que comprobarlo en dispositivo.
3. **Botón Inicio como launcher**: depende de `singleTask` + intent nuevo; comprobar que vuelve
   al inicio desde una subpantalla.
4. Algunos fabricantes ocultan `ACTION_HOME_SETTINGS`: la app lo detecta y muestra instrucciones.
5. Cualquier error de compilación de Gradle (versiones de dependencias sin verificar).

### Pasos exactos para continuar
1. Subir el proyecto a GitHub y mirar el resultado de la pestaña Actions (ver README).
2. Pegar el primer error de compilación para corregirlo.
3. Instalar el APK y probar: abrir apps, buscar, marcar favoritas, apps solo verticales, elegir
   OpenTV como launcher y volver al original.
4. Fase 3: conectividad (Miracast vía ajustes del sistema, Cast, DLNA) y pantalla «Conectar TV».


---

## Fase 3: conectividad (Conectar TV)

### Archivos
- `connect/`: `Capabilities` (estados y evaluador), `MirroringGuide`, `Troubleshooting`, `Ssdp`,
  `SsdpSearch` (UDP), `UpnpDescription`, `HttpMessages`, `LanHttp` (socket), `LanAddress` (política
  de red local), `DlnaSoap`, `DlnaController`, `SystemCastLauncher`, `ExternalDisplayMonitor`,
  `CapabilityDetector`, `CastDiscovery`, `CastOptionsProvider`, `ConnectViewModel`.
- UI: `ui/connect/ConnectTvScreen`; cambios en `HomeScreen`/`HomeViewModel` (chip real de pantalla
  externa), `DeviceInfoScreen`, `Routes` (+`CONNECT`, −`PendingFeature.CONNECT_TV`), `OpenTvNavHost`,
  `AppContainer`, `Color` (+`Success`).
- Manifest: permisos `INTERNET` y `ACCESS_NETWORK_STATE`, `<queries>` de `CAST_SETTINGS`, meta-data
  del SDK de Cast. Gradle: `play-services-cast-framework` 21.4.0 y `androidx.mediarouter` 1.6.0.
- Pruebas nuevas: `CapabilityEvaluatorTest`, `MirroringGuideTest`, `SsdpTest`, `UpnpDescriptionTest`,
  `HttpMessagesTest`, `LanAddressTest`, `DlnaSoapTest`, `DlnaControllerTest`, `SocketLanHttpTest`,
  `TroubleshootingTest`.

### Decisiones y desvíos respecto del pedido
- **Miracast sin conexión desde la app**: no existe API pública para escanear ni conectar. Se
  detecta la pantalla externa y se abre el ajuste del sistema; no se inventa nada más.
- **Cast solo detecta** (el envío usa el reproductor, Fase 4). **Wi‑Fi Direct solo detecta**
  (el canal real entre teléfonos es de la Fase 5). Ambos figuran así en pantalla.
- **DLNA con sockets propios**, sin `usesCleartextTraffic`: solo IPv4 privadas, el `LOCATION` debe
  venir del mismo equipo que respondió, XML sin DOCTYPE/ENTITY, respuestas con tope de tamaño y tiempo.

### Incidente de la sesión (corregido)
Había una capa pura de Fase 3 ya escrita. Se crearon por error archivos paralelos que chocaban con
ella; se borraron y se adoptó el diseño existente. Tres pruebas propias tenían expectativas mal
escritas y se corrigieron (texto de mensaje, carácter inválido en XML 1.0, una tilde).

### Verificación real
| Qué | Resultado |
|---|---|
| 94 pruebas de lógica pura (incluye SSDP, UPnP, SOAP, controlador DLNA con dobles y socket real por loopback) | **Pasan** en la JVM con Kotlin 2.0.21 (no el 1.9.24 del proyecto). |
| Imports internos, tipos duplicados, miembros de `AppContainer` usados | **Auditados con script**: sin problemas. |
| XML del manifest | Válido. |
| Compilación Gradle de la capa Android, Compose, SDK de Cast, versiones 21.4.0/1.6.0 | **NO ejecutado.** Sin Android SDK ni acceso a Google/Maven en el entorno. |
| Búsqueda SSDP/DLNA contra un TV real, Cast real, pantalla externa real | **Ninguna prueba en hardware.** |
| Workflow de GitHub Actions | **No ejecutado.** |

### Riesgos conocidos
1. Versiones del SDK de Cast y mediarouter sin verificar; si Gradle no las resuelve, ajustar.
2. SSDP: algunos routers/teléfonos filtran multicast; las respuestas son unicast y no se usa
   `MulticastLock`. Si en un equipo no aparece ningún TV con DLNA activo, probar a agregarlo.
3. `isOnLocalNetwork` mira solo la red activa: un Wi‑Fi sin internet puede figurar como «sin red».
4. Varios TV DLNA exigen metadatos DIDL‑Lite o tipos MIME concretos; hoy se envía el enlace sin
   metadatos y el TV puede rechazarlo (el mensaje del TV se muestra tal cual).
5. El foco con mando en la pantalla larga y el botón «Desconectar» solo se verifican en dispositivo.

### Pasos exactos para continuar
1. Subir a GitHub y mirar Actions; pegar el primer error de compilación.
2. En un teléfono: abrir «Conectar TV», comprobar estados, abrir la transmisión del sistema, buscar
   TV por DLNA y enviar un enlace de prueba de tu red local.
3. Fase 4: reproductor Media3, selector de documentos, envío por Cast y servir archivos locales por DLNA.


---

## Fase 4 (en curso)

### Módulo 1: reproductor Media3
- `player/{PlayableUrl,VideoFit,PlayerViewModel}`, `ui/player/PlayerScreen`; ruta `PLAYER` real (sale de `PendingFeature`).
- Dependencias: Media3 1.4.1 (exoplayer, hls, dash, ui), sin verificar contra el repositorio.
- Manifest: `usesCleartextTraffic="true"` (para enlaces http de la red local). Es una decisión de seguridad a revisar.
- Verificado: 101 pruebas de lógica pura (7 nuevas). NO verificado: compilación, reproducción real, `PlayerView` con el tema Material del sistema.

### Módulo 2: pantalla externa y envío directo
- `connect/{CastSender,CastMime}`: toque en un dispositivo Cast → `MediaRouter.selectRoute` → sesión → `RemoteMediaClient.load` con el enlace. «Desconectar Cast» cierra la sesión.
- `ui/player/ExternalPresentation`: con pantalla externa, «Ver en la pantalla externa» dibuja el video en una `Presentation` a la resolución nativa del TV; el celular queda como control (Pausar/Reanudar).
- Enlace compartido entre reproductor y «Conectar TV» (`AppContainer.sharedLink`); botón «Enviar a un TV» en el reproductor.
- Verificado: 103 pruebas de lógica pura. NO verificado: compilación, API de Cast (`SessionManagerListener`, `selectRoute`), `Presentation` en el Redmi 14C, reproducción en un TV real.
- Límites: Cast necesita que el receptor pueda abrir el enlace (CORS en HLS/DASH, enlaces vencidos); la `Presentation` solo funciona si Android informa una pantalla externa y puede reemplazar al espejo del sistema, algo que solo se comprueba en el equipo.

### Módulo 2 (rehecho): selector de TV propio
Corrige un reclamo real: las tarjetas eran texto y no había ícono ni selector.
- `ui/connect/CastIcon` (ícono dibujado a mano + `CastButton`), `ui/connect/CastPickerDialog` (lista unificada de TV Cast y DLNA; al tocar uno envía el enlace desde OpenTV; botones «Buscar de nuevo» y «Detener / desconectar»).
- El botón de Cast está en el inicio, en el reproductor y en «Conectar TV». Las tarjetas de Cast/DLNA/Miracast de «Conectar TV» son clicables.
- `connect/TvTargets`, `CastDevice` (archivo propio), `ConnectViewModel.send/stopAll`, `core/BuildInfo` (etiqueta «Fase 4 · módulo 2 (selector de Cast propio)» visible en el inicio y en «Dispositivo»).
- Verificado: 106 pruebas de lógica pura; imports auditados. NO verificado: compilación, el diálogo con D-pad/táctil, Cast y DLNA contra un TV real, `Presentation` en el Redmi 14C.
- Miracast: sigue pasando por el ajuste del sistema; no existe API pública para conectarlo desde una app.

### Módulo 3A: TV elegido y launcher en filas
- Tocar un TV (Cast o DLNA) en el selector lo elige como salida: con Cast abre la sesión en el momento (`CastSender.connect`); con DLNA queda como destino. `connect/OutputController`.
- Con un TV elegido, «Reproducir» envía el enlace allí (botones «Detener en el TV» / «Ver aquí en el celular»).
- Se quitó la caja de enlace del selector y de «Conectar TV». La entrada manual de enlaces quedó en Configuración › Herramientas (`ui/settings/ToolsScreen`); el reproductor ya no está en el inicio.
- Inicio en filas horizontales (`LazyRow`): Aplicaciones (favoritas primero + «Ver todas»), Multimedia, Ajustes.
- Etiqueta de versión: «Fase 4 · módulo 3A».
- Verificado: pruebas de lógica pura e imports. NO verificado: compilación, Cast/DLNA contra el LG, foco con D-pad en las filas.
- PENDIENTE (módulo 3B): proyectar el launcher en la pantalla externa con `Presentation` y control remoto desde el celular. Límite conocido: las apps de terceros se abren en la pantalla del teléfono, no en el TV.

### Módulo 3B: proyección inalámbrica propia 16:9 y fila de apps con «+»
Pilar fijado por el usuario: nada de HDMI/MHL ni «Emitir» del sistema; todo por Wi‑Fi y propio de OpenTV.
- Fila «Aplicaciones» del inicio: solo las favoritas fijadas + tarjeta «+» (`ui/apps/AppPickerDialog`) para agregar/quitar. «Todas las apps» pasó a la fila Ajustes.
- Proyección (`stream/`): pantalla virtual propia de 1920×1080 (`DisplayManager.createVirtualDisplay`, sin MediaProjection) donde se dibuja la interfaz del TV (`ui/remote/TvPresentation` + `TvLauncherContent`) → `ScreenEncoder` (MediaCodec H.264, repite cuadro si no hay cambios) → `TsMuxer` (MPEG-TS propio) → `StreamHub` + `HttpStreamServer` (solo IPv4 privadas, ruta con código aleatorio, máx. 2 clientes) → DLNA `SetAVTransportURI` con metadatos DIDL-Lite (`DidlLite`) y `Play`. Orquesta `TvProjection`.
- Control remoto táctil (`ui/remote/RemoteScreen`, `RemoteViewModel`, `stream/TvNavigation`): cruceta, OK e Inicio mueven el cursor de la interfaz que se ve en el TV.
- DIDL-Lite también se envía ahora con cualquier enlace DLNA (muchos LG lo exigen).
- Verificado en la JVM: 129 pruebas (TsMuxer: ida y vuelta del video para 13 tamaños, CRC de PAT/PMT, contadores, PCR, PTS; servidor HTTP por loopback; hub; navegación; DIDL). NO verificado: compilación; MediaCodec, pantalla virtual y `Presentation` en el Redmi 14C; que el LG 42LB5800 (NetCast) acepte un MPEG-TS en vivo por DLNA; retraso real; Compose dentro de la `Presentation`.
- Límites: retraso de varios segundos (búfer del TV); sin audio; solo DLNA (no Cast); las apps de terceros se abren en el teléfono; si OpenTV pasa a segundo plano la imagen del TV puede congelarse (falta un servicio en primer plano).

### Módulo 3C: compatibilidad con TV viejos y pantalla negra
Reporte real: LG 42LB5800 (NetCast) dijo «Archivo no compatible»; un TV Linux de 32" aceptó pero quedó en negro.
- Causa probable LG: se anunciaba `MPEG_TS_HD_NA_ISO` (perfil MPEG-2) con video H.264, y el TS no tenía audio. Ahora: perfil `AVC_TS_HD_NA_ISO`, pista de audio AAC muda (`SilentAudioEncoder`, `Adts`, `TsMuxer(withAudio)`), H.264 Main nivel 4.0 (3.1 en 720p) a bitrate constante, `Accept-Ranges: none`, `realTimeInfo`.
- Causa probable pantalla negra: el codificador solo recibía cuadros si la ventana de la pantalla virtual dibujaba; si no dibujaba, no salía nada. Ahora `ViewFrameFeeder` dibuja la interfaz directo sobre el codificador a 15 cuadros/s (o un cartel «OpenTV» si la interfaz no está visible). La pantalla virtual solo aloja la ventana (su imagen se descarta en un `ImageReader`).
- 5 formatos seleccionables en la app (`StreamProfile`, botón «Cambiar formato»): H.264 con perfil DLNA, TS genérico, MIME `video/vnd.dlna.mpeg-tts`, sin metadatos DIDL, 720p.
- Diagnóstico en pantalla (`ProjectionStats`): cuadros dibujados/codificados, bytes enviados, conexiones y último pedido del TV (método + user-agent), si la interfaz se ve, y una pista de dónde está el corte.
- El TV pide un fotograma clave al conectarse (`StreamHub.onSubscribe`).
- Verificado en la JVM: 143 pruebas (audio TS ida y vuelta, PMT con/sin audio y CRC, ADTS, perfiles, cabeceras HTTP por perfil, registro del pedido, estadísticas). NO verificado: compilación; MediaCodec AAC/H.264; dibujar la vista Compose con `lockHardwareCanvas`; que cualquiera de los 5 formatos lo acepte el LG o el TV de 32".

### Módulo 3C-2: corte al segundo de reproducción
Reporte real: ambos TV abren el formato (sin error) y muestran la interfaz, pero tras ~1 s salen de la reproducción, en los 5 formatos.
- Causa probable (hipótesis, no confirmada): con la interfaz quieta el codificador casi no entrega datos (unos kbps + audio mudo); el TV vacía su búfer y abandona. Corrección: relleno con paquetes nulos TS (`TsPadding`, PID 0x1FFF) hasta un caudal mínimo constante de 2 Mbps, emitido por un hilo (`TvProjection.startPadder`).
- El dibujo ya no se detiene ante un error (`ViewFrameFeeder` cuenta errores y reintenta); el codificador repite el último cuadro si se frena el dibujo; TCP_NODELAY y búfer de envío de 256 KB.
- Diagnóstico nuevo en el teléfono: cómo terminó la última conexión («el TV cerró…» u «OpenTV detuvo…», con segundos y KB), errores al dibujar y su texto.
- Verificado en la JVM: 146 pruebas (relleno nulo válido, cálculo de relleno, contador del hub). NO verificado: compilación y que el relleno corrija el corte.
- Pendiente probable: servicio en primer plano (si Android pone la app en segundo plano al cambiar de pantalla, el dibujo se frena).

### Módulo 3C-3: servicio en primer plano e historial de red
Reporte real: el relleno a 2 Mbps no cambió nada; sigue 1 s de imagen y salida del TV en ambos televisores y todos los formatos. La hipótesis del caudal queda debilitada (no descartada: no se vio el bloque de estado).
- `stream/ProjectionService`: servicio en primer plano (tipo `mediaPlayback`, permisos normales `FOREGROUND_SERVICE` y `FOREGROUND_SERVICE_MEDIA_PLAYBACK`) que vive mientras dura la proyección; lo inicia y detiene `TvProjection`.
- `HttpStreamServer.history`: últimos 10 eventos con tiempo (conexión, pedido, cierre y quién cortó); se muestra en el estado de la pantalla de proyección.
- Verificado en la JVM: 147 pruebas. NO verificado: compilación, el servicio en el Redmi 14C ni la causa del corte.

### Módulo 3C-4: diagnóstico siempre visible y prueba aislada
Reporte real: con el 3C-3 el bloque de estado no mostró nada. Causas halladas: el bloque solo se dibujaba en el estado «proyectando» y la etiqueta de versión no se había actualizado desde el 3C (no había forma de confirmar el APK instalado). Revisado: nada dentro de la app detiene la proyección por su cuenta (solo el botón, OK en la fila «Detener» o cerrar la app).
- El bloque de estado se ve siempre que haya datos, también después de detenerse o fallar, con la versión instalada, la fase, el historial combinado de la app (inicio, pantalla virtual, envío al TV, respuesta del TV, quién detiene) y de la red (conexión, pedido, cierre) ordenado por tiempo, y un botón «Copiar diagnóstico».
- «Prueba sin interfaz»: envía una barra que se mueve y un contador en lugar de la interfaz de OpenTV. Si el TV también corta, el problema no está en dibujar la interfaz.
- Etiqueta: «módulo 3C-4 (diagnóstico siempre visible)».
- Verificado en la JVM: 148 pruebas. NO verificado: compilación ni la causa del corte.

### Módulo 3C-5: el corte lo causaba OpenTV (causa confirmada con datos)
Registro real en la LG NetCast: el TV hizo GET del video y leyó más de 500 KB, pero ~2 s después el comando `Play` terminó en «Unreachable» (el TV no contesta el SOAP mientras abre el flujo) y OpenTV, al tomarlo como fallo, se detuvo a sí mismo. Las hipótesis anteriores (caudal, cierre del socket, segundo plano) no eran la causa; el relleno nulo y el servicio en primer plano quedan, son inofensivos.
- `stream/PlayOutcome`: si el TV no confirma el Play pero ya descarga el video, se sigue transmitiendo; solo se corta si no confirma Y no pide el video (con 4 s de gracia).
- Etiqueta: «módulo 3C-5 (Play tolerante)».
- Verificado en la JVM: 151 pruebas. NO verificado: compilación, y que la LG sostenga la reproducción después de esto; la TV de 32" no mandó registro.

### Módulo 3D: latencia y verificación de 1920×1080
Confirmado en la LG NetCast: con el Play tolerante (3C-5) la transmisión es continua y estable; la interfaz responde al control remoto. Queda el retraso entre pulsar y ver el cambio.
- `stream/StreamLatency` (botón «Latencia» en la pantalla de proyección): «Estable» (lo probado) y «Baja latencia» (30 cuadros/s, márgenes de tiempo del flujo reducidos: PTS +0,2 s y PCR 0,1 s antes en lugar de 0,7/0,5 s, caudal mínimo 4 Mbps, `latency`/`priority` del codificador). `TsMuxer` recibe los márgenes por parámetro (valores por defecto sin cambios).
- El piso del retraso es el búfer interno del TV: no se controla desde la app. El GOP ya era de 1 s; `KEY_INTRA_REFRESH` no se usó porque el retraso no viene de los fotogramas clave.
- El lienzo ya es exactamente 1920×1080 (pantalla virtual, ventana y codificador); la «Prueba sin interfaz» ahora dibuja un marco naranja en los 4 bordes y «1920×1080» para ver en el TV si recorta o deja franjas (overscan/zoom del propio TV).
- Etiqueta: «módulo 3D (latencia + marco de prueba)». Verificado en la JVM: 153 pruebas. NO verificado: compilación, cuánto baja el retraso real, ni que el modo de baja latencia lo acepte la LG.
- Espejo del teléfono (MediaProjection + Shizuku/`wm size`): diseñado, no implementado (ver mensaje al usuario).

### Módulo 3E: espejo de pantalla y latencia por partes
Reporte real (módulo 3D): «Estable» ~3–4 s de retraso; «Baja latencia» (30 fps + 4 Mbps + márgenes cortos, todo junto) empeoró a ~5–6 s. Como se cambiaron varias cosas a la vez, no se sabe cuál empeoró.
- Latencia (`stream/StreamLatency`): se eliminó «Baja latencia». Ahora 3 variantes, cada una cambia UNA cosa respecto de «Estable»: «Cola corta, sin relleno» (sin paquetes nulos, búfer de envío del socket 32 KB, cola máxima 192 KB por cliente), «Margen de tiempo corto» (PTS/PCR más cercanos). 15 fps en todas.
- `StreamHub`: cola acotada también por bytes; si el TV se atrasa se descarta lo viejo y se retoma en el próximo fotograma clave (el retraso no crece; la imagen puede saltar). `HttpStreamServer`: tamaño del búfer de envío configurable.
- No se tocó el GOP (1 s): `KEY_I_FRAME_INTERVAL` es entero en segundos y 0 significa todo fotogramas clave (mucho más caudal). El empaquetado TS agrega ~4 % de cabeceras: no es fuente de retraso.
- Espejo (`TvProjection.start(..., mirror = MirrorRequest)`): MediaProjection (permiso del sistema en cada sesión) → pantalla virtual `AUTO_MIRROR` de 1920×1080 directo al codificador → misma tubería DLNA. El sistema encaja la pantalla sin deformarla (franjas si la proporción no es 16:9). `ProjectionService` declara además el tipo `mediaProjection`, espera estar en primer plano antes de capturar (requisito de Android 14), registra el callback antes de crear la pantalla virtual y su notificación tiene el botón «Detener». Apps con DRM salen negras.
- NO implementado a propósito: `wm size` vía Shizuku/ADB. Desde este entorno no se puede verificar la librería de Shizuku (Maven Central bloqueado) ni su API actual; agregarla a ciegas puede romper la compilación de todo. Queda para un módulo aparte.
- Etiqueta: «módulo 3E (espejo + latencia por partes)». Verificado en la JVM: 156 pruebas (cola por bytes, variantes de latencia). NO verificado: compilación, MediaProjection ni el servicio en Android 14 en el Redmi 14C, ni cuánto baja el retraso.

### Módulo 3F: 16:9 forzado con Shizuku y restauración
Reporte real (3E): espejo por MediaProjection estable en la LG; con el teléfono 20:9 aparecen franjas; el retraso por el búfer del TV sigue.
- `display/ScreenResizer` (lógica pura, 9 pruebas): lee `wm size`/`wm density`, calcula 16:9 vertical (720×1640 → 1080×1920 y densidad 320 → 480 para conservar el ancho en dp), guarda en disco QUÉ restaurar ANTES de cambiar (`PrefsResizeStore`, `commit()` síncrono), verifica el cambio y lo revierte si el sistema no lo aceptó, restaura el override previo del usuario si lo había, y no pisa lo guardado si se aplica dos veces. Solo permite comandos `wm size|density` (`isAllowedCommand`).
- `display/ShizukuShell` + `ShellUserService` + `IUserService.aidl`: servicio que Shizuku ejecuta con permisos de shell (patrón UserService de Shizuku). Estado: no instalada / no activa / versión vieja / falta permiso / lista.
- Se aplica al espejar si «16:9 forzado» está en Sí; se restaura al detener (botón, botón «Detener y restaurar» de la notificación, error de arranque) y al abrir la app si quedó pendiente. Botón «Restaurar resolución» en la pantalla.
- Latencia: el modo por defecto pasó a «Cola corta, sin relleno» con búfer de envío de 16 KB (descarta lo viejo si el TV se atrasa). No hay datos de cuál variante fue mejor.
- Dependencias nuevas SIN verificar (Maven Central bloqueado desde el entorno): `dev.rikka.shizuku:api` y `provider` 13.1.5; `aidl = true`; proveedor `rikka.shizuku.ShizukuProvider` en el manifiesto. Reglas de R8 agregadas.
- Etiqueta: «módulo 3F (Shizuku 16:9 + restauración)». Verificado en la JVM: 165 pruebas. NO verificado: compilación (las llamadas a la API de Shizuku están escritas según su patrón documentado, de memoria), el servicio con permisos de shell, que `wm size` surta efecto en el Redmi 14C, ni la restauración real.

### Módulo 3G: espejo nativo (Miracast del sistema) con 16:9 forzado por Shizuku
Reporte real (3F): con Shizuku activa y conectada, «16:9 forzado» no cambiaba nada. Causa: ese botón solo marcaba una opción que se aplicaba al arrancar el espejo propio por DLNA (MediaProjection); nunca se conectó con el espejo nativo, y «Restaurar» decía «nada que restaurar» porque nunca se había aplicado. Error de diseño mío.
Decisión del usuario: dejar DLNA y usar el espejo nativo del sistema (Miracast / pantalla inalámbrica).
- `display/NativeMirrorFlow` (lógica pura, 6 pruebas con teléfono simulado): 1) fuerza 16:9 con `ScreenResizer` (OBLIGATORIO: si falla, NO se abre el panel de transmisión y se muestra el motivo); 2) abre la transmisión del sistema (`SystemCastLauncher`); 3) vigila la pantalla externa (`ExternalDisplayMonitor`): al desconectarse restaura; si no se conecta en 3 minutos restaura; si el teléfono no tiene panel de transmisión restaura. Corre en un alcance propio de la app (sigue vigilando con OpenTV en segundo plano).
- Pantalla «Espejo nativo 16:9» (`ui/mirror`, ruta `mirror`, tarjeta en el inicio): estado de Shizuku, permiso, botón «Forzar 16:9 y abrir transmisión», «Detener y restaurar», «Restaurar resolución» y la etiqueta de versión.
- En la pantalla del espejo propio el botón se renombró «16:9 en espejo propio» para no confundir.
- Etiqueta: «módulo 3G (espejo nativo + Shizuku)». Verificado en la JVM: 171 pruebas. NO verificado: compilación; que `ShizukuShell.exec` ejecute realmente `wm size`/`wm density` (nunca se probó el servicio con permisos de shell: es la primera prueba real); que Miracast acepte el cambio de resolución sin cortarse; el cierre de la app con la resolución forzada.
- Límite: si el proceso muere con el panel de transmisión abierto, la restauración ocurre al abrir OpenTV de nuevo (o con el botón / Termux).

### Módulo 3G-2: Shizuku por vía directa
Reporte real (3G): Shizuku «lista», pero al forzar 16:9: «Timed out waiting for 8000 ms». Era el `withTimeout` de `ShizukuShell.connect()`: el servicio auxiliar (`ShellUserService`) nunca llamó a `onServiceConnected`; el comando `wm` ni se ejecutó. Causa de fondo NO confirmada (sin registros del proceso auxiliar).
- `ShizukuShell.exec` ahora prueba primero la vía directa (`Shizuku.newProcess` por reflexión: `sh -c` con permisos de shell, sin proceso auxiliar; timeout 6 s) y, si falla, el servicio auxiliar (timeout 4 s). Si ambas fallan, el error lista cada motivo («directo: … | servicio auxiliar: …»). El timeout de coroutines se trata como error, no como cancelación. La lista blanca de comandos (`wm size|density`) se exige también en la app.
- Etiqueta: «módulo 3G-2 (Shizuku por vía directa)». Verificado en la JVM: 171 pruebas (la lógica no cambió). NO verificado: compilación; que `newProcess` exista con esa firma en la API 13.1.5 (privado, firma tomada de memoria); que funcione en el Redmi 14C.

### Módulo 3G-3: 16:9 persistente (causa del reinicio a los 2 s)
Reporte real (3G-2): Shizuku funciona (la resolución cambió a 16:9) pero volvía a la normal a los ~2 s, justo al abrir el panel de transmisión.
- Causa (confirmada en el código): `MainActivity.onCreate` llamaba a `restorePendingResize()` («restaurar al abrir la app por si quedó pendiente»); el cambio de densidad recreaba la Activity (el manifest no tenía `density` en `configChanges`), `onCreate` volvía a correr y la restauración deshacía el cambio recién aplicado. Error de diseño mío.
- Correcciones: se eliminó la restauración automática al abrir; `density` agregado a `configChanges` (la Activity ya no se recrea al cambiar la resolución); `NativeMirrorFlow` ya no restaura por tiempo ni al desconectarse (modo global hasta que el usuario restaure; `restoreOnDisconnect` queda como opción, desactivada); el inicio muestra el botón «Resolución 16:9 forzada · Restaurar» mientras haya algo pendiente.
- Restauración: «Detener y restaurar» / «Restaurar resolución» en el espejo nativo, el botón del inicio, o por Termux (`rish -c "wm size reset"`, `wm density reset`).
- Etiqueta: «módulo 3G-3 (16:9 persistente)». Verificado en la JVM: 172 pruebas (el modo persiste al conectar y desconectar, y mientras no se conecte ninguna pantalla). NO verificado: compilación; que la resolución se mantenga con el panel del sistema abierto en el Redmi 14C; que Miracast acepte el cambio.

### Módulo 3H: 16:9 manual en dos pasos, solo tamaño, panel de transmisión desde la Activity
Reporte real (3G-3): la resolución se queda fija (bien), pero el panel de transmisión de Xiaomi se abría errático (cruzado con Wi-Fi/red) y `wm density` desordenaba los íconos del escritorio.
- Íconos (hipótesis NO verificada): al cambiar la densidad para conservar el ancho en dp, la altura en dp baja (≈360×640 vs 360×820) y el launcher pierde filas y vuelve a guardar posiciones. Solo tamaño mantiene la altura en dp (≈540×960). `ScreenResizer.apply(changeDensity=false)` por defecto; la restauración solo toca la densidad si OpenTV la cambió (`PendingResize.densityChanged`, persistido). OpenTV NO puede deshacer un desorden ya ocurrido ni garantizar que no ocurra.
- Botón manual: `NativeMirrorFlow` ahora son dos pasos del usuario: «Forzar 16:9» y «Abrir transmisión» (estado `Forced(size, tvConnected, panelNote)`); la resolución no se restaura sola.
- Panel: `SystemCastLauncher.open(from)` usa la Activity visible (sin NEW_TASK). Si no abre, se indica el botón «Emitir/Smart View» del panel rápido. Comportamiento en HyperOS NO verificable aquí.
- Compatibilidad: la pantalla avisa si el teléfono no parece tener Miracast (sin pantalla de transmisión o sin Wi-Fi Direct) y remite al DLNA propio.
- Etiqueta: «módulo 3H (16:9 manual, solo tamaño)». Verificado en la JVM: 176 pruebas. NO verificado: compilación Android, cambios de UI, nada en hardware.

### Módulo 3H-2: rotación fija en horizontal con el 16:9
Reporte real (3H): íconos bien con solo tamaño; pero al girar la pantalla (o si una app pide horizontal) con 1080x1920 forzado la pantalla se parte/marea.
- Hipótesis NO verificada: el tamaño forzado es vertical y la rotación a mitad del espejo choca con él. Medida: al forzar 16:9 se fija la rotación en horizontal (`settings put system accelerometer_rotation 0` y `user_rotation 1`), guardando los valores previos en `PendingResize` (persistidos) y devolviéndolos al restaurar (borra `user_rotation` si no existía). Mejor esfuerzo: si falla, el 16:9 sigue igual. Lista blanca ampliada solo a esos dos ajustes.
- Sin cambios en el panel de transmisión (sigue pendiente la causa del desvío a Wi-Fi; hace falta ver qué intent abre Xiaomi).
- Etiqueta «módulo 3H-2 (16:9 + rotación fija)». Verificado en la JVM: 180 pruebas. NO verificado: compilación, hardware.

### Módulo 3H-2 (ampliado): asistente de Shizuku + espejo por navegador (MJPEG)
Pedido del usuario: mismo módulo 3H-2 (se conservan el arreglo de íconos —solo `wm size`— y la rotación horizontal fija), más (a) asistente interno de Shizuku y (b) servidor HTTP/MJPEG para teléfonos sin Miracast.
- Asistente (`display/ShizukuGuide`, pura, 5 pruebas; pantalla `ui/shizuku`, ruta `shizuku-guide`): 5 pasos (instalar, opciones de desarrollador, depuración inalámbrica, iniciar/emparejar, permiso) que se tildan por el estado REAL de Shizuku; botones para abrir la tienda/GitHub, opciones de desarrollador, Shizuku y pedir el permiso. Se abre desde «Espejo nativo» mientras Shizuku no esté lista. Límite honesto: Shizuku no puede embeberse ni activarse sola (Android exige la depuración inalámbrica del usuario) y se apaga al reiniciar.
- Espejo por navegador: `stream/MjpegHub` (solo el último cuadro; sin acumulación), `MjpegHttpServer` (HTTP/1.0, `/<código>/`, `stream.mjpg`, `snapshot.jpg`, `lento` con recarga automática para navegadores muy viejos; solo red local; máx. 4 clientes; repite el último cuadro si la pantalla no cambia), `MjpegPages`, `MjpegQuality` (Baja/Media/Alta), `BrowserMirror` (MediaProjection → ImageReader → JPEG; reutiliza `ProjectionService`; notificación con «Detener»), pantalla `ui/browser` (ruta `browser`, tarjeta «Espejo por navegador» en el inicio). Solo HTTP (no HTTPS: sin certificado válido en red local y los TV viejos no abren TLS moderno).


### Módulo 3H-2 (ampliado 2): central «Transmitir a TV», audio real en el espejo DLNA, URL corta del navegador
Reporte real: 16:9 por Shizuku estable. El espejo por navegador dio «página no disponible» en el navegador del LG (causa NO conocida).
- Central de conexiones (`ui/connect/ConnectHubScreen`, ruta `hub`): estado de la transmisión, botón 16:9 (fuerza/restaura; si Shizuku no está lista abre el asistente), botón «Emisión nativa» (abre la transmisión del sistema desde la Activity), lista de TV de la red con SUS modos (`connect/ConnectModes`, pura: DLNA → espejo con sonido y proyectar OpenTV; Cast → salida del reproductor), y alternativas sin emisión nativa. Inicio limpiado: Aplicaciones, Transmitir (central, 16:9, emisión nativa) y Más (todas las apps, configuración). Se eliminó la pantalla «Espejo nativo» (la reemplazan la central y el asistente) y las tarjetas sobrantes.
- Audio real en el espejo DLNA: `PlaybackAudioCapture` (captura de audio interno, Android 10+, con permiso RECORD_AUDIO que se pide al espejar; sin permiso o Android <10 sigue con pista muda y lo anota en el historial). `SilentAudioEncoder` → `ScreenAudioEncoder` con fuente PCM opcional. NO verificado en hardware; apps que prohíben la captura quedan mudas; no se sabe si Android 14 permite capturar con la app en segundo plano.
- Navegador: URL corta por defecto (`http://IP:8080`, sin código; alternativa con código), puerto fijo 8080→8081→8888→azar, rutas cortas (`/s`, `/f`, `/l`), historial de pedidos en pantalla (para saber si el TV llegó al servidor).
- Asistente de Shizuku: «Siguiente paso» y aviso para Android <11 (sin depuración inalámbrica).
- Etiqueta sin cambios («módulo 3H-2 (16:9 + rotación fija)»). Verificado en la JVM: ver cifra en la entrega. NO verificado: compilación Android ni nada en hardware.

### Revisión por «BUILD FAILED» en GitHub Actions (sin registro del error)
Sin el registro no se pudo ubicar el error exacto (el entorno no tiene Android SDK: la capa Android nunca se compiló aquí). Se hizo: pruebas de lógica repetidas con el frontend de Kotlin 1.9 (`-language-version 1.9`, el mismo que usa el proyecto): 202 OK; XML bien formado; revisión de balance de llaves/paréntesis y de importaciones. Se corrigieron puntos sospechosos de compilación: `try…catch ?: return` en `BrowserMirror.FrameEncoder` (ahora explícito), `socket!!` y captura de `var` en `MjpegHttpServer.start`, llamada a API 29 sin `@RequiresApi` en `PlaybackAudioCapture` (ahora con comprobación `SDK_INT` y función anotada), y llamada ambigua a `viewModel()` en `ConnectHubScreen` (ahora calificada). NO verificado: compilación real.
