# OpenTV Fase 6.3 · pruebas (una sola tanda)

**Aviso:** esta versión NO fue compilada para Android en mi entorno (sin SDK). Solo pasaron 392 pruebas de lógica, la validación del MP4 con ffmpeg y un chequeo de tipos con stubs. Subila a GitHub y fijate que Actions compile; si falla, pasame el error.

## Teléfono (Redmi 14C)
1. IPTV → «Medir» (~20 s). Esperado: lista de perfiles con FPS medidos y el elegido (1080p60, 720p60 o menos). Informá la lista.
2. Iniciar emisión en automático con un video en pantalla. Esperado: pedido/codificado/entregado distintos y sin etiquetas falsas; notificación con tecnología, resolución, FPS, bitrate, audio y «Detener». Informá los números.
3. Con sonido: estado de audio coherente en pantalla y notificación.
4. Wi‑Fi Direct (diagnóstico Miracast): informá si `connect()` sigue dando error 0 y el texto que muestra.

## Noblex X7 (Android TV)
- IPTV Smarters con `http://IP:8090/tv/channel.m3u` y luego `compat.m3u` (3 canales). Informá cuál reproduce, con audio y a qué FPS parece.
- Navegador: `http://IP:8090/`. Esperado: reproduce solo; informá el método usado.

## LG 42LB5800-SB (NetCast)
- Navegador del TV: `http://IP:8090/` (dirección corta). Esperado: prueba HLS, MP4, TS en orden; en el teléfono aparece el diagnóstico. Informá método que reprodujo o mensaje final.
- Smart IPTV: `compat.m3u`, informá qué prueba (1, 2, 3) funciona.

## Dos teléfonos (OpenTV Link)
- Emisor: Link → Enviar; receptor: Link → Recibir. Emparejar comparando el código de 6 dígitos, luego Conectar. Esperado: imagen en el receptor; cortá el Wi‑Fi unos segundos y verificá que reconecta. Informá resolución/FPS/latencia aproximada.

Informar siempre: modelo, qué pasó vs. lo esperado y, si falla, el texto exacto en pantalla.
