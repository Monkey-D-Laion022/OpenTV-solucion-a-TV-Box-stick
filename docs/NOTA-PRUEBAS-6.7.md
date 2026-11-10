# Pruebas 6.7 — resultados automáticos y guía para el Redmi 14C y el LG 42LB5800

Referencia: emisor Redmi 14C; televisor LG 42LB5800 (NetCast). No se asume nada del Noblex.

## A. Resultados automáticos (obtenidos en el entorno de desarrollo, sin Android SDK ni hardware)
| Prueba | Resultado |
|---|---|
| Lógica Android en JVM (Kotlin 2.0, K2) | **OK (505 tests)** |
| Misma lógica con `-language-version=1.9` | **OK (505 tests)** |
| Tipos del código Android contra stubs escritos a mano (NO es el SDK real) | 0 errores |
| Receptor PS3, demux TS (`run_host_tests.sh`) | 3/3 (trozos de 100000, 188 y 7 B: 150 cuadros, 5 clave, 236 tramas AAC) |
| Receptor PS3, mDNS | 10/10 |
| Receptor PS3, OpenTV Link (C + mbedTLS) contra emisor Python independiente | 11/11: mismo SAS en ambos lados; TS recibido idéntico byte a byte (1 138 716 B); ACCEPT limita a 30 fps/8 Mbps; STATS y PONG; reconexión con secreto; trama alterada → corte (rc −3); secreto equivocado → rechazo sin datos; teléfono desconocido → rc −7 |
| Receptor PS3: compilación + enlace + `sprxlinker` + `fself` + `pkg` | Genera `opentv-ps3.pkg` (958 720 B); `pkg -l` lista ICON0.PNG, PARAM.SFO, USRDIR/EBOOT.BIN, opentv.cfg.example |
Nota: `NativeMirrorFlowTest` (archivo protegido, sin cambios) falló 2 de 14 ejecuciones en sesiones anteriores (intermitente); en las últimas no falló.

**NO se obtuvo:** APK compilado (no hay Android SDK ni acceso a dl.google.com/maven.google.com: debe salir de tu GitHub Actions), ninguna prueba en hardware real (Redmi 14C, LG, PS3, Wi‑Fi Direct, Miracast).

## B. En el teléfono / LG (hardware real; pendiente de que lo pruebes)
1. **IPTV / calidad adaptativa**: iniciar emisión; la línea de tiempos muestra percentiles de intervalo, cuadros tardíos, cola del codificador, tiempo de codificación y la causa y el perfil actuales (con motivo de cada cambio). Anotar si sube a 1080p60 y si se mantiene o baja (y por qué causa: codificador/red/transporte/receptor/temperatura).
2. **Marca diagnóstica B/N en la esquina** (validada antes): sigue disponible para ver repetidos/saltos en el LG.
3. **Cambio de perfil en vivo**: con HLS el LG recibe un SPS nuevo en un fotograma clave; **cómo lo tolera el LG es desconocido**. Si la imagen se congela al cambiar, anotarlo.
4. **DLNA**: IPTV en marcha → «Buscar televisores DLNA» → «Enviar a …». Copiar el registro (formatos probados, motivo de cada fallo, recuperaciones e inestabilidad).
5. **Miracast/Wi‑Fi Direct**: correr el diagnóstico con la opción «Transmitir (M4→PLAY)» activada. Anotar hasta qué mensaje llega (M1…M7/PLAY) y si el LG muestra imagen.
6. **Link → PS3**: ver `docs/PS3-RECEPTOR.md`.
