# ¿Hace falta una tecnología propia nueva? (evaluación honesta)
## Qué hay
OpenTV Link: TCP + AES-GCM + TS H.264/AAC, con STATS del receptor y adaptación de bitrate. /tv y DLNA: HLS/TS por HTTP. IPTV: HLS/M3U.
## Alternativas consideradas
- **UDP + corrección (FEC) / RTP**: evita el bloqueo de cabecera de TCP ante pérdida, pero en Wi-Fi doméstico con buena señal la pérdida es baja y TCP ya entrega 1,1 MB de prueba sin fallos. Exige reimplementar retransmisión/jitter en un receptor de recursos limitados (PS3).
- **WebRTC**: ningún receptor antiguo (NetCast, PS3) lo soporta.
- **Combinar Link + HLS**: no aporta nada medible sin saber dónde está el cuello.
## Conclusión
Los fallos medidos (≈5 FPS capturados, imagen negra en PS3, M4 de Miracast, /tv congelado) **no están en el transporte**: están en la captura, el decodificador del receptor y la compatibilidad del navegador. Una tecnología nueva no los arregla. **No se desarrolló prueba de concepto**: sin una medición que demuestre una ventaja, agregarla sería complejidad sin evidencia. Se reabre si los logs reales muestran pérdida de red como causa.
