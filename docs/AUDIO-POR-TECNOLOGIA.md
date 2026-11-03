# «Audio solo en TV» en cada tecnología (6.5)

Probado físicamente solo en IPTV (LG y Noblex Android TV): el teléfono baja su volumen y el sonido llega al televisor. No se rompe.

| Tecnología | Estado | Motivo |
|---|---|---|
| IPTV | Funciona (probado) | `SoloTvAudio`: espera sonido, baja el volumen multimedia, mide la captura y restaura si se apaga o al terminar |
| OpenTV Link | **Implementado en 6.5** (misma lógica, sin probar) | Usa la misma captura (`CapturePipeline`) |
| Miracast nativo | No aplicable desde OpenTV | Lo gestiona Android; una app de terceros no controla su ruteo de audio ni su negociación. No medido |
| Wi‑Fi Direct / WFD | Idem | Es transporte; el audio WFD lo maneja el sistema |
| DLNA y espejo por navegador | No tocado | Dependen de `TvProjection`/`ProjectionService`/`BrowserMirror` (protegidos). La lógica es reutilizable (`SoloTvAudio.onSecond`) si se autoriza modificar esos archivos |

Límites que se mantienen: con auriculares/Bluetooth el volumen es por dispositivo; apps que bloquean Playback Capture entregan ceros (indistinguible de «no suena nada»); el volumen se restaura solo si sigue en 0 (si lo subiste a mano se respeta).
