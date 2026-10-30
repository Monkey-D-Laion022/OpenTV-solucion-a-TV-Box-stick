# Receptor de referencia de OpenTV Link

Prototipo independiente (Python 3.9+, `pip install cryptography`). No forma parte de la app Android.

```
python3 opentv_receiver.py --host IP --port 8765 --pair          # primera vez: compará el código con el del emisor
python3 opentv_receiver.py --host IP --port 8765 | ffplay -i -   # luego reconecta con el secreto guardado
```
El secreto queda en `~/.opentv-receiver.json` (permisos 0600). Especificación: `docs/PROTOCOLO-OPENTV.md`.

## Prueba de interoperabilidad que se corrió
Emisor Kotlin (`OtHandshake.asSender`) en loopback + este receptor: emparejamiento con el mismo código de 6 dígitos en ambos,
oferta/aceptación, 5 bloques TS cifrados idénticos byte a byte, PING/PONG, BYE, y segunda conexión por reconexión
con el secreto guardado. No se probó por Wi-Fi ni con video real.
