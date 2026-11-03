# IPTV: corte en segundo plano, ritmo de cuadros y 60 fps (6.5)

## Evidencia de las pruebas de la 6.4
LG 42LB5800-SB y Noblex X7: la emisión se corta al salir de OpenTV, volver atrás o abrir otra app, **aunque la 6.4 ya tenía servicio en primer plano, WAKE_LOCK y WifiLock**. Siempre se elige «Compartir pantalla completa».

## Auditoría del ciclo de vida (código real)
| Pieza | Quién es dueño | ¿Algo la detiene al salir de la pantalla? |
|---|---|---|
| Sesión (MediaProjection, VirtualDisplay, codificador, servidor HTTP, temporizador) | `IptvController` (singleton del proceso) vía `CapturePipeline` | No: la pantalla y su ViewModel solo observan; ninguna ruta de salida llama a `stop()` |
| Servicio en primer plano | `IptvService` (notificación, tipo mediaPlayback+mediaProjection, mantiene vivo el proceso) | Solo se detiene con «Detener» o al quedar el registro vacío |
| Corrutinas | `viewModelScope` solo lanza `start()`, que termina al iniciar; no sostiene la emisión | No hay corrutina dueña que cancelar |
| `MainActivity.onDestroy` (protegido) | Llama `shutdownProjection()`, que solo apaga la proyección DLNA y el espejo por navegador si estaban iniciados | No toca IPTV |
| Pantalla de Conexiones (protegida) | Al ocultarse cancela búsquedas Cast/DLNA/pantalla externa | No toca IPTV |

**Conclusión:** en el código no hay ninguna orden de parada asociada a salir de la pantalla. La 6.4 tampoco mostró un «Última parada», lo que
indica que el controlador seguía creyendo que emitía: el corte era una **emisión que se quedaba sin cuadros**, no una parada.

## Causa más probable (NO confirmada en dispositivo)
La pantalla virtual de MediaProjection entrega un cuadro **solo cuando algo cambia en pantalla**. Mientras se miraba la pantalla de IPTV
(que se actualiza cada segundo) o la de Conexiones (búsquedas animadas) llegaban cuadros; al pasar a una pantalla quieta (inicio, otra app
estática) casi dejan de llegar. Con el codificador conectado directo eso produce:
1. Fotogramas clave según **cuadros recibidos** (1 s = 30 cuadros): con la red de seguridad de 5 cuadros/s repetidos, un GOP de «1 s» pasa a durar ~6 s.
2. Segmentos HLS que no cierran (se cortan en fotogramas clave) y TS/MP4 con marcas de tiempo irregulares → el reproductor del televisor se queda sin datos y corta.
Coincide con que pasa en los dos televisores y con que los candados de CPU/Wi‑Fi no servían (no era energía).

## Corrección 6.5: bomba de cuadros a ritmo fijo (`FramePump`)
La pantalla virtual dibuja en una `SurfaceTexture`; un hilo OpenGL ES dibuja la última imagen en la superficie del codificador **a los FPS pedidos**
(30 o 60) aunque no haya cambios. Resultado esperado: ritmo constante, fotogramas clave cada 1 s, segmentos regulares, FPS que no dependen del contenido.
- Si no se puede crear el contexto GL, se usa la captura directa de antes y se avisa. Si falla en marcha, `fallbackToDirect()` reconecta la pantalla al codificador.
- Botón «Ritmo fijo de cuadros» (activado por defecto) para compararlo con la captura directa en la misma prueba.
- Si el corte persiste, la pantalla lo distingue: **FPS capturados** (cambios reales), **FPS entregados al codificador**, **FPS codificados**, **último cuadro hace N ms**, y «Estado verificado» que exige cuadros recientes y datos entregados. Un cuadro nuevo del codificador cada <3 s y entrega de datos <8 s con clientes conectados.
- Motivos de parada registrados: usuario, botón de la notificación, Android cerró MediaProjection, servicio detenido, codificador detenido, servidor HTTP detenido, error de captura, error de red. Los clientes que se desconectan figuran en el historial de pedidos (IP, agente, bytes, «el cliente cortó»).
- No se reinicia la captura ni se vuelve a pedir el permiso si Android la revoca.

## 60 fps: por qué sale 1080p30
- «Declarado» por MediaCodec (`areSizeAndRateSupported`) no es «sostenido»: la medición (botón «Medir») codifica 5 s a la frecuencia pedida y cuenta cuadros; sostenido = ≥ 90 %. El modo automático elige el mejor perfil sostenido. Si 1080p60 aparece en la lista pero no queda elegido, es porque la medición no lo sostuvo.
- Posibles causas reales a confirmar con tu medición: límite del codificador H.264 del teléfono (muchos de gama media/baja sostienen 1080p30), frecuencia de la pantalla virtual, o contenido quieto (ahora corregido por la bomba).
- Cambio: si elegís el perfil **a mano** (calidad automática desactivada) se intenta tal cual, sin bajarlo por una consulta previa; los FPS codificados reales se muestran.
- Se distinguen: FPS capturados, solicitados, codificados, entregados/enviados; FPS recibidos y Hz del televisor **no están disponibles** (y no son lo mismo).
- 1080i no se implementa (MediaCodec no entrelaza).

## Perfiles para televisores antiguos
- 480p/360p piden H.264 **Baseline** (el más abierto), AAC‑LC, GOP de 1 s, segmentos de 4 s. Si el codificador no acepta Baseline se informa «no aceptó el perfil pedido».
- Lista `plain.m3u` (`/tv/plain.m3u`): sin atributos `tvg-*`, saltos CRLF, tres canales (TS, HLS, MP4), para reproductores estrictos (candidato para el Noblex Linux).
