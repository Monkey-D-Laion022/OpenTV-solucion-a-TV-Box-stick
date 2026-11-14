# OpenTV 6.7 — Parte 2: lo hecho, lo no hecho y lo que falta probar

## Hecho en esta entrega (acumulativo con `OpenTV-cierre-parte1.zip`)
### PS3 — cadena TCP → HELLO → emparejamiento → ACCEPT → TS → video/audio
- Dónde sale "se esperaba HELLO": en el **teléfono** (`OtLink.kt`, `asSender`). Se produce cuando la primera trama que llega de la PS3 no es HELLO: lo habitual es que la PS3 **cierre la conexión sin enviarlo** (o envíe un ERROR). El protocolo es correcto (réplica Java del emisor Android + 14 pruebas en PC), así que la PS3 está cortando antes de enviar su HELLO o la sesión anterior dejó el estado equivocado.
- Corregido: el modo EMPAREJAR ya no se apaga tras un intento fallido (parte 1); ERROR NOT_PAIRED del teléfono → `-7` y la PS3 pasa sola a EMPAREJAR; etapas completas en `ot_link.c` (rng, ecp_load, ecdh_gen, enviar HELLO, esperar HELLO, … AUTH, esperar OFFER y datos), reiniciadas en cada sesión (antes la etapa quedaba "pegada" de un intento anterior).
- **Registro persistente nuevo**: `/dev_hdd0/game/OPENTV001/USRDIR/opentv.log` (conexión aceptada y modo, estado de cada etapa, OFFER, primer bloque TS, y cada ~3 s: KB de TS, video recibido, imágenes decodificadas y mostradas, errores del decodificador, tramas de audio, subdesbordes, desfase A/V, resolución). Con ese archivo se ve **exactamente en qué etapa se corta** (TCP / HELLO / emparejar / ACCEPT / TS / decodificación / presentación), que es lo que pediste determinar. Hay que copiarlo de la PS3 (o leerlo con un gestor de archivos) y enviarlo.
- **No se encontró la causa de la pantalla negra**: sin ese registro y sin hardware no se puede afirmar cuál de las 9 etapas falla. **No se puede declarar funcional** y no lo es hasta una prueba en consola.
- Riesgo conocido: la inicialización de pantalla de `ps3_av.c` (secuencia oficial de PSL1GHT) y el hilo de audio nuevo solo están compilados; la versión anterior sí mostraba la interfaz en la PS3. Si la pantalla está negra desde que abre la aplicación (antes de conectar), el culpable es `ps3_av.c`: avisar y se restaura la inicialización anterior.
### Miracast / LG NetCast — M3 → M4
- El selector de formato (`WfdFormatChooser`) rechazaba al televisor y **M4 no se enviaba** si el nivel H.264 declarado era menor al habitual del modo (p. ej. 1080p30 con nivel 3.1/3.2). Nueva segunda pasada: usa el nivel más alto declarado y avisa en la nota. Y el mensaje de fallo ahora enumera los modos progresivos, perfiles y niveles que declaró el televisor (antes no se podía saber qué había anunciado). 2 pruebas nuevas; 31/31 pasan (`WfdSourceTest`, `WfdVideoFormatsTest`).
- Limitación honesta: si el LG solo declara modos que OpenTV no produce (p. ej. 640x480, 720x480), sigue sin haber formato y la negociación falla con ese texto detallado; producir otros tamaños exigiría presets nuevos en `IptvPreset`, que es el motor IPTV de referencia que no se debe tocar sin tu permiso.
- Arranque de MediaProjection corregido en la parte 1 (servicio en primer plano); sin compilar ni probar en hardware.

## NO hecho (dicho sin rodeos)
No avancé en: refactor de DLNA (timeouts/caída a 480p30), navegador `/tv` del LG, unificación de menús, ni implementación de una tecnología propia nueva o de Chromecast/AirPlay. No hice una unificación de interfaz Android a ciegas: sin Android SDK no puedo compilar ni probar cambios de interfaz, y un refactor así sin compilar pondría en riesgo lo que funciona.

## Investigación de viabilidad (sin implementar)
- **Tecnología propia:** ya existe en el proyecto el esqueleto: **OpenTV Link v1** (descubrimiento mDNS, ECDH P‑256 + código de verificación, AES‑256‑GCM, MPEG‑TS por TCP, ACCEPT/STATS para adaptar bitrate y fps, reconexión con secreto guardado). Es la línea propia; su primer receptor es la PS3. Su límite real hoy es el receptor, no el protocolo.
- **AirPlay (espejo):** requiere FairPlay para el espejo de pantalla; no hay implementación libre legal y estable para emitir desde Android: descartado.
- **Chromecast:** el emisor necesita Google Cast SDK y el receptor ha de ser un dispositivo/app Cast certificado; un televisor antiguo no lo es. Solo sirve para dispositivos Cast reales (el proyecto ya tiene código Cast en `connect/`).
- **DLNA/UPnP y navegador `/tv`:** son las rutas para el LG NetCast; pendientes de auditar en la parte 3. No se afirma compatibilidad sin evidencia.

## Pruebas
| Tipo | Resultado |
|---|---|
| PC: 14 pruebas de Link (protocolo, réplica del emisor Android, bytes sueltos, teléfono desconocido), autoprueba cripto, mDNS | pasan |
| PC: Kotlin puro `WfdSourceTest` + `WfdVideoFormatsTest` (31) | pasan |
| Compilación PS3 real (ppu-gcc 13.2.0) + verificación estructural del PKG (354 624 B) | pasa |
| Kotlin con dependencias Android (WfdSourceStreamer) | **no compilado** (sin SDK) |
| **Hardware: PS3 (HELLO→video/audio) y LG (M4→RTP)** | **sin probar** |

## Qué hacer ahora
1. Aplicar ambos ZIP, ejecutar `build.yml`, instalar el PKG.
2. Intentar transmitir al Redmi y **enviarme `opentv.log`** (USRDIR de la PS3) y el texto de pantalla.
3. En Miracast, enviar el registro del diagnóstico: ahora dice qué modos declaró el LG.
