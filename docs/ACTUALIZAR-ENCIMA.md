# Actualizar OpenTV encima del anterior (sin desinstalar)
**Es factible y quedó armado.** Android solo actualiza un APK sobre otro si coinciden tres cosas:
1. **Mismo `applicationId`** (`com.opentv.app`): el Home 2 es un módulo dentro de la app (ya va en el build normal), no otra app.
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

## Ronda 8: no funcionó en la prueba real — cómo diagnosticarlo
Se reportó que «actualizar no funcionó» sin el mensaje de error, así que la causa NO está confirmada. Para dejarla a la vista: **Ajustes → Sistema → «Compilación y firma»** muestra `compilación N · firma XXXXXXXXXXXX` del APK instalado. Con dos APK: si la firma (12 caracteres) **difiere**, Android rechaza la actualización (hay que desinstalar una vez); si la firma es igual y el N nuevo es **menor**, también la rechaza. Mensajes típicos: «App no instalada / paquete en conflicto» = firma distinta; «no se puede degradar» = N menor. Pasame el texto exacto y la pantalla de «Compilación y firma» de ambos APK.
Causas posibles aún sin descartar: el APK instalado venía de una compilación anterior a este cambio (otra firma, hay que desinstalar una vez); una restricción del instalador del teléfono (HyperOS) con APK de depuración; o `app/opentv-debug.keystore` no se subió al repositorio (en ese caso el build habría fallado con «Keystore file not found»).
