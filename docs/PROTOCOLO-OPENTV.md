# OpenTV Link v1 — protocolo de transmisión propio

Estado: **definido, implementado (`app/.../proto/`) y probado entre dos implementaciones** (emisor Kotlin ↔ receptor de
referencia en Python, `tools/receiver-py/`) por TCP en loopback. **No** está integrado todavía en la app Android (ni
emisor ni receptor) y **no** se probó entre dos equipos reales por Wi-Fi.

## Qué es propio y qué reutiliza estándares

| Capa | Estándar reutilizado | Propio de OpenTV |
|---|---|---|
| Descubrimiento | DNS-SD/mDNS (`NsdManager`), tipo `_opentv._tcp` | Contenido del registro TXT |
| Transporte | TCP | Formato de trama, versión, límites |
| Seguridad | ECDH P-256, HKDF-SHA256, HMAC-SHA256, AES-256-GCM | Cómo se combinan: comparación de código (SAS), secreto de reconexión, nonces implícitos |
| Video/audio | H.264 (hardware), AAC-LC, MPEG-TS | Nada: la carga de video es MPEG-TS estándar |
| Control | — | Mensajes OFFER/ACCEPT/STATS/CONTROL, control de bitrate, compuerta de envío |

No se inventa criptografía ni se promete superar al Wi-Fi: el protocolo agrega emparejamiento seguro, cifrado,
negociación y control sobre TCP + MPEG-TS. Como TCP no pierde datos, la "pérdida" aparece como retraso: se maneja
descartando lo viejo hasta el próximo fotograma clave (`OtSendGate`).

## Roles
- **Emisor** (el teléfono que transmite): escucha en TCP (puerto sugerido 8765) y se anuncia por DNS-SD.
- **Receptor** (TV, caja o app receptora): descubre al emisor y se conecta.

## Descubrimiento (DNS-SD)
Servicio `_opentv._tcp.`; TXT: `v=1`, `id=<16 hex>`, `n=<nombre ≤40>`, `r=s|r`, `c=<capacidades en hex>`.
Solo descubre; no autentica. El receptor debe tratar el TXT como dato no confiable (`OtDiscovery.parse`).

## Trama
```
+--------+--------+--------------------+----------------+
| tipo   | flags  | largo (u32, BE)    | carga          |
| 1 byte | 1 byte | 4 bytes            | `largo` bytes  |
+--------+--------+--------------------+----------------+
```
Largo máximo: 4 MiB. Tipos desconocidos o largos mayores cierran la conexión.

| Tipo | Nombre | Dirección | Cifrado |
|---|---|---|---|
| 0x01 | HELLO | ambos (1.º cada uno) | no |
| 0x02 | AUTH | ambos (reconexión) | sí |
| 0x03 | PAIR_CONFIRM | ambos (emparejar) | sí |
| 0x04 | ERROR | ambos | sí (en HELLO: sin cifrar) |
| 0x10 | OFFER | emisor → receptor | sí |
| 0x11 | ACCEPT | receptor → emisor | sí |
| 0x20 | TS_DATA | emisor → receptor | sí |
| 0x30 / 0x31 | PING / PONG | ambos | sí |
| 0x32 | STATS | receptor → emisor | sí |
| 0x40 | CONTROL | ambos | sí |
| 0x50 | BYE | ambos | sí |

## Mensajes (todo big-endian)
- **HELLO**: `versión u8 (=1) · rol u8 (1 emisor, 2 receptor) · deviceId 8 B · largoNombre u8 · nombre UTF-8 (≤64) · caps u16 · nonce 16 B · modo u8 (0 emparejar, 1 reconectar) · largoClave u8 · clave pública P-256 (65 B sin comprimir; vacía al reconectar)`.
  Caps: `0x1 H.264 · 0x2 AAC · 0x4 MPEG-TS · 0x8 control`.
- **OFFER** (14 B): `códecVideo u8 (1=H.264) · ancho u16 · alto u16 · fps u8 · bitrate u32 · códecAudio u8 (0 ninguno, 1 AAC-LC) · contenedor u8 (1=MPEG-TS)`.
- **ACCEPT** (8 B): `aceptado u8 · bitrate u32 · fps u8 · búferObjetivoMs u16`. El receptor puede pedir menos de lo ofrecido.
- **TS_DATA**: bloques de MPEG-TS (múltiplo de 188 B). Flag `0x01` = el bloque empieza en un fotograma clave (lleva PAT/PMT). Un receptor nuevo descarta todo hasta el primero con ese flag.
- **STATS** (10 B, 1 vez por segundo): `búferMs u16 · descartadas u32 · tarde u32`.
- **CONTROL** (5 B): `comando u8 · argumento u32`. 1 pausa, 2 reanudar, 3 detener, 4 pedir fotograma clave, 5 fijar bitrate.
- **ERROR**: `código u16 · mensaje UTF-8`. 1 no emparejado, 2 autenticación fallida, 3 rechazado, 4 no soportado, 5 ocupado.
- **PING/PONG**: 8 bytes opacos que PONG repite (mide el RTT).

