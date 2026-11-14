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

## Propuestas separadas (NO aplicadas; esperan tu autorización)
- **A. Subir el tope de cola de `StreamHub` de 1 MB a 2 MB en `IptvController`** (una constante). Costo: hasta ~0,8 s más de retraso solo durante un atasco, nunca en régimen normal. Beneficio medido en simulación: cubre microcortes de hasta 1,6 s a 10 Mbps.
- **B. Ante enlace saturado, bajar bitrate (CBR) antes que resolución/fps**: el codificador admite `setBitrate` en vivo; hoy el adaptador cambia de preset. Requiere pruebas en el Redmi.
- Para medir de verdad: grabar `lateTicks`, `capturedFrames/renderedFrames` y la tasa de entrega por cliente durante Stremio/Super TV y compartirlos.
