# Home 2 de prueba (el «Nova launcher» integrado como OpenTV Home 2)
Estado: **integrado como módulo opcional, NO compilado en ningún lado todavía** (sin SDK de Android acá; solo el parser de voz compila y pasa sus 9 tests con Kotlin 2.0.21).

## Qué se hizo
- Módulo de biblioteca `:homenova` (paquete `com.opentv.homenova`): las 10 pantallas del Nova (inicio, apps, descubrir, en vivo, entradas, notificaciones, ajustes, búsqueda, voz, reproductor), renombrado a «OpenTV» en lo visible.
- Se **quitó** su `Application` y su receptor de manifest: ahora un contenedor perezoso (`NovaContainer.kt`) y el receptor de paquetes se registra por código en la actividad. No se toca la `Application` de OpenTV.
- Actividad **«OpenTV Home 2 (prueba)»**: ícono aparte, **horizontal fija**, **sin categoría HOME** (no compite con `.HomeAlias` ni con el Home v3). Resultado: dos homes de prueba separados, cada uno con su ícono.
- Versiones de la pila de OpenTV (Kotlin 1.9.24, compilador Compose 1.5.14, BOM 2024.06, compileSdk 34): todas las APIs que usa el Nova existen en ellas. Dependencias nuevas, solo en el módulo: `datastore-preferences 1.1.1` y `material-icons-extended` (versión del BOM).

## Por qué es opcional (bandera `-PwithNova=true`)
Sin compilador de Android acá, un error de compilación en 7.000 líneas bloquearía el APK y las pruebas de conectividad. Por defecto el módulo **ni se incluye** en el build (`settings.gradle.kts`/`app/build.gradle.kts` lo ven solo con la bandera). El build de siempre (`:app:testDebugUnitTest :app:assembleDebug`) queda idéntico.

## Cómo compilarlo (workflow aparte, manual)
Crear `.github/workflows/home2.yml` con el contenido de abajo y ejecutarlo a mano (Actions → «Home 2 (prueba)» → Run workflow). Genera un APK de OpenTV **con** el Home 2 dentro. Si falla, el workflow principal `Build` no se ve afectado; mandar el log de ese job.
```yaml
name: Home 2 (prueba)
on:
  workflow_dispatch:
jobs:
  build-home2:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: "17" }
      - uses: gradle/actions/setup-gradle@v4
      - run: chmod +x gradlew
      - run: yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses > /dev/null || true
      - run: ./gradlew :homenova:testDebugUnitTest :app:assembleDebug -PwithNova=true --stacktrace
      - uses: actions/upload-artifact@v4
        if: success()
        with:
          name: OpenTV-con-Home2-apk
          path: app/build/outputs/apk/debug/app-debug.apk
```

## Qué revisar al probarlo (checklist)
1. Que el workflow «Home 2 (prueba)» termine verde; si falla, pasarme el log del paso `gradlew` (los primeros errores `e:` alcanzan).
2. Instalar el APK de ese workflow: aparecen DOS íconos nuevos además de OpenTV: «OpenTV Home (prueba)» (Home v3) y «OpenTV Home 2 (prueba)».
3. Abrir Home 2 con el teléfono en cualquier posición: debe quedar **horizontal** y no rotar.
4. Pantalla de inicio: ¿se ve el hero y las filas? ¿carga la lista de apps? (es lo real; el catálogo es de demostración).
5. Táctil: tocar una app la abre; mantener tocada abre su menú; deslizar listas funciona.
6. Con control/teclado: flechas mueven el foco, OK abre, OK mantenido (0,5 s) o MENU abre el menú contextual, ATRÁS cierra paneles.
7. Pantallas: Apps, Descubrir, En vivo, Entradas, Notificaciones, Búsqueda (teclado en pantalla), Voz, Ajustes. Anotar cuál se cierra o se ve mal.
8. Voz: pide permiso de micrófono al usarla; probar «abrir <app>».
9. Reproductor (demo): abrir un título y ver si reproduce (usa internet).
10. Que **OpenTV** y el **Home v3** sigan funcionando igual (el Home 2 no debería afectarlos).
Si el Home 2 compila bien, cambiar `withNova=false` a `true` en `gradle.properties` lo incluye en el build normal.

## Limitaciones conocidas (no se arreglaron, para no inventar)
- Contenido de **demostración**: catálogo ficticio con videos de ejemplo, «en vivo» simulado y notificaciones de mentira. Solo son reales: lista de apps, entradas de TV (TV Input Framework), ajustes y voz.
- Usa `java.time` (Android 8+): en Android 7 (minSdk 24 de OpenTV) podría fallar al mostrar la hora.
- «Elegir como launcher predeterminado» en sus ajustes no sirve: esta actividad no declara HOME a propósito.
- Su reproductor propio es de demostración; no usa el motor de OpenTV.

## Próximos pasos (después de cerrar conectividad)
Quedarse con una sola base. Candidatos a unir: Home v3 (notificaciones reales, «seguir viendo», perfiles, Google Assistant/voz) + del Home 2 el rail lateral, ajustes por categorías, teclado de búsqueda con D-pad, panel de entradas y la corrección del OK largo. Cambiar el contenido demo por datos reales.

## Home unificado (ronda 7)
Base estética: Home 2 (horizontal fija). Integrado:
- De Home v3: asistente de voz (por defecto abre el asistente del sistema; si no hay, la pantalla de voz propia), botón Asistente/Búsqueda del control (MainActivity → `voiceRequests`), fila "Sistema" (Wi-Fi, Bluetooth, Sonido, Pantalla, Transmitir, Fecha, Apps, Ajustes).
- Del Home original de OpenTV: fila "OpenTV" (IPTV, DLNA, Conexiones, Multimedia, Apps, Control remoto, Ajustes) vía `OpenTvLink` → `RouteRequest` (lista blanca en :app).
- Toque: `tvFocusable` ya no reinicia el detector de toques en cada recomposición (corrige el doble toque).
No portado de v3: perfiles, "reproduciendo ahora"/notificaciones (requieren servicio de escucha no registrado), protector de pantalla.
Puntos de enganche para el Nova terminado: `NovaTvApp(voiceRequests)`, `HomeScreen(openTvShortcuts, systemShortcuts)`, `extras/*`, `Focus.kt`, `useSystemAssistant=true`.
Nada de esto está compilado en este entorno (sin Android SDK): compilar con el workflow home2.yml.
