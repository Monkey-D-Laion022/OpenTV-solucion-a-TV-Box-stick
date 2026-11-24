# Actualizar OpenTV encima del anterior (sin desinstalar)
**Es factible y quedó armado.** Android solo actualiza un APK sobre otro si coinciden tres cosas:
1. **Mismo `applicationId`** (`com.opentv.app`): el APK normal y el «con Home 2» (`-PwithNova=true`) usan el mismo; el Home 2 es un módulo dentro de la app, no otra app.
2. **Misma firma.** Antes cada ejecución de GitHub Actions firmaba con una clave de depuración distinta (la genera el servidor) → «App no instalada». Ahora todos los builds usan `app/opentv-debug.keystore`, un archivo del repositorio, así que cualquier workflow firma igual.
3. **`versionCode` igual o mayor.** Estaba fijo en 1. Ahora es `minutos desde 1970` al compilar: crece entre builds y **entre workflows** (el número de ejecución de GitHub NO sirve, porque cada workflow cuenta aparte y `Build` y `Home 2` se pisarían).

## Lo que hay que saber
- **Una última desinstalación** al pasar a este build: el APK que tenés instalado está firmado con la clave aleatoria vieja y Android no deja cambiarla. Desde el siguiente, se actualiza encima. Se pierden los datos de la app solo esa vez.
- Los datos y preferencias se conservan al actualizar (mismo paquete y firma). Los ajustes del Home 2 usan DataStore propio y no chocan.
- **Seguridad:** la clave es de pruebas y pública (contraseña `android`, como la de depuración estándar de Android). Sirve para instalar en tus equipos; no la uses para publicar en tiendas. Quien tuviera el archivo podría firmar una actualización falsa de tu app, algo irrelevante para una app personal instalada a mano.
- `.gitignore` ignoraba `*.keystore`; se agregó una excepción para este archivo. Si subís por la web de GitHub, comprobar que `app/opentv-debug.keystore` quedó en el repo (si falta, el build falla con «Keystore file not found»).
- Si un día cambiás `applicationId` o la clave, vuelve a hacer falta desinstalar.

## Qué se revisó
Firma (clave fija, formato PKCS12 aceptado por AGP 8.5), `versionCode` (entero < 2^31: hoy ≈ 29,4 millones, crece cada minuto), `applicationId` idéntico en ambas variantes, recursos del Home 2 con prefijo `home2_` (sin choques de nombre), y que el módulo `:homenova` no declara otra `Application` ni `sharedUserId`.
