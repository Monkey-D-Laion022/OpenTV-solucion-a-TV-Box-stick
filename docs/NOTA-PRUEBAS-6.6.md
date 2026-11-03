# Nota de pruebas físicas — Fase 6.6 (una sola sesión)

Nada de esto está probado en equipos todavía. Anotá solo lo que ves. Los resultados negativos valen igual.

## 0. Instalación
Instalá la APK que genere Actions con esta versión (la pantalla de inicio dice «Fase 6.6»). Si Actions falla, mandame el error tal cual.

## 1. IPTV en segundo plano y FPS (LG con SS IPTV y Noblex Android)
1. Perfil 1080p60, ritmo fijo de cuadros ACTIVADO. Iniciá, cargá `/tv` en el televisor.
2. Salí de OpenTV a una pantalla quieta 2 minutos. Volvé y anotá: «Estado verificado», FPS dibujados/codificados, «Gobernador de FPS» (objetivo, causa, estado térmico) y «Última parada».
3. Repetí con el ritmo fijo DESACTIVADO.
4. Anotá si el gobernador bajó de 60 a 30 y qué causa dijo (dibujado, codificador, temperatura o red).

## 2. Wi‑Fi Direct (error 0)
Con el televisor en «Screen Share» / «Pantalla compartida» en espera, corré la prueba. Ahora NO llama a connect() si el sistema no ve al televisor: pasa a redescubrir (3 veces). Anotá: ¿conectó?, ¿dijo «cambió su dirección»?, ¿terminó en «no apareció en la lista»? Copiá el registro.

## 3. Espejo por navegador (Noblex Linux y LG)
Abrí la dirección del espejo. Anotá cuadros enviados / producidos / calidad JPEG. Con el televisor leyendo lento, la calidad debería bajar sola.

## 4. OpenTV Link (si tenés un receptor Android TV)
Receptor: «Escuchar». Teléfono: elegí el receptor, emparejá, transmití. Anotá fps, bitrate y si aparece una nota del gobernador.

## 5. Audio solo en TV
IPTV en el LG y el Noblex Android: debe seguir como en 6.4. Probalo también en Link.

## 6. No cambió (confirmar rápido)
Miracast nativo y DLNA siguen sin tocarse. El 16:9 debe seguir igual.
