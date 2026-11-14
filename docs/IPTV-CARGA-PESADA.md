# IPTV bajo carga pesada (Stremio 1080p, Super TV, juegos) — análisis y propuestas

**No se modificó ningún archivo de IPTV.** Este documento separa lo medido en PC (simulación), las hipótesis y las propuestas para evaluar aparte.

## Lo que se observó en el Redmi 14C (informado por León, no reproducido aquí)
- Stremio 1080p: la carga no avanzaba. Super TV: arrancó pero muy entrecortado.

## Qué revisé en el código (lectura, sin cambios)
- Codificador (`stream/ScreenEncoder.kt`): H.264 por hardware, CBR, `KEY_PRIORITY=0` y `KEY_OPERATING_RATE` si fps>30, `latency=1` cuando corresponde, I-frame cada 1 s. La configuración ya es la de tiempo real; no hay un parámetro evidente que corregir.
- Bomba de cuadros (`FramePump`): CFR por OpenGL, hilo con prioridad DISPLAY. Expone `intervals` y `lateTicks`: si bajo carga hay tics atrasados, el cuello es CPU/GPU del teléfono.
- Transporte (`StreamHub`): por cliente, cola de 1 MB (IptvController). Si se llena se **vacía entera y se espera al próximo fotograma clave** (hasta 1 s).
- Adaptador (`QualityAdapter`/`FpsGovernor`): ya distingue cuello de CPU/codificador/red.

## Simulación en PC (StreamHub real, tiempo lógico) — `tools/stress/HubStress.kt`
1080p60, 10 Mbps CBR, I-frame 1 s (6× un cuadro normal), 60 s. Es un modelo del reparto, **no** mide CPU, GPU, codificador ni Wi-Fi reales.

| Escenario | Perdido | Hueco máx. |
|---|---|---|
| Wi-Fi sano, sin cortes | 0 % | 16 ms |
| Microcorte 300 ms cada 5 s | 0 % | 304 ms |
| Microcorte 800 ms cada 5 s | 1,6 % | 960 ms |
| Corte 1,5 s cada 10 s | 12,2 % | 1632 ms |
| Enlace insuficiente (8 Mbps para 10) | 32,9 % | 864 ms |

Con tope de cola de 2 MB en vez de 1 MB, el microcorte de 800 ms pasa de 1,6 % de pérdida a 0 % (hueco 800 ms, sin salto de resincronización).

## Conclusiones (hipótesis, sin hardware)
1. Con enlace sano el transporte no es el límite: los cortes cortos (<0,8 s) no pierden nada.
2. Con el enlace justo o saturado, el flujo pierde ~1/3: es consecuencia de que el bitrate pedido (10 Mbps) supera lo que el Wi-Fi entrega, algo muy probable cuando **el mismo teléfono baja una película por ese Wi-Fi mientras sube el espejo** (half-duplex y una sola radio). Esto no es un fallo del código sino de capacidad compartida.
3. Una app pesada compite con el codificador/GL por CPU y GPU; la señal para confirmarlo en el teléfono son `lateTicks` altos y `capturedFrames` vs `renderedFrames` en la pantalla de estado de IPTV.
4. Stremio puede además marcar su reproductor como contenido protegido (FLAG_SECURE/DRM): la captura sale negra aunque la app «cargue». Es un límite de Android, no de OpenTV.

## ACTUALIZACIÓN: optimizaciones aplicadas (autorizadas el 2026-11-14)
1. **Cola por cliente dimensionada en tiempo** (`iptv/HubSizing.kt`): ~1,6 s del bitrate pedido, piso 1 MB, techo 3 MB. Perfiles de hasta 5 Mbps: sin cambio (1 MB). 1080p60 (10 Mbps): 2 MB.
2. **Señal de pérdida real** (`StreamHub.droppedBytes`, solo un contador; el reparto no cambió) y su uso en `QualityAdapter`: ≥ 2 % de lo producido en 30 s y en ≥ 2 episodios → bitrate −25 % (piso 40 %), **sin tocar resolución ni FPS**. Recuperación +10 % cada 20 s, y cada corte duplica la espera (hasta 120 s) para no oscilar.
3. Sin cambios en captura, codificador ni FPS: la configuración ya era la de tiempo real.

Antes/después (simulación en bucle cerrado, `docs/resultados-simulacion-iptv.txt`, 1080p60 pedido, 300 s):

| Escenario | Pérdida antes → después | Estado final |
|---|---|---|
| Wi-Fi sano + corte de 1,2 s cada 10 s | 8,4 % → 0,0 % | 1080p60 a 10 Mbps |
| Enlace 9 Mbps (película bajando a la vez) | 9,9 % → 4,2 % | 1080p60 a ~9,5 Mbps |
| Enlace 7 Mbps constante | 42,5 % → 8,0 % | 1080p60 a ~7,5 Mbps |
| 12 Mbps que cae a 6 Mbps 60 s y vuelve | 9,5 % → 4,0 % | 1080p60 |
| Wi-Fi sano + microcorte 300 ms | 0 % → 0 % | sin cambio |

Costos honestos: con enlace insuficiente el hueco máximo sin imagen sube (p. ej. 112 → 817 ms a 9 Mbps) porque la cola más grande tarda más en vaciarse; la pérdida total baja pero cada episodio dura más. En todos los casos se mantiene 1080p60. Con enlace sano el uso normal no cambia.
Límites: es un modelo (tiempo lógico, sin Wi-Fi, CPU ni térmica reales); falta medirlo en el Redmi 14C con Stremio/Super TV.

### Criterio de eficiencia por tecnología (no se copian parámetros)
| Tecnología | Quién fija el flujo | Estado |
|---|---|---|
| IPTV / `/tv` / DLNA | Tubería IPTV compartida (DLNA y `/tv` heredan la mejora) | optimizado arriba |
| OpenTV Link / PS3 | El receptor limita fps/bitrate en ACCEPT y reporta STATS; el emisor ajusta (`OtBitrateController`) | ya adaptativo; sin cambios |
| Miracast | Lo negociado por el TV (M3/M4); nivel H.264 flexible | sin cambios de bitrate; [HW] |

## Propuestas que quedan (NO aplicadas)
- **C. Codificación VBR/CBR restringida** para ahorrar tráfico en pantallas estáticas: depende del codificador de cada teléfono; requiere medir en el Redmi antes de tocar.
- **D. Buffer de envío del socket (SO_SNDBUF)**: beneficio no demostrable sin dispositivo.
- Para medir de verdad: grabar `lateTicks`, `capturedFrames/renderedFrames` y la tasa de entrega por cliente durante Stremio/Super TV y compartirlos.
