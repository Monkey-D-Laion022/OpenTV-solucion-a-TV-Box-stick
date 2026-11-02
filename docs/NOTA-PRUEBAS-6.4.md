# OpenTV Fase 6.4 · una sola tanda de pruebas

**Aviso:** NO fue compilada para Android en mi entorno (sin SDK). Pasaron 404 pruebas de lógica (Kotlin 2.0 y 1.9, main y test como módulos separados), el MP4 se validó con ffmpeg y un chequeo de tipos con stubs (no es el SDK). Subila a GitHub y verificá que Actions compile; si falla, pasame el error.
Único permiso nuevo: WAKE_LOCK (normal, no se pide).

## 1) Teléfono (Redmi 14C) + Noblex X7: corte de IPTV y audio
1. IPTV → iniciar emisión (dirección corta). En el Noblex abrí `http://IP:8090/` y la lista M3U.
2. Salí de Conexiones, abrí otras pantallas y apps durante 2 minutos. Esperado: la emisión sigue. Si se corta, volvé a IPTV y copiá el texto «Última parada: …».
3. Con la emisión activa y un video con sonido: activá «Audio solo en TV». Esperado en 3–10 s: el estado pasa a «Teléfono silenciado…» y luego «Audio solo en TV…». Informá: ¿el TV suena? ¿el teléfono NO suena? ¿la imagen sigue estable? Si el estado dice «No se pudo confirmar», copiá el texto.
4. Detené la emisión: el volumen del teléfono debe volver al que tenías.

## 2) Teléfono → TV box/stick Android por HDMI (OpenTV Link)
Hace falta un TV box o stick Android con OpenTV instalado, conectado al televisor (también sirve probar con el Noblex).
1. En el receptor: Conexiones → Recibir con OpenTV Link → «Esperar al teléfono» → «Emparejar un teléfono».
2. En el teléfono: Conexiones → Emitir con OpenTV Link → elegir el receptor → permitir captura. Comparar el código de 6 dígitos y confirmar en los dos.
3. Esperado: la pantalla del teléfono aparece en el televisor. Informá resolución/FPS que muestra el teléfono, el búfer del receptor y si hay audio.
4. Cortá el Wi‑Fi del teléfono 10 s: debe reconectar solo sin volver a emparejar.
Si el receptor no aparece en la lista, escribí `IP:8766` a mano e informá si la lista automática falló.

## 3) LG 42LB5800-SB
- Navegador del TV: `http://IP:8090/`. Informá qué método reprodujo (HLS, MP4 o TS) o el mensaje final.
- OpenTV Link no corre en este televisor (es NetCast): solo con receptor HDMI.

## 4) Wi‑Fi Direct (diagnóstico)
- Repetí el diagnóstico con el LG: informá si `connect()` sigue dando error 0 y copiá el texto del resultado.

Informar siempre: modelo, qué pasó vs. lo esperado y el texto exacto en pantalla si falla.
