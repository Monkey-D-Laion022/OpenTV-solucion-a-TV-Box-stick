# Diagnóstico: 16:9 y orientación (HECHOS vs HIPÓTESIS)

## Hechos (leídos en el código de H2)
- `MainActivity` declara `android:screenOrientation="sensorLandscape"` y `configChanges` con `orientation|screenSize|screenLayout|smallestScreenSize|density`: la Activity no se recrea al cambiar el tamaño y pide horizontal con el sensor (esa variante no respeta el bloqueo de rotación del usuario). Afecta SOLO a las ventanas de OpenTV.
- El espejo captura TODA la pantalla (launcher y otras apps). Esas ventanas dependen de la rotación del sistema, no de la de OpenTV. `OrientationPolicy` solo avisa sobre apps externas que declaran vertical; no fuerza nada.
- Secuencia protegida de `ScreenResizer.apply` (H2): lee `wm size`, `wm density` y los ajustes de rotación; guarda qué deshacer; `wm size 1080x1920` (vertical: `wm size` es relativo a la orientación natural); si se pudo leer la rotación, `settings put system accelerometer_rotation 0` y `user_rotation 1`; vuelve a leer `wm size` y restaura si no coincide.
- La rotación se fija igual tanto si el usuario la tenía libre como bloqueada; lo que cambia es el estado previo.

## Observación del usuario (hardware)
Con la rotación bloqueada en vertical, el 16:9 queda girado/cortado; activando la rotación automática desde el panel y repitiendo, queda bien.

## Hipótesis (NO demostradas)
1. En HyperOS el ajuste de bloqueo del panel rápido no equivale a `accelerometer_rotation`/`user_rotation`, o el sistema ignora `user_rotation` mientras el bloqueo está activo.
2. Orden: el tamaño se aplica antes de que la rotación cambie, y el sistema queda con una combinación tamaño/rotación incoherente hasta que algo fuerza un recálculo (activar la rotación automática lo provoca).
3. El launcher o la app en primer plano declara su propia orientación y gana sobre `user_rotation`.
No hay evidencia para distinguirlas sin probar en el teléfono; no se hicieron pruebas con comandos.

## Opciones evaluadas
- A (solo la Activity de OpenTV): ya está hecho (`sensorLandscape`). No puede corregir otras ventanas ni la rotación global. Sin efecto sobre el problema.
- B (coordinar con la secuencia 16:9): implica cambiar el orden o los ajustes de rotación dentro de `ScreenResizer` (zona protegida). Se probó en el módulo R2 (liberar rotación antes de `wm size`): no se puede demostrar que sea seguro sin hardware, y el usuario pidió no modificar esa zona. DESCARTADA.
- C (aviso): elegida. Texto fijo en el Inicio; no cambia nada del teléfono.

## Cómo se evitaría un estado incorrecto
No se agrega ningún mecanismo: la restauración de H2 queda exactamente como está. Ante cualquier duda, recuperación manual conocida: `wm size reset` desde Shizuku/Termux (`rish -c "wm size reset"`).

## Pruebas que no tocan el teléfono
- Unitarias de `ScreenResizer` (H2, 16 pruebas) y guardia estática `ProtectedResolutionGuardTest`.
- Pendiente de hardware: repetir con rotación bloqueada y libre y anotar el resultado de `settings get system accelerometer_rotation` / `user_rotation` ANTES de forzar (solo lectura).
