# Receptor OpenTV para PlayStation 3 (6.7)

La PS3 es el **receptor**; el teléfono es el **emisor**. No se instala Android en la PS3 ni se convierte ningún APK.

## Qué hay (estado real)
| Pieza | Archivo | Estado |
|---|---|---|
| Demultiplexor MPEG-TS (H.264 Annex-B + AAC ADTS, PTS, pérdida de sincronía, descarte tras corte) | `receivers/ps3/core/ot_ts.c` | **Implementado y probado en PC** con gcc: 150/150 cuadros y 236/236 tramas AAC iguales a ffprobe, alimentando en trozos de 100000, 188 y 7 bytes; con 15 paquetes borrados y basura al inicio recupera la sincronía y cuenta 1 discontinuidad |
| Cliente HTTP (`GET /live.ts`) | `core/ot_http.c` | **Implementado y probado en PC** (loopback contra `http.server`: 200 → 150 cuadros; 404 → error -4; puerto cerrado → error -1) |
| Programa principal (config, reconexión automática) | `src/main.c` | Escrito para PSL1GHT, **no compilado, no probado en PS3** |
| Video/audio (cellVdec, cellAdec, cellAudio, RSX) | `src/ps3_av.c` | **Esqueleto**: solo cuenta cuadros; cada llamada real está marcada `TODO-PS3`. No se inventaron firmas de la API |
| Makefile / PKG | `Makefile` | Escrito, **no ejecutado** (no hay ps3toolchain en el entorno de desarrollo) |

## Qué falta (y por qué)
1. **Compilar**: falta `ps3toolchain` + `PSL1GHT` (no está en el entorno; se instala en un PC con `PS3DEV`/`PSL1GHT`; no se puede desde el teléfono ni desde este entorno).
2. **Decodificar y mostrar**: escribir y probar `ps3_av.c` con cellVdec (H.264) y RSX. Requiere una PS3 real con HEN/CFW; no se puede probar sin ella.
3. **Audio**: AAC en la PS3 necesita cellAdec o un decodificador propio; sincronía por PTS pendiente (Moonlight PS3 documenta problemas de sincronía de audio).
4. **Link seguro** (ECDH + AES-GCM, `docs/PROTOCOLO-OPENTV.md`): requiere mbedTLS en la PS3; **no implementado**. El primer arranque lee el canal HTTP del teléfono sin cifrado (solo red local confiable).
5. **Resolución/FPS**: 1080p60 en PS3 no está demostrado; empezar por 720p30 (preset de compatibilidad) y subir midiendo. La latencia inicial será de segundos.

## Instalar / compilar (cuando haya toolchain)
1. En un PC: instalar ps3toolchain y PSL1GHT; `export PS3DEV=… PSL1GHT=…`.
2. `cd receivers/ps3 && make` → `opentv-ps3.pkg`.
3. Copiar el PKG a la PS3 (USB) e instalarlo con "Install Package Files" (requiere CFW o PS3HEN activado en cada arranque; no todas las consolas/firmwares lo permiten).
4. Crear `/dev_hdd0/game/OPENTV001/USRDIR/opentv.cfg` con `host=IP_DEL_TELEFONO` y `port=PUERTO` (el que muestra la pantalla de IPTV; use dirección fija/corta).
5. En el teléfono: iniciar la emisión IPTV; abrir la app en la PS3.

## PS3 de fábrica como renderizador DLNA
La PS3 con firmware original **reproduce contenido de servidores DLNA** (es un cliente/reproductor); que acepte un *push* como renderizador (AVTransport) **no está comprobado**. Prueba: en la pantalla IPTV de OpenTV tocar "Buscar televisores DLNA"; si la PS3 aparece, probar "Enviar a". Si no aparece, no es renderizador y solo sirve el receptor nativo.

## Pruebas del núcleo en PC
`sh receivers/ps3/run_host_tests.sh` (gcc, ffmpeg, python3).
