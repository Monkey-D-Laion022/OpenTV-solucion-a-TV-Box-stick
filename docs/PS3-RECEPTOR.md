# Receptor OpenTV para PlayStation 3 (6.7)

La PS3 es el **receptor**; el teléfono (la app OpenTV) es el **emisor**. No se instala Android en la PS3 ni se convierte un APK.
Archivos: `receivers/ps3/` (código), `receivers/ps3/build/opentv-ps3.pkg` (instalable, ver estado), `docs/PS3-TOOLCHAIN-CLANG.md`.

## Estado real (leer antes de instalar)
| Pieza | Estado |
|---|---|
| Núcleo C portable: demux MPEG-TS (`ot_ts`), cliente HTTP (`ot_http`), **OpenTV Link v1 receptor** (`ot_link`: ECDH P-256, HKDF, AES-256-GCM, código SAS, reconexión con secreto), **anuncio mDNS** (`ot_mdns`) | **Implementado y probado en PC**: Link contra un emisor Python independiente (11/11), TS (3/3), mDNS (10/10) |
| Capa PS3 `src/ps3_av.c`: pantalla (gcm), decodificación H.264 (cellVdec), AAC (faad2), audio (cellAudio), mando, texto en pantalla | **Escrita, compilada y enlazada. Nunca ejecutada** en PS3 ni emulador |
| Programa principal `src/main.c` (escucha 8766, mDNS, emparejamiento con código en pantalla y mando, reconexión, respaldo HTTP) | **Compilado y enlazado. Nunca ejecutado** |
| PKG `opentv-ps3.pkg` | **Generado y listado** (`pkg -l`: `ICON0.PNG`, `PARAM.SFO`, `USRDIR/EBOOT.BIN`, `opentv.cfg.example`). **No instalado ni probado** en consola |

**Qué NO se pudo verificar y por qué:** no hay PS3 ni emulador utilizable en el entorno de desarrollo. Por eso no está comprobado que arranque, que cellVdec acepte la secuencia usada, que el video se vea, que el audio suene ni la sincronía. **1080p60 en PS3 no está demostrado** (límite del decodificador de la consola y de su red); por defecto la PS3 pide al teléfono 30 fps cuando la oferta supera 720p (`fps1080=` en `opentv.cfg`) y un tope de bitrate (`maxkbps=`, 12000). **720p60 es la referencia a probar primero.**

## Requisitos de la consola
Firmware personalizado (CFW) o PS3HEN activo: el PKG es de tipo «debug/homebrew» y su `EBOOT.BIN` usa un SELF no firmado oficialmente. Una PS3 de fábrica **no** lo instalará.

## Instalar
1. Copiar `opentv-ps3.pkg` a un USB (raíz). En la PS3: *Juego → Instalador de paquetes → Instalar archivos de paquete*. (Con HEN: activar HEN antes.)
2. (Opcional) crear `/dev_hdd0/game/OPENTV001/USRDIR/opentv.cfg` a partir de `opentv.cfg.example` (nombre, respaldo HTTP, topes).
3. Abrir «OpenTV Receptor». La pantalla muestra la **IP y el puerto 8766**.

## Probar con el teléfono (Redmi 14C → PS3)
1. PS3 y teléfono en la misma red. Primera vez: en la PS3 el modo ya es **EMPAREJAR** (no hay teléfonos guardados; ✕ lo alterna).
2. En OpenTV (teléfono): *OpenTV Link → Enviar a TV* y elegir la PS3 (si el descubrimiento no la lista, escribir la IP:8766).
3. La PS3 muestra un **código de 6 dígitos**; el teléfono muestra otro: deben ser **iguales**. En la PS3: ✕ = coincide, ○ = no.
4. Tras emparejar empieza el video. Siguientes veces: la PS3 reconoce al teléfono por el secreto guardado (`peers.dat`) sin código.
5. Mando: □ muestra/oculta datos (resolución, fps, ms de decodificación, A/V, descartes); START corta la sesión; △ abre el respaldo HTTP (si `host=` está en `opentv.cfg`: `http://IP:PUERTO/live.ts` del canal IPTV).
6. Anotar y enviar: qué pantalla ves, el texto de la línea inferior y los datos de □. Es la única forma de saber qué parte falla.

## Seguridad
El secreto de reconexión se guarda **sin cifrar** en `peers.dat` (la PS3 no ofrece un almacén de claves accesible); quien tenga acceso al disco de la consola podría hacerse pasar por ella ante ese teléfono. Es una limitación asumida. El protocolo no fue auditado por terceros (ver `docs/PROTOCOLO-OPENTV.md`).

## Compilar de nuevo
`make -C receivers/ps3 ci PS3DEV=<raíz del SDK>` (ver `docs/PS3-BUILD-CI.md`; reemplaza al antiguo `build_pkg.sh`).

## Pruebas en PC
`sh receivers/ps3/run_host_tests.sh` (gcc, ffmpeg) y `sh receivers/ps3/test/run_link_tests.sh RUTA_LINK_HOST CLIP.ts` (requiere compilar `test/link_host.c` con mbedTLS; ver el script). Resultados en `docs/NOTA-PRUEBAS-6.7.md`.

## PS3 de fábrica como renderizador DLNA
La PS3 con firmware original **reproduce contenido de servidores DLNA**; que acepte un *push* (AVTransport) **no está comprobado**. En IPTV: «Buscar televisores DLNA»; si aparece, probar «Enviar a».

## Diagnóstico en pantalla (Link)
- Al arrancar, la PS3 ejecuta una **autoprueba criptográfica** (HMAC‑SHA256, HKDF, AES‑256‑GCM, ECDH P‑256 con vectores de RFC 4231/5869/NIST/RFC 5903). Si falla, la pantalla inicial muestra `CRIPTO FALLA: <primitiva>`; si pasa, la salida de depuración dice `Cripto OK`.
- Si una sesión termina con error, el mensaje indica **código, etapa y código de mbedTLS**, por ejemplo `Sesion terminada: codigo -6 en etapa 'ECDH' (mbedtls -0x...)`. Etapas: rng, ecp_load, ecdh_gen, enviar HELLO, esperar/leer HELLO del telefono, clave publica del telefono, ECDH, HKDF, AES-GCM clave, mostrar codigo, enviar/esperar/validar PAIR_CONFIRM, datos.
- Verificación del protocolo contra el emisor Android: `tools/sender-java/KotlinParitySender.java` replica 1:1 `OtHandshake.asSender` con las mismas primitivas JCA (`java KotlinParitySender.java HOST PUERTO pair ARCHIVO_PSK`). Prueba en PC: emparejamiento, SAS idéntico, ACCEPT, STATS y PONG OK. NO prueba la PS3 real.
