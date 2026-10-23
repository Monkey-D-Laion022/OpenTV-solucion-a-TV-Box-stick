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
