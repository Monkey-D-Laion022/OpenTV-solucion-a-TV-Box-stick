# Ronda 10 (sobre los resultados de la ronda 9) — versión 6.8.0 / PKG 06.10

## Lo que muestra el opentv.log (PS3)
- **720p60**: el teléfono produjo ~57 fps y la PS3 decodificó todo sin errores, pero **mostró 36–45 fps**: el presentador tardaba 19–20 ms por cuadro (escalado 720→1080 + copia a la memoria de video), por encima de los 16,7 ms de 60 Hz. Había 1.500 cuadros decodificados y no mostrados. Además el **A/V skew creció hasta +465 ms** (video por delante del audio).
- **1080p60**: presentación 12–13 ms (sobra), decodificación sin errores; picos de 58–60 fps y luego 30. El flujo llegaba a ~30 fps con pausas de 1,2–3 s: el límite está en el teléfono (el gobernador baja 60→30 por tics atrasados / cola del codificador, intenta subir y vuelve a bajar: eso es el «acelera un instante y vuelve a 30»).

## Cambios
1. **PS3 (escalado)**: camino rápido 1280x720→1920x1080 (4 px → 6 px con 3 escrituras de 64 bits). Objetivo: bajar de ~19 ms a ~14–15 ms. La copia a la memoria de video (~12 ms por 8 MB) es el piso; no se medirá en esta ronda sin hardware: probar y mirar `presentar … us` en el log (meta < 16000).
2. **Teléfono: «Mantener 60 fps»** (Ajustes → Sistema, desactivado por defecto): el gobernador nunca baja los FPS y la escalera de resolución solo usa escalones con los mismos FPS; ante sobrecarga solo baja el bitrate. Con el teléfono saturado habrá tirones en vez de caer a 30. Se aplica en la próxima transmisión.
3. **Audio silencioso**: hilo con prioridad de audio y ráfaga de cuadros cuando se atrasa más de 60 ms respecto del reloj (ataca el desfase A/V creciente). Hipótesis, sin confirmar en hardware.
4. **Versión** 6.8.0; PKG 06.10.
5. (Ronda 9, ya incluido) replay de GOP en DLNA para el timeout inicial; el LG sigue pendiente de confirmar.

## Límites
- El tiempo del codificador del Redmi 14C bajo carga (23–28 ms/cuadro) no lo cambia ningún ajuste del receptor.
- Miracast: el TV percibe 30 fps aunque el teléfono envíe 60 (el LG declara 1080p30; 1080p60 forzado es no estándar y su decodificador parece mostrar 30).
- DLNA/LG: sigue siendo el caso más frágil; si «Mantener 60 fps» no lo resuelve, la causa es el reproductor del TV.

## Probar
PS3 720p60 y 1080p60 con «Mantener 60 fps» activado y desactivado; comparar `fps mostrados`, `presentar us`, `A/V skew`. Pasar el opentv.log de nuevo.
