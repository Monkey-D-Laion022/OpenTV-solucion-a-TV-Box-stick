# DLNA independiente (6.7) — `media/dlna/`

Implementación propia, sin importar `connect/` (DLNA anterior), `display/`, `TvProjection` ni pantallas protegidas (lo verifica `DlnaIndependenceTest`).

Flujo: `DlnaClient.discover` (SSDP M-SEARCH, MediaRenderer) → descripción UPnP → `GetProtocolInfo` (formatos que acepta el televisor) → `ProfileSelector` ordena ofertas (AVC_TS con AAC primero; descarta AC3/LPCM/MP3/CIF; MPEG_TS_* al final; perfil genérico que ya reprodujo en un LG, siempre de respaldo; 1080p30 o 480p30; nunca 60 fps) → `DlnaSession` envía `SetAVTransportURI`+`Play` y consulta `GetTransportInfo` cada 2 s → `SessionSupervisor` (puro) reintenta con espera creciente, pasa al formato siguiente y termina con el motivo mostrado en pantalla. Control: `Stop`, `Pause`, `Play` en `DlnaClient`.

Límites conocidos: la resolución del flujo es la del canal IPTV en curso (la sesión no cambia el preset del emisor todavía; `onOffer` queda como gancho); el audio AAC dentro del TS no está confirmado en el LG 42LB5800 (antes el LG aceptó TS con audio en silencio); 60 fps no se ofrece porque ningún perfil AVC_TS estándar lo declara. Nada de esto fue probado contra un televisor real.
