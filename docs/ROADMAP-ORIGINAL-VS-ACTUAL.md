# Roadmap original vs ampliaciones vs estado real

**Recuperado textualmente**: el prompt maestro original (secciones 1–12 y la continuación Fase 3–7) está en los adjuntos del historial; lo que sigue lo resume sin inventar. **Reconstruido**: la distribución exacta de números de fase posteriores, deducida del código y de las notas NOTA-*.txt.

## 1. Roadmap ORIGINAL (F0–F7)
| Fase | Contenido original | Estado real |
|---|---|---|
| F0–F1 | Base Android TV/celular, Compose, navegación, tema, home | Hecho |
| F2–F3 | Pantallas de reproducción/medios locales, ajustes | Parcial: no hay explorador de archivos, Room, Coil ni «Continuar viendo» |
| F4 | Duplicar/transmitir pantalla (DLNA, Miracast, Wi‑Fi) | Hecho en parte: DLNA y Miracast nativo hechos y probados por el usuario; Wi‑Fi Direct `connect()` error 0 sin resolver |
| F5 | **Control remoto desde otro celular** (modo Control, emparejamiento, touchpad, texto, comunicación protegida, nearby/Wi‑Fi Direct) | **NO implementado** (`RemoteScreen` es «proyectar OpenTV» con D‑pad táctil) |
| F6 | Pulido, accesibilidad, release | **NO hecho** |
| F7 | Extensiones | No iniciado |
Objetivo principal original: conectividad y transmisión real de pantalla **y audio** con televisores y receptores externos.

## 2. AMPLIACIONES autorizadas posteriores
Congelado de resolución 16:9/Shizuku (protegido), diagnóstico Miracast Etapas A/B, rediseño R1–R3 con catálogo TMDB/RPDB (solo información), IPTV (HLS+M3U), protocolo OpenTV Link v1 (cifrado, emparejamiento por código), inventario de conexiones, evaluación REA (NO). Los números de fase 4/5/6 se reutilizaron para estas ampliaciones: de ahí la desviación de numeración.

## 3. Estado real (Fase 6.1)
Hecho y probado solo en lógica JVM/ffmpeg: IPTV, protocolo, audio de captura (código), perfiles 60 FPS (código). Sin compilar en Android ni probar en hardware. Faltan: emisor/receptor OpenTV Link en la app, receptor Android TV/HDMI, F5 Control remoto, DLNA/navegador (necesitan mediciones), Wi‑Fi Direct, F6 Pulido, explorador de archivos.

## 4. Desviaciones detectadas
1. El diseño/catálogo avanzó antes que el objetivo de conectividad (ahora se invierte la prioridad).
2. F5 y F6 originales sin hacer pese a haberse numerado fases posteriores.
3. Audio: durante meses solo pista en silencio; ahora hay captura real pendiente de prueba.
4. Fundamentos incompletos: Room/Coil/explorador, medición real de FPS y A/V sync.

## 5. Plan integrado (prioridad conectividad; Stremio postergado)
1. Emisor nativo con audio real + perfiles 720p60/1080p60 (6.1, hecho en código; **probar**).
2. IPTV: demostrar con LG/SmartOne que llega otro equipo y suena.
3. OpenTV Link: servicio emisor + receptor Android (Media3 con DataSource propio) y arquitectura para receptor HDMI/stick.
4. F5 Control remoto reutilizando el emparejamiento/cripto de OpenTV Link.
5. DLNA/navegador/Wi‑Fi Direct con medición.
6. F6 Pulido y matriz de pruebas por tecnología.
