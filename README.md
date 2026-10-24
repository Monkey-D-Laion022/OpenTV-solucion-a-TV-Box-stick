# OpenTV

Aplicación Android que convierte un teléfono antiguo en un centro multimedia para televisor
(launcher tipo Android TV, reproductor, conexión a TV y control remoto desde otro celular).

> **Estado: Fase 3 de 7 (conectividad), código escrito y auditado de forma estática, SIN compilar.** Ver [`docs/PROGRESS.md`](docs/PROGRESS.md) para lo que
> existe, lo que falta y lo que NO se ha verificado.

## Requisitos para compilar

- JDK 17 o superior (probado con 21 solo para la lógica pura; ver PROGRESS).
- Android SDK con **plataforma 34** y build-tools. Con Android Studio (Hedgehog o más nuevo) se
  instala solo al abrir el proyecto. Sin Android Studio, crea `local.properties` en la raíz:

  ```
  sdk.dir=/ruta/al/Android/Sdk
  ```
- Acceso a `google()`, `mavenCentral()` y `services.gradle.org` para descargar dependencias.

## Compilar sin computadora (GitHub Actions)

El repositorio incluye `.github/workflows/build.yml`. Si subís el proyecto a un repositorio de
GitHub, cada subida compila en los servidores de GitHub, corre las pruebas y deja el APK como
descargable: pestaña **Actions** › la última ejecución › **Artifacts** › `OpenTV-debug-apk`.
Si falla, el log del paso «Pruebas unitarias y APK de depuración» trae el error exacto, que se
puede copiar y pegar para corregirlo. Este workflow **no se ha ejecutado todavía**.

## Comandos (solo con computadora)

