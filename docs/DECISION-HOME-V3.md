# Decisión sobre OpenTV Home v3 (2026-11-17)
**Decisión: NO integrar ahora.** Se conserva el ZIP del usuario como referencia; no se copió código al proyecto (código sin integrar es justo lo que la auditoría de conectividad busca eliminar).
## Qué es
Módulo independiente de 12 archivos Kotlin (~1.960 líneas): launcher estilo Android TV (tarjetas 16:9, banner, filas, panel rápido), voz, protector de pantalla, «Seguir viendo» (sesiones de medios), notificaciones y perfiles locales. Su informe declara que **no se compiló ni se probó** y que su autor **nunca vio el proyecto principal**.
## Por qué no
1. **Riesgo para lo que importa ahora.** El APK solo se compila en GitHub Actions. Un error de compilación en cualquiera de los puntos que el propio informe marca como probables (`focusRestorer`, `Card`/`CardDefaults` de tv-material, `Crossfade`, `NotificationListenerService`) bloquea el APK y con él TODAS las pruebas de conectividad pendientes, que son la prioridad declarada.
2. **Dependencia nueva** (`androidx.tv:tv-material:1.1.0`) y permisos/servicios nuevos (`NotificationListenerService`, `BIND_DREAM_SERVICE`, `REQUEST_DELETE_PACKAGES`, `RECORD_AUDIO` para voz) sin poder validarlos aquí.
3. **Duplica lo que OpenTV ya tiene:** inicio, grilla de apps, ajustes rápidos, ya integrados con su propio tema y navegación; el módulo trae su propia actividad, tema y estado.
4. **Privacidad:** leer notificaciones de otras apps en una pantalla de TV es una decisión de producto que merece su propia revisión.
## Qué sí valdría la pena después (cuando la conectividad esté validada y un APK limpio compile)
- «Seguir viendo» por sesiones de medios y el foco recordado por fila: son lo que más aporta y lo menos intrusivo. Se integraría de a uno, con el APK compilado entre medio.
- Perfiles, voz, protector de pantalla y notificaciones: opcionales; sin urgencia.