## Seguridad
1. **Emparejar (modo 0)**: ambos generan un par ECDH P-256 efímero y lo mandan en HELLO. `Z = ECDH`. Se valida que el punto recibido esté en la curva.
2. **Claves de sesión**: `okm = HKDF-SHA256(salt = nonceE‖nonceR, ikm = Z, info = "OTV1 session", 64 B)`; los primeros 32 B cifran emisor→receptor y los otros 32 B receptor→emisor.
3. **Código de comparación (SAS)**: `HMAC-SHA256(Z, "OTV1 sas" ‖ HELLO_emisor ‖ HELLO_receptor)`, primeros 4 bytes como entero mod 1 000 000, 6 dígitos. **El usuario compara el código en ambos equipos**; si difiere, hay un intermediario. Cada lado manda `PAIR_CONFIRM` (1 = coincide, 0 = no) y solo si ambos confirman guarda el secreto.
4. **Secreto guardado**: `HKDF(salt = SHA256(HELLO_e‖HELLO_r), ikm = Z, info = "OTV1 psk", 32 B)`.
5. **Reconectar (modo 1)**: sin clave pública; `ikm` = secreto guardado. Cada lado manda `AUTH = HMAC(psk, "OTV1 auth" ‖ rol u8 ‖ HELLO_e ‖ HELLO_r)` y verifica el del otro en tiempo constante.
6. **Cifrado de tramas**: AES-256-GCM, etiqueta de 16 B. Nonce de 12 B = `dirección u8 (1 emisor→receptor, 2 receptor→emisor) ‖ 000 ‖ contador u64`, que sube en cada trama y **no viaja**. AAD = la cabecera de 6 B tal como viaja (su largo es el del texto cifrado, es decir texto + 16). Cualquier alteración, reordenamiento o repetición falla la autenticación y cierra la conexión.

Límites asumidos: el secreto guardado no da secreto hacia adelante entre sesiones de reconexión (sí lo da el emparejamiento inicial); el protocolo no oculta que existe tráfico OpenTV ni su tamaño; no hay revocación remota (se borra el secreto en cada equipo). No ha sido auditado por terceros.

## Flujo
```
Receptor                                  Emisor
 HELLO (modo, nonce, [clave]) ───────────▶
                              ◀─────────── HELLO (modo, nonce, [clave])
 [PAIR_CONFIRM | AUTH] ◀───────────────────▶ [PAIR_CONFIRM | AUTH]      (ya cifrado)
                              ◀─────────── OFFER
 ACCEPT ─────────────────────────────────▶
                              ◀─────────── TS_DATA … (con PING cada tanto)
 STATS (1/s), PONG, CONTROL ──────────────▶
                              ◀─────────── CONTROL, BYE
```
Reconexión ante un corte: el receptor vuelve a conectar en modo 1; el emisor envía TS_DATA desde el próximo fotograma clave.

## Sincronización y latencia
El audio y el video viajan juntos en el mismo flujo MPEG-TS con PTS/PCR: la sincronización la hace el reproductor del receptor
(en Android TV, Media3 ExoPlayer con una fuente de datos propia). El receptor informa el búfer en STATS y el emisor ajusta el
bitrate con `OtBitrateController` (baja ×0,7 ante descartes/búfer alto; sube ×1,2 tras 5 informes sanos). El retraso real
depende del reproductor y de la red y **no está medido**.

## Cómo probarlo hoy (sin la app)
```
python3 tools/receiver-py/opentv_receiver.py --host <IP del emisor> --port 8765 --pair      # primera vez
python3 tools/receiver-py/opentv_receiver.py --host <IP del emisor> --port 8765 | ffplay -i -
```
(requiere un emisor: por ahora solo el de pruebas de la JVM; ver `OtLink.kt`).

## Pendiente
Servicio emisor en Android (reutiliza la captura del canal IPTV y publica TS_DATA), receptor para Android TV (Media3), publicación
NSD, almacenamiento cifrado de los secretos (`OtPeerStore` sobre Keystore) y prueba con dos equipos reales.
