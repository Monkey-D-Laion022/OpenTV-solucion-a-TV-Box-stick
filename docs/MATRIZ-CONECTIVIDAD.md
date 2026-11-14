# Matriz de conectividad inalámbrica (2026-11-14)
Estados: APROBADA (integrada + compila + pruebas disponibles OK) · PARCIAL · FALLIDA (error reproducible) · PENDIENTE DE HARDWARE.
Regla aplicada: **nada simulado se marca APROBADA.** Android no se pudo compilar aquí (lo compila GitHub Actions); por eso ninguna tecnología Android llega a APROBADA todavía.

| Tecnología | Estado | Base de la calificación | Pendiente |
|---|---|---|---|
| APK / app base | APROBADA (usuario, ronda 1) | prueba física del usuario | — |
| IPTV | PARCIAL | HW: 1080p60 bien; cae bajo multitarea extrema. Cambios (cola por bitrate, adaptación por pérdida) solo PC/SIM | repetir carga extrema en el Redmi |
| Espejo / DLNA | PARCIAL | HW: 1080p30, baja latencia, tirones y ≈5 FPS capturados. Cambio en `FramePump` sin compilar/medir | medir FPS capturado/codificado/mostrado; logcat de microcierres |
| /tv (navegador) | FALLIDA en ronda 1 (HW); corrección PENDIENTE DE HARDWARE | script v2 + Plan B probados solo en simulador | probar en la LG |
| Miracast / Wi-Fi Direct | FALLIDA (M4) | causa desconocida sin el texto de M3 | enviar el texto M3 del diagnóstico |
| OpenTV Link (PS3) | PARCIAL | HW: cifrado y audio OK, imagen negra. Protocolo: 14 pruebas PC OK. Diagnóstico agregado | ejecutar en la PS3 y enviar `opentv.log` |
| Web Video Caster / alternativa | PARCIAL (investigación) | cubierta por DLNA + Plan B de /tv; sin función "enviar URL" dedicada | decidir si se agrega |
| Tecnología propia nueva | NO JUSTIFICADA | ver `INVESTIGACION-TECNOLOGIA-PROPIA.md` | — |

## Auditoría (hecha por revisión de código y tests PC)
- Receptores PC eliminados (receivers/pc, tools/receiver-py); docs y texto de la app corregidos.
- Interfaz: Miracast/Cast, /tv, IPTV, OpenTV Link y DLNA ya son grupos separados en Conexiones. `MethodAdvisor` existe y tiene tests, pero **no está conectado a la pantalla** (no se hizo a ciegas): pendiente.
- Funciones visibles sin validar: textos "Sin probar" ya presentes en la UI para IPTV y Link.
- Fugas/concurrencia/sesiones largas: NO medidas. Solo hay `HubStress` (PC) para el Hub. Requiere hardware.
