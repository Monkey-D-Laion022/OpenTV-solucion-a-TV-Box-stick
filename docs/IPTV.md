# Emisión IPTV: el teléfono como canal de televisión en vivo

Módulo independiente (`app/.../iptv/`, `ui/iptv/`). No toca `TvProjection`, `ProjectionService`, el espejo nativo ni Shizuku/16:9.

## Cómo funciona
1. El usuario pulsa **Iniciar emisión IPTV** (Conexiones › Emisión IPTV) y acepta el permiso de captura de pantalla de Android.
2. `IptvService` (servicio en primer plano, notificación con «Detener») mantiene viva la captura.
3. `IptvController`: MediaProjection → `VirtualDisplay` → `ScreenEncoder` (H.264 Main por MediaCodec, hardware si el teléfono lo tiene) → `TsMuxer` (MPEG-TS) → dos salidas:
   - `HlsSegmenter`: corta en fotogramas clave en segmentos de 2 o 4 s (ventana de 4 en la playlist) y mantiene en memoria ≤ 24 MB.
   - `StreamHub`: reparte el TS continuo a quien pida `live.ts`.
4. `IptvServer` (HTTP/1.1, sockets propios, hasta 6 conexiones, solo IPv4 de red local) publica, bajo `http://IP:8090/<código aleatorio>/`:
   `index.m3u8` (HLS versión 3), `seg-N.ts`, `live.ts` (TS directo), `channel.m3u` (lista M3U de un canal), `compat.m3u` (lista de pruebas con HLS y TS directo), `status.json`.
5. En la app IPTV del televisor se carga `channel.m3u` (o el canal directo) y se elige el canal.

Calidad (pedida al codificador, no garantizada): 720p·15 fps·2 Mbps·segmentos de 4 s; 720p·30 fps·3 Mbps·2 s; 1080p·30 fps·6 Mbps·2 s.
Retraso teórico de HLS ≈ 3 segmentos (6–12 s); es una cuenta, no una medición.

## Qué está verificado y qué no
Verificado **aquí** (no en el teléfono ni el televisor):
- Lógica de segmentación, playlist, M3U, rutas, rangos y servidor con sockets reales: pruebas JUnit (17 en `IptvLogicTest`).
- Con `ffmpeg`/`ffprobe` reales: el servidor entrega una playlist HLS v3 válida; `ffprobe` reconoce H.264 Main 1280×720 + AAC LC 48 kHz; `ffmpeg` decodifica el HLS sin errores. El video de prueba lo generó x264, no el MediaCodec del teléfono.

**No** verificado: captura y codificación en el Redmi 14C, el servicio en primer plano en HyperOS, reproducción en el LG 42LB5800-SB, reproducción en SmartOne IPTV, que SmartOne acepte listas por dirección (puede exigir cargarlas desde su sitio web), audio.

## Prueba mínima de compatibilidad con el LG + SmartOne (la hacés vos)
1. Teléfono y televisor en la misma red; en el teléfono: Conexiones › Emisión IPTV › Iniciar.
2. En un navegador de otro equipo (o del propio teléfono) abrí `…/status.json`: debe responder y mostrar `segments` creciendo.
3. En SmartOne IPTV cargá la lista **de pruebas** (`compat.m3u`): trae «prueba 1: HLS» y «prueba 2: TS directo».
4. Probá cada canal y anotá: ¿abre?, ¿imagen?, ¿se corta?, ¿mensaje de error?. Mirá también «Últimos pedidos recibidos» en la pantalla de OpenTV: si no llega ningún pedido, el televisor no está llegando al teléfono (red/aislamiento); si llegan `index.m3u8` y `seg-N.ts` pero no se ve imagen, es un problema de formato.
5. Pasame esos resultados; con ellos se decide si hace falta un perfil más viejo (segmentos más cortos, sin audio, 720p15, etc.).

## Limitaciones y red
- HTTP sin cifrar; la protección es el código aleatorio en la ruta y el filtro de red local. Cualquier equipo de la Wi-Fi que conozca la dirección puede verlo.
- Routers con «aislamiento de clientes/AP isolation» o redes de invitados impiden que el televisor llegue al teléfono. El cortafuegos del teléfono no interviene (Android no filtra conexiones entrantes de apps), pero sí puede hacerlo el router.
- No hay audio real: pista AAC en silencio. Capturar el audio del sistema requiere `RECORD_AUDIO` (Android 10+, y solo apps que lo permiten); no está autorizado en el proyecto.
- Una sola captura de pantalla a la vez: no usar junto con el espejo por navegador.
- Si el televisor pide una lista M3U externa y no por dirección local, habrá que servirla de otro modo (pendiente de lo que muestre la prueba).
