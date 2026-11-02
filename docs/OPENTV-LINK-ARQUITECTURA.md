# OpenTV Link: teléfono → televisor (arquitectura y límites reales)

## Corrección de rumbo
La versión 6.3 dejó OpenTV Link orientado a «un teléfono con otro teléfono». **No era el objetivo.** El objetivo es
**TELÉFONO (emisor) → TELEVISOR o receptor HDMI (destino)**. Desde la 6.4 la interfaz, los textos y el sentido de la
conexión van en esa dirección. Un teléfono usado como receptor sigue sirviendo como equipo de pruebas, pero no se
presenta como la solución.

## Qué necesita un televisor para entender un protocolo nuevo
Un televisor solo reproduce lo que su software sabe decodificar. OpenTV Link envía MPEG‑TS (H.264 + AAC) dentro de un
canal cifrado propio: **hace falta un programa receptor** que lo descifre y lo reproduzca. Tres caminos reales:

| Destino | ¿Puede ejecutar el receptor OpenTV? | Estado |
|---|---|---|
| TV Android / Google TV (p. ej. Noblex X7) | Sí: es la misma app OpenTV (pantalla «Recibir con OpenTV Link») | Implementado; sin probar |
| **Receptor HDMI externo** (TV box o stick Android económico conectado al HDMI del televisor, con OpenTV instalado) | Sí | Implementado; sin probar. **Es la solución para televisores que no pueden instalar nada** |
| LG NetCast (42LB5800-SB) | **No de forma nativa** (ver abajo) | Usar el receptor HDMI, o IPTV/navegador (sin garantía de decodificación) |

### LG NetCast
Lo que pude comprobar en la documentación pública de LG: las aplicaciones de NetCast son **aplicaciones web** (HTML/JavaScript
empaquetadas en ZIP, con la API Web de LG y el plugin de reproducción de medios), no programas nativos; el HTML5/CSS3 de NetCast es
más reducido que el de webOS ([Comparison with NetCast](https://webostv.developer.lge.com/develop/specifications/comparison-netcast),
[NetCast SDK Overview](https://webostv.developer.lge.com/more/netcast/sdk-overview)).
Consecuencias:
1. No se puede instalar un receptor Android/nativo en ese televisor.
2. Un receptor web tendría que descifrar AES‑GCM y decodificar H.264 en JavaScript: sin API de decodificación propia, no es viable a 1080p.
3. **No pude confirmar** si un particular puede instalar una app web propia en ese modelo sin pasar por la tienda de LG; por eso no se promete.
4. Lo que sí hizo tu LG en pruebas: DLNA con TS H.264 y el espejo nativo Miracast a ≈1920×1080 a 30 Hz (sin medición de FPS real).

Conclusión de diseño: para el LG 42LB5800-SB, OpenTV Link llega **a través de un receptor HDMI barato**; el televisor solo muestra HDMI.
No se promete compatibilidad universal ni 1080p60: depende del emisor, del receptor y de la red, y se mide.

## Arquitectura por capas (cada una separada en el código)
| Capa | Dónde | Notas |
|---|---|---|
| Transporte y tramas | `proto/OtWire.kt`, `OtLink.kt` (`OtChannel`) | Tramas con tipo/flags/largo; TCP |
| Emparejamiento | `OtHandshake` | ECDH P‑256 + código de 6 dígitos (SAS) comparado en las dos pantallas |
| Autenticación y reconexión | `OtHandshake` (modo 1) | HMAC con el secreto guardado; no repite el emparejamiento |
| Cifrado | `OtCrypto`, `OtFrameCipher` | AES‑256‑GCM, contador anti‑repetición, flags en el AAD |
| Control de calidad / bitrate | `OtAdaptation.kt`, `OtBitrateController` | El receptor informa búfer y cuadros descartados; el emisor ajusta el bitrate |
| Video y audio | `iptv/CapturePipeline.kt` | MediaProjection → H.264 por hardware → TS; audio de reproducción o pista en silencio |
| Emisor Android | `link/LinkSenderController.kt` | **Se conecta al receptor elegido** (modo televisor) o, en modo avanzado, espera |
| Receptor Android | `link/LinkReceiverController.kt` | **Escucha** (modo televisor), se anuncia por DNS‑SD; ventana de emparejamiento de 2 min |
| Descubrimiento | `link/LinkNsd.kt`, `proto/OtDiscovery.kt` | `_opentv._tcp`; el TXT dice si es emisor o receptor |

### Flujo del modo televisor (6.4)
1. En el televisor/TV box: OpenTV › Conexiones › Recibir con OpenTV Link › «Esperar al teléfono». Se abre el puerto 8766 y se anuncia en la red.
2. Primera vez: en el receptor «Emparejar un teléfono» (abre 2 minutos). Sin esa ventana un teléfono desconocido es rechazado.
3. En el teléfono: Conexiones › Emitir con OpenTV Link › elegir el receptor encontrado (o escribir `IP:8766`).
4. Se muestra el mismo código de 6 dígitos en ambos; se confirma en los dos.
5. El teléfono envía OFFER (resolución/FPS/bitrate); el receptor responde ACCEPT y empieza el video cifrado.
6. Si se corta, el teléfono reintenta cada 3 s (hasta 20 veces) con el secreto guardado; no hay que emparejar de nuevo.
El saludo no depende de quién abre el TCP: el receptor habla primero. Prueba automática con sockets reales (`OtListeningReceiverTest`).

## Qué NO está hecho ni probado
- Nada de esto se probó en un televisor, TV box ni red real; el audio y el A/V sincronizado tampoco.
- El receptor no tiene servicio en primer plano ni notificación: debe estar abierto en pantalla (es lo normal en un TV box).
- Sincronía audio/video entre emisor y receptor: depende del reproductor; no se midió.
- Pérdida de paquetes: TCP no pierde, pero se retrasa; el receptor pide fotograma clave si su cola desborda.
- Receptor web para navegadores antiguos: descartado (ver LG NetCast); para eso existe la página `/` del IPTV.
