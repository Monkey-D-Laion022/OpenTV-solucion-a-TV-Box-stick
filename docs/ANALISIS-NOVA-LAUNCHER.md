# Análisis del «Nova TV Launcher» (ZIP de DeepSeek/OpenCode/CraftAnything) — 2026-11-22
**Actualización 2026-11-22 (a pedido del usuario): se integró como módulo OPCIONAL «Home 2 (prueba)» (`-PwithNova=true`), ver `HOME2-NOVA.md`. El build por defecto no lo incluye.**

**Decisión inicial: no meterlo en el build principal en esta ronda, por los motivos de abajo (siguen vigentes: por eso es opcional).**

## Qué es
Proyecto Android independiente (`com.novatv.launcher`), 7.172 líneas Kotlin, 10 pantallas (inicio con hero y filas, apps, descubrir, en vivo, entradas, notificaciones, ajustes, búsqueda, voz, reproductor), DataStore, TV Input Framework, parser de voz con 9 tests. Usa Compose Material3 normal (no `tv-material`).

## Por qué no ahora
1. **Nunca se compiló.** README y CAMBIOS dicen que solo pasó análisis sintáctico y 9 tests del parser; `docs/ARQUITECTURA.md` afirma lo contrario («verificado assembleDebug»). Se toma lo primero.
2. **Pila distinta a la de OpenTV:** Kotlin 2.0.21 + plugin compose, AGP 8.7.3, compileSdk 35, BOM 2024.12, minSdk 29. OpenTV usa Kotlin 1.9.24 + compilador 1.5.14, AGP 8.5.2, compileSdk 34, BOM 2024.06, minSdk 24. Meterlo exige portar y sumar DataStore; cualquier error de compilación bloquea el APK y con él las pruebas de conectividad.
3. **Mucho contenido de demostración:** catálogo ficticio con videos de ejemplo, parrilla «en vivo» simulada y notificaciones de mentira. Home v3 ya trae lo real (notificaciones, «seguir viendo» por sesiones de medios, perfiles, voz, accesos del sistema).
4. **Duplica** reproductor (Media3), ajustes y navegación que OpenTV ya tiene.
5. **Procedencia:** `docs/ANALISIS_REFERENCIA.md` indica que se tomaron colores, medidas y flujos de un launcher de Google TV **decompilado con JADX**. Declara no copiar código ni recursos, pero choca con la regla del proyecto de no hacer ingeniería inversa de apps propietarias. No se incluye ese documento ni se usan sus métricas; si se porta algo, se rediseña con valores propios.
6. Declara `HOME` con prioridad y `RECEIVE_BOOT_COMPLETED`/`QUERY_ALL_PACKAGES`: competiría con `.HomeAlias`.

## Qué vale la pena portar (de a una pieza, con APK compilado entre medio)
- Corrección del **OK largo** con mando (`ui/components/Focus.kt`).
- **Teclado de búsqueda** navegable con D-pad (`SearchScreen.kt`).
- **Panel de entradas** HDMI/TV con TV Input Framework real (`InputRepository.kt`, `InputsScreen.kt`) — útil en TV.
- Estructura de **ajustes por categorías** y **rail lateral**.
- `VoiceCommandParser` (puro, testeable; compilable aquí).
No portar: catálogo/live/notificaciones de demostración, su reproductor, su manifest.

## Cambio hecho ahora en Home v3
Orientación **horizontal fija** (`sensorLandscape` + `configChanges`, igual que `MainActivity`). Sin tocar código Kotlin. No compilado/probado.
