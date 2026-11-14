# OpenTV — Versión candidata para la ronda general de conexiones

Leyenda: **[PC]** ejecutado en PC · **[SIM]** simulación · **[HW]** requiere dispositivo real. Lo marcado [PC]/[SIM] no garantiza nada en hardware.
Complementa `INFORME-PARTE3.md` (DLNA, `/tv`, `MethodAdvisor`) y `IPTV-CARGA-PESADA.md`.

## 1. Build PS3 (error «'stdio.h' file not found») — corregido
- Causa: el job `diagnose-ps3` seguía ejecutando el flujo experimental con Clang; su wrapper tenía la ruta fija `/home/claude/ps3dev` (inexistente en GitHub) y Clang quedaba sin los headers Newlib del SDK. El `ppu-gcc` real nunca llegaba a usarse.
- Solución: `receivers/ps3/ci/setup-sdk.sh` (instala el SDK y verifica que `ppu-gcc` compila un programa con stdio.h/string.h/stdint.h/math.h desde su propio sysroot y que no usa `/usr/include`) + job `diagnose-ps3` reescrito: instalar SDK → `make tools` → `make ci` (FAAD2 + mbedTLS + receptor + PKG + verificación) → subir PKG. Los scripts de Clang se conservan por referencia (`toolchain/LEEME-OBSOLETO.md`) y el wrapper ya no tiene rutas fijas. No se copió ningún stdio.h ni se desactivó ningún error.
- Verificado [PC] desde cero con el SDK real: SDK extraído en directorio limpio, FAAD2 y mbedTLS compilados con ppu-gcc 13.2.0, PKG de 354.624 B, 9/9 verificaciones. **No** ejecutado en GitHub Actions. No pude probar la descarga del SDK vía la API de GitHub (inaccesible desde mi entorno); usa la lógica de selección de asset que ya funcionó en tu log.

## 2. IPTV bajo carga — optimizaciones aplicadas
Cola por cliente en tiempo (1,6 s del bitrate; perfiles ≤5 Mbps sin cambio) y reducción de bitrate por pérdida real medida (nunca resolución ni FPS). Antes/después [SIM]: pérdida 42,5 % → 8,0 % (enlace 7 Mbps), 8,4 % → 0 % (cortes de 1,2 s), 1080p60 conservado en todos. Costo: huecos más largos si el enlace es insuficiente. Detalle y límites en `IPTV-CARGA-PESADA.md`. [HW]: falta medir en el Redmi 14C.

## 3. Estado por tecnología
| Tecnología | Hecho | Pendiente |
|---|---|---|
| PS3 / Link | Receptor, `opentv.log`, autodiagnóstico, protocolo 14 pruebas, PKG con SDK real [PC] | emparejar, imagen y sonido reales [HW]; ejecutar el workflow |
| Miracast | FGS de MediaProjection, negociación H.264 por nivel (31 pruebas) [PC] | LG NetCast: M4→RTP y captura [HW] |
| DLNA | no corta si el TV sigue descargando; hereda mejoras de cola/bitrate [PC] | TV real [HW] |
| `/tv` | script ES3 con vigilante, reintentos y orden por capacidad [SIM] | LG 42LB5800 [HW] |
| IPTV | ver §2 | Redmi con apps pesadas [HW] |
| Unificación | `MethodAdvisor` (une TV por varios protocolos y elige método) 10 pruebas [PC] | **pantalla Compose sin integrar** (ver abajo) |
| Tecnología propia | OpenTV Link v1 (cifrado, emparejamiento, bitrate adaptativo por receptor) es la base; sin protocolo nuevo | receptores adicionales (Linux/PC) como siguiente paso |

## 4. Lo que NO hice y por qué
- **Integración visual (Compose) de las conexiones:** no hay SDK de Android en mi entorno y no pude descargarlo; un error de compilación bloquearía todo tu ciclo de pruebas. La lógica que la UI necesita ya está probada (`MethodAdvisor`, `TvTargets`); falta cablearla a la pantalla. Recomiendo hacerlo justo después de que confirmes que el build de Android compila.
- **Protocolo propio nuevo:** sin un segundo receptor que probar sería agregar un protocolo «por cantidad».
- Los archivos Android modificados (`DlnaSession.kt`, `IptvViewModel.kt`, `IptvController.kt`, `StreamHub.kt`, `QualityAdapter.kt`, `HubSizing.kt`, `WfdSourceStreamer.kt`) **no se compilaron con el SDK de Android**; sí se compilaron y probaron en JVM los que no dependen de Android (StreamHub, QualityAdapter, HubSizing, SessionSupervisor, MethodAdvisor…). `IptvController.kt` y `DlnaSession.kt` solo pasaron revisión manual.

## 5. Pruebas ejecutadas
530 pruebas JVM [PC] (todas las de lógica pura) · 14 pruebas de Link con réplica del emisor Android [PC] · `/tv` en Node 7/7 [SIM] · simulaciones de transporte y de bucle cerrado IPTV [SIM] · PKG PS3 real 9/9 [PC].
Protegidos sin tocar: ScreenResizer, PrefsResizeStore, Shizuku, lógica 16:9, CatalogLogicTest.kt. Sin `wm density`.

## 6. Ronda de pruebas físicas (orden)
1. Ejecutar el workflow; descargar APK, `opentv-ps3-pkg`. Si el job PS3 falla, subir el log del paso que falló.
2. PS3: instalar PKG, emparejar; si falla, enviar `opentv.log`.
3. LG: `http://IP:8090/tv` con IPTV activo; leer el registro `/diag` en el teléfono.
4. DLNA al LG: verificar que no baje a 480p mientras el TV reproduce.
5. Miracast: confirmar paquetes RTP > 0.
6. IPTV con Stremio/Super TV: anotar cuadros capturados/mostrados, `lateTicks` y si el reproductor sale en negro (contenido protegido).
