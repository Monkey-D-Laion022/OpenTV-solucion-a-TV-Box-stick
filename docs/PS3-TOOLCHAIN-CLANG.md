# Toolchain de PS3 con clang (cómo se compiló el receptor)

**Por qué existe:** el entorno de desarrollo no tenía `ps3toolchain` (GCC 7 para PPU) y no se pudo compilar GCC completo. Se armó una cadena **alternativa y experimental**:
clang 18 (backend PowerPC) + lld, la libc de newlib (parcheada para PS3, tomada de ps3toolchain), PSL1GHT (cabeceras, crt0, librerías `libnet`, `libvdec`, `libaudio`, `libgcm_sys`, …) y las herramientas del SDK (`sprxlinker`, `fself`, `sfo`, `pkg`).
**Es una cadena no oficial**: el binario resultante **nunca se ejecutó** en una PS3 ni en un emulador. Lo que sí se verificó: compila, enlaza sin símbolos sin resolver, `sprxlinker` rellena `.lib.stub`, `fself` genera el `EBOOT.BIN` y `pkg` lista los 5 archivos esperados.

## Piezas (en `receivers/ps3/toolchain/`)
- `ppu-gcc.clang-wrapper`: envoltorio de `clang --target=powerpc64-unknown-elf -mcpu=970` que imita a `ppu-gcc` (quita flags de GCC que clang no entiende, enlaza con `lv2.ld`, crt0/crti/crtn y `lv2-sprx.o`).
- `psl1ght-clang.patch`: cambios a PSL1GHT (commit en `PSL1GHT-commit.txt`): `__start` global y `ENTRY(__start)` en `lv2.ld`; `ATTRIBUTE_PRXPTR` vacío bajo clang.
- `ppc-asm.h`: cabecera de GCC necesaria para ensamblar `.S` con clang.
- Versiones: clang 18.1.3; mbedTLS v3.6.2; faad2 (commit en `faad2-commit.txt`).

## Pasos de reconstrucción (resumen)
1. Compilar newlib 1.20.0 + parche PS3 con clang para `powerpc64-ps3-elf` (con `-nostdlibinc`, sin los `limits.h` del anfitrión).
2. Compilar PSL1GHT (`ppu/`, `libsysmodule`, `librsx`, stubs `sprx/*`) con el wrapper; aplicar `psl1ght-clang.patch`.
3. Compilar las herramientas (`sprxlinker`, `fself`, `sfo`, `pkg`) para el anfitrión (libelf; sin gmp no hay `make_self` real → se usa **fself**).
4. `faad2`: `-DLC_ONLY_DECODER -DDISABLE_SBR` (solo AAC-LC, que es lo que emite OpenTV).
5. mbedTLS con `third_party/ot_mbedtls_config.h` (solo ECDH P-256, HKDF, HMAC-SHA256, AES-GCM, CTR-DRBG; `MBEDTLS_NO_UDBL_DIVISION` porque clang/PPU no trae `__udivti3`).
6. `sh receivers/ps3/build_pkg.sh` → `build/opentv-ps3.pkg`.

## Advertencias conocidas (riesgos reales)
1. **Punteros del sistema de 32 bits**: PSL1GHT usa `__attribute__((mode(SI)))` en campos puntero de estructuras que lee el sistema. Clang no lo admite; ahí los punteros son de 64 bits y el **diseño de la estructura cambia**. Se evitó en el receptor: el contexto gcm se maneja como 4 × `u32` y se llama `gcmInitBodyEx` directamente (no `rsxInit`, no las macros de librsx que leen `context->current`). **Cualquier otra estructura con punteros que se pase al sistema debe revisarse** (las usadas —`vdecConfig`, `vdecAU`, `audioPortConfig`, `videoConfiguration`— usan `u32`).
2. **SELF falso (`fself`)**: sirve en consolas con CFW/HEN que aceptan ejecutables sin firma oficial; no es un SELF firmado.
3. **Punteros a función**: se pasan al sistema como dirección de descriptor (`OPD`) de 32 bits (callback de cellVdec). Sin verificar en consola.
4. `setjmp/longjmp` de newlib con clang no se probó (el receptor no los usa).
5. Si algo falla en la consola, lo más probable (en orden): inicialización de gcm/vdec, orden de llamadas de cellVdec, o diseño de alguna estructura (punto 1).