```bash
./gradlew :app:testDebugUnitTest   # pruebas unitarias
./gradlew :app:assembleDebug       # APK de depuración
# Resultado: app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Todavía no hay APK de lanzamiento firmado (se prepara en la Fase 6).

## Qué hace la Fase 3 (Conectar TV)

- **Estado real, sin simular**: la pantalla «Conectar TV» muestra por método (Miracast, Cast, DLNA,
  Wi‑Fi Direct) un estado honesto: Conectado / Disponible / Sin verificar / Solo detectado /
  No disponible.
- **Miracast**: Android no da a las apps ninguna API para buscar TV ni conectarse. OpenTV solo
  puede (a) detectar si hay una pantalla externa con `DisplayManager` (no distingue HDMI de
  inalámbrica) y (b) abrir la pantalla de transmisión del sistema (`ACTION_CAST_SETTINGS`).
  «Desconectar» abre esa misma pantalla, porque una app no puede cortar el enlace.
- **Google Cast**: SDK oficial (`CastContext` + `MediaRouter`). Hoy **detecta** dispositivos; el
  envío de contenido llega con el reproductor (Fase 4).
- **DLNA**: búsqueda SSDP real por UDP y envío de un enlace directo al TV (AVTransport). Solo
  acepta enlaces y TV de la red local. No duplica la pantalla.
- **Wi‑Fi Direct**: solo se detecta el hardware. No se usa todavía (el canal entre dos teléfonos
  llega en la Fase 5) y nunca se promete video.
- **Guía por marca de TV y fabricante del teléfono** y solución de problemas.
- Permisos nuevos: `INTERNET` y `ACCESS_NETWORK_STATE` (normales, sin diálogo).

## Qué hace la Fase 2 (launcher)

- **Aplicaciones**: lista real de apps instaladas (nombre e ícono reales, con
  `queries` en el manifest, sin `QUERY_ALL_PACKAGES`), búsqueda sin tildes ni mayúsculas, orden
  (favoritas primero / A–Z / Z–A), «Actualizar», y apertura con intent explícito.
- **Favoritas**: pulsación prolongada, o tecla Menú / F, marca ★. Aparecen en el inicio.
- **Apps solo verticales**: si el manifest de la app declara orientación vertical fija, se avisa
  antes de abrirla (OpenTV no puede forzarla a horizontal; Android no lo permite).
- **Errores al abrir**: app desinstalada, bloqueada por el sistema o con fallo, con mensaje claro.
- **Launcher predeterminado**: OpenTV aparece como opción de «App de inicio» (alias HOME). Se
  elige desde Configuración, que abre la pantalla oficial de Android. Nunca se activa solo, y el
  mismo lugar sirve para volver al launcher original. El botón Inicio devuelve a la pantalla
  principal de OpenTV.
- Íconos cargados bajo demanda, caché LRU con tope de 2–8 MB y vaciado ante falta de memoria.

## Qué hace la Fase 1

- Interfaz horizontal (`sensorLandscape`), fondo a pantalla completa y barras del sistema ocultas.
- Margen de seguridad del 5 % por lado para el contenido (overscan de algunos TV) y respeto de la
  muesca de la cámara.
- Inicio con hora, fecha, estado de transmisión y 6 tarjetas navegables con flechas, Enter y
  Escape/Atrás. Las funciones que aún no existen llevan la etiqueta «Fase N» y su pantalla lo dice.
- «Continuar viendo» no se muestra: todavía no hay historial real.
- Dispositivo: pantalla real, ventana de la app, orientación, densidad, relación de aspecto, RAM,
  versión de Android y limitaciones conocidas. Solo valores que informa el sistema.
- Configuración: solo las secciones que existen (Diagnóstico y Acerca de).
- Sin permisos en el manifest.

## Estructura

```
app/src/main/java/com/opentv/app/
  MainActivity.kt          ventana inmersiva, Escape = Atrás
  OpenTvApp / AppContainer dependencias compartidas (inyección manual, perezosa)
  core/                    lógica pura sin Android (AspectRatio, OrientationPolicy)
  apps/                    repositorio, favoritas, lanzador, íconos, búsqueda/orden
  launcher/                ayuda para el launcher predeterminado
  connect/                 capacidades, Cast, SSDP/UPnP/DLNA, pantalla externa, guías
  device/                  lectura de pantalla, memoria y versión (DeviceInfoProvider)
  navigation/              rutas y NavHost
  ui/theme|components      tema oscuro, FocusableSurface, TvCard, TvButton, InfoRow
  ui/home|device|settings|pending|apps|connect   pantallas
```

Los paquetes de las fases siguientes (`apps`, `connect`, `player`, `files`, `remote`, `data`,
`diagnostics`) se crearán con su código; no se dejan carpetas vacías.

## Decisiones de arquitectura (Fase 0/1)

- **minSdk 24, target/compile 34.** Un solo módulo Gradle con paquetes por responsabilidad.
- **MVVM**: `ViewModel` + `StateFlow` (el reloj se detiene cuando la pantalla no está visible).
  La lectura de `DeviceInfo` es una instantánea sin estado y se hace en un proveedor de la capa de
  dispositivo; se pasará a ViewModel cuando haya estado que conservar.
- **Foco primero**: todo elemento interactivo pasa por `FocusableSurface`, con estado de foco
  siempre visible. No se usa `androidx.tv` para no subir requisitos ni dependencias.
- **Textos en español dentro del código.** Pendiente moverlos a `strings.xml` si se localiza.

## Limitaciones conocidas

- Miracast no tiene API pública de transmisión para apps: OpenTV solo podrá abrir la función del
  sistema (Fase 3) y no controla la relación de aspecto de esa transmisión.
- Las apps externas con orientación fija no pueden forzarse a 16:9/horizontal.
- La ventana inmersiva oculta las barras; en algunos equipos se muestran al deslizar desde el borde.
- `FLAG_KEEP_SCREEN_ON` mantiene la pantalla encendida mientras OpenTV está abierta (consumo de
  batería: usar el teléfono enchufado).


.
