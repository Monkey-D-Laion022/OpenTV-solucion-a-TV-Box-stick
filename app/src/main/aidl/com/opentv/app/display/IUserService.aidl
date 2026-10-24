package com.opentv.app.display;

// Servicio que Shizuku ejecuta con permisos de «shell». Solo acepta comandos `wm size` y `wm density`.
interface IUserService {
    // Código reservado por Shizuku para cerrar el servicio.
    void destroy() = 16777114;

    String exec(String command) = 1;
}
