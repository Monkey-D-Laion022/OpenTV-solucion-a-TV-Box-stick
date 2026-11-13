# Cierre de conectividad — parte 1 (PS3 y Miracast)

Alcance de esta entrega: **PS3 (cripto + HELLO + modo de emparejamiento)** y **Miracast (servicio en primer plano de MediaProjection)**.
**NO incluido todavía:** DLNA (timeouts/Range), navegador `/tv` del LG NetCast, consolidación de menús, propuesta de protocolo propio. Siguen pendientes.

## PS3
**Causa raíz de `CRIPTO FALLA: HMAC-SHA256, mbedTLS -20736`.** -20736 = -0x5100 = `MBEDTLS_ERR_MD_BAD_INPUT_DATA`, lo devuelve `mbedtls_md_hmac()` (md.c) cuando `md_info` es NULL.
El Makefile compilaba el receptor con `-I$(PS3DEV)/portlibs/ppu/include` *antes* que las cabeceras de mbedTLS 3.6.2 de `build/deps`. Esa carpeta del SDK trae **mbedTLS 2.28.10** (y un faad antiguo):
las cabeceras 2.28 tapaban a las 3.6.2, y el enum `mbedtls_md_type_t` cambió entre versiones (SHA256 = 0x09 en 3.6.2). El receptor pedía "SHA256" con el número de la 2.x y la biblioteca 3.6.2 devolvía NULL.
Solo ocurría en la PS3 porque mis pruebas en PC usaban las cabeceras correctas. Comprobado con `ppu-gcc -M`: con el orden anterior `mbedtls/md.h` salía de `portlibs/`; con el nuevo, de `build/deps/mbed-out/include`.
Correcciones: orden de `-I` (sin portlibs), objetivo `check-includes` (falla si alguna fuente toma cabeceras mbedTLS/faad de portlibs), `#error` si la cabecera no es 3.6.x y comprobación en la autoprueba de que SHA-256 existe en MD.
**Causa de `-5` en HELLO.** `-5` = `OT_LINK_ERR_PEER`: el teléfono envió un ERROR. `main.c` apagaba el modo EMPAREJAR tras *cualquier* intento (aunque fallara): el siguiente intento enviaba HELLO en modo reconectar,
el teléfono (sin secreto) respondía ERROR NOT_PAIRED. Ahora el modo solo se apaga si el secreto se guardó, un ERROR NOT_PAIRED se traduce a `-7` y activa EMPAREJAR, y el mensaje de fallo muestra etapa y código mbedTLS.
**Lectura TCP.** Ya era robusta (bucle con `poll`/`recv` hasta completar cabecera y cuerpo, timeouts, cierre); se comprobó enviando 1 byte por vez. **El protocolo y el emisor Android no se tocaron.**

Archivos: `receivers/ps3/Makefile`, `core/ot_selftest.c`, `core/ot_link.c`, `src/main.c`, `test/run_link_tests.sh`, `tools/sender-java/KotlinParitySender.java`.

| Tipo | Resultado |
|---|---|
| PC: 14/14 pruebas de Link (incl. réplica del emisor Android, 1 byte por vez, teléfono desconocido), mDNS, TS, HTTP, autoprueba cripto | PASAN |
| Compilación real: ppu-gcc 13.2.0, `make pkg verify`, 9/9 estructurales, PKG 353 600 B | PASA |
| **Hardware (pendiente):** autoprueba `Cripto OK` en pantalla, HELLO, SAS idéntico, ACCEPT, STATS/PONG, video y audio | **SIN PROBAR** |
Limitación: nada de lo anterior demuestra que el receptor funcione en la consola; el resto de errores de PowerPC solo aparecerá en la PS3.

## Miracast (Wi-Fi Direct al LG)
**Causa raíz de "0 paquetes RTP" + "Media projections require a foreground service of type mediaProjection".** El camino Miracast (`WfdSourceStreamer`) creaba `CapturePipeline` (que llama a `getMediaProjection()`) **sin arrancar ningún servicio en primer plano**; IPTV, DLNA y OpenTV Link sí arrancan `IptvService` (declarado `mediaPlayback|mediaProjection`) antes. Desde Android 14 eso lanza `SecurityException`.
Corrección: `WfdSourceStreamer.start` arranca `IptvService`, espera a que esté en primer plano (máx. 3 s, en un hilo aparte, sin bloquear el hilo RTSP), y recién entonces crea la captura; si no arranca informa el error en vez de seguir; `stop()` libera el servicio si ninguna otra transmisión lo usa. El mensaje de la sesión ya no dice "Transmitiendo": dice que **inicia** y que hay que verificar paquetes RTP > 0.
Verificado por lectura: manifiesto con `FOREGROUND_SERVICE_MEDIA_PROJECTION` y tipos correctos; `registerCallback` antes de `createVirtualDisplay` (requisito de Android 14+) y callback `onStop` ya existentes en `CapturePipeline`.
Archivos: `app/src/main/java/com/opentv/app/miracast/WfdSourceStreamer.kt`, `WfdDiagnosticSession.kt` (solo texto).
**NO compilado ni probado:** mi entorno no tiene Android SDK; hay que compilar en Actions (`:app:testDebugUnitTest`). Miracast **no** se declara funcional: falta confirmar en el LG contadores RTP > 0 e imagen.
Protegidos sin tocar: ScreenResizer, PrefsResizeStore, Shizuku, lógica 16:9, `CatalogLogicTest.kt`; sin `wm density`.

## Aplicar
Copiar el ZIP sobre el repo (carpeta raíz `OpenTV/`), ejecutar `build.yml` y probar: (1) PS3: pantalla inicial `Cripto OK`; luego Enviar a TV y anotar el texto en pantalla. (2) Miracast: repetir la prueba y reportar el resumen "RTP enviados".
