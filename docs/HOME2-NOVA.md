# Home 2 de prueba (el «Nova launcher» integrado como OpenTV Home 2)
Estado: **integrado como módulo opcional, NO compilado en ningún lado todavía** (sin SDK de Android acá; solo el parser de voz compila y pasa sus 9 tests con Kotlin 2.0.21).

## Qué se hizo
- Módulo de biblioteca `:homenova` (paquete `com.opentv.homenova`): las 10 pantallas del Nova (inicio, apps, descubrir, en vivo, entradas, notificaciones, ajustes, búsqueda, voz, reproductor), renombrado a «OpenTV» en lo visible.
- Se **quitó** su `Application` y su receptor de manifest: ahora un contenedor perezoso (`NovaContainer.kt`) y el receptor de paquetes se registra por código en la actividad. No se toca la `Application` de OpenTV.
- Actividad **«OpenTV Home 2 (prueba)»**: ícono aparte, **horizontal fija**, **sin categoría HOME** (no compite con `.HomeAlias` ni con el Home v3). Resultado: dos homes de prueba separados, cada uno con su ícono.
- Versiones de la pila de OpenTV (Kotlin 1.9.24, compilador Compose 1.5.14, BOM 2024.06, compileSdk 34): todas las APIs que usa el Nova existen en ellas. Dependencias nuevas, solo en el módulo: `datastore-preferences 1.1.1` y `material-icons-extended` (versión del BOM).

## Cómo se compila (ronda 8)
**Va siempre dentro del build normal**: `gradle.properties` trae `withNova=true`, así que `build.yml` (`:app:assembleDebug`) ya incluye el Home v3 y el Home 2 junto con OpenTV y la PS3 (la PS3 se compila en su propio job). **Ya no hace falta `home2.yml`** (se eliminó). Para sacar el Home 2 del build si algún día rompe la compilación: poner `withNova=false`.

Qué revisar al probar: aparecen DOS íconos además de OpenTV: «OpenTV Home (prueba)» (Home v3) y «OpenTV Home 2 (prueba)». Home 2: horizontal fija, un toque por tarjeta, D-pad completo. Micrófono: ver «Pendiente» más abajo.

**Pendiente (reportado en la ronda 7):** en el Home 2 el micrófono/Asistente no responde (en el Home v3 sí). No se tocó en esta ronda por pedido; para la próxima, comparar cómo el v3 lanza el asistente (`OpenTvHomeActivity`) con `openVoice()` del Home 2.

## Home unificado (ronda 7)
Base estética: Home 2 (horizontal fija). Integrado:
- De Home v3: asistente de voz (por defecto abre el asistente del sistema; si no hay, la pantalla de voz propia), botón Asistente/Búsqueda del control (MainActivity → `voiceRequests`), fila "Sistema" (Wi-Fi, Bluetooth, Sonido, Pantalla, Transmitir, Fecha, Apps, Ajustes).
- Del Home original de OpenTV: fila "OpenTV" (IPTV, DLNA, Conexiones, Multimedia, Apps, Control remoto, Ajustes) vía `OpenTvLink` → `RouteRequest` (lista blanca en :app).
- Toque: `tvFocusable` ya no reinicia el detector de toques en cada recomposición (corrige el doble toque).
No portado de v3: perfiles, "reproduciendo ahora"/notificaciones (requieren servicio de escucha no registrado), protector de pantalla.
Puntos de enganche para el Nova terminado: `NovaTvApp(voiceRequests)`, `HomeScreen(openTvShortcuts, systemShortcuts)`, `extras/*`, `Focus.kt`, `useSystemAssistant=true`.
Nada de esto está compilado en este entorno (sin Android SDK): compila `build.yml`.
