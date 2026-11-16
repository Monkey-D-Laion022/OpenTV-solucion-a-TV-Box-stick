# Auditoría: «Shisoku», ZIP de pantalla inicial, envío de URL y User-Agent (2026-11-16)
## «Shisoku»
Búsqueda en todo el repositorio (código, scripts, docs): **0 coincidencias**. No existe ni está definido. Se parece al nombre de **Shizuku** (integración protegida, usada solo para `wm size`), que no estabiliza redes. No se inventó una implementación. Solución real ya presente y suficiente: cada sesión tiene supervisión propia (`SessionSupervisor`, reintentos con espera creciente, `streamAlive`) y el receptor PS3 reinicia su estado por sesión. Una inicialización global previa añadiría un bloqueo sin beneficio verificable. Si el informe de Gemini define el protocolo, que se aporte la definición.
## ZIP `opentv-home` (launcher estilo Android TV, 1111 líneas)
Funciones: pestañas, favoritos/recientes, voz, accesos al sistema, protector de pantalla. Requiere `androidx.tv:tv-material` (no está en el proyecto), `REQUEST_DELETE_PACKAGES`, un segundo intent HOME (su propio SETUP dice que no debe duplicarse) y **no fue compilado**. OpenTV ya tiene inicio, apps y ajustes. No aporta nada a la conectividad. **Decisión: no incorporar.** Reevaluar cuando se cierre la etapa de conexiones.
## Envío de URL a reproductores externos
Ya existe: «Transmitir a TV» (DLNA) envía una dirección al televisor. No se agregó otra función; el selector de un toque queda como mejora de interfaz posterior.
## User-Agent en /tv
El script de `/tv` detecta capacidades con `canPlayType` en el propio navegador, más fiable que el User-Agent (NetCast 2014 anuncia Chrome 22/WebKit). No se agregó entrega por User-Agent; queda abierta hasta ver en la LG qué formato reproduce.
