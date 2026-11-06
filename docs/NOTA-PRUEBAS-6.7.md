# Pruebas 6.7 — desde el Redmi 14C y el LG 42LB5800

Referencia: emisor Redmi 14C; televisor LG 42LB5800 (NetCast). No se asume nada del Noblex.

## 1. Microcortes IPTV (instrumentación nueva)
1. IPTV → desactivar el gobernador (para medir sin que cambie el ritmo) → "Iniciar emisión".
2. Mirar la línea de tiempos: percentiles 50/95/99 y máximo entre cuadros, cuadros tardíos, cuadros/KB/tiempo de entrega por segmento y separación entre pedidos.
3. Activar "Marcas de diagnóstico": el contador de la esquina permite ver en el LG si se repiten o saltan cuadros.
4. Provocar el corte (cambiar de app, navegar) y anotar qué cambia: intervalo máximo, cuadros tardíos, ratio de entrega. **La causa aún no está identificada**; estos números sirven para decidirla.
## 2. Wi-Fi Direct
Correr el diagnóstico: ahora registra el estado de pares antes de `connect()` y no conecta a pares ausentes. Anotar el campo exacto del "0" (estado del par: 0=conectado; error de acción: 0=ERROR).
## 3. MJPEG
Ver "producidos/repetidos": si los repetidos ≈ 1/s con pantalla quieta, el 1 fps era reenvío; mover la pantalla y comparar.
## 4. DLNA
IPTV en marcha → "Buscar televisores DLNA" → si aparece el LG, "Enviar a …". Copiar el registro mostrado (formatos probados y motivo de cada fallo) y si hay imagen y audio.
## 5. PS3
Ver `docs/PS3-RECEPTOR.md`.
