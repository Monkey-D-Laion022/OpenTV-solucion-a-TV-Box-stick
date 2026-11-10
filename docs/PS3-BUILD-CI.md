# Compilar el receptor PS3 y generar el PKG (GitHub Actions)

**Código fuente:** `receivers/ps3/` (`src/`, `core/`, `third_party/`, `pkg/`). **Mecanismo:** `receivers/ps3/Makefile` (+ `ci/build-deps.sh`, `ci/verify-pkg.sh`). **Salida:** `receivers/ps3/build/opentv-ps3.pkg`.

## Por qué se usa el GCC del SDK y no el wrapper de Clang
El SDK nightly (ps3dev) ya incluye `ppu-gcc` 13.2.0 (`powerpc64-ps3-elf`), `libvdec`, `libaudio`, `libgcm_sys`, etc. Compilar con el compilador del propio SDK evita cuestiones de ABI (el log `clang-probe.log` muestra que Clang ignoraba `-mcpu=ppc-cell-be`, y PSL1GHT declara punteros de 32 bits con atributos que Clang no admite). El wrapper de Clang y su job de diagnóstico **se conservan** pero no intervienen en el PKG.

## Pasos (los ejecuta `make ci`)
1. `make tools`: comprueba `ppu-gcc` (powerpc64-ps3-elf) y las herramientas del SDK (`sprxlinker`, `fself` o `make_self_npdrm`, `sfo`, `pkg`; con o sin `.py`).
2. `make deps`: compila faad2 (commit fijado) y mbedTLS 3.6.2 con **ppu-gcc**, con la configuración de `third_party/ot_mbedtls_config.h` → `build/deps/{faad-out,mbed-out}`.
3. `make pkg`: compila `src/*.c` y `core/*.c`, enlaza (`-lnet -lnetctl -lsysutil -lsysmodule -lvdec -laudio -lrsx -lgcm_sys -lio -lrt -llv2 -lm`), `ppu-strip`, `sprxlinker`, EBOOT.BIN (`make_self_npdrm` si existe; si no, `fself -n`), `PARAM.SFO`, `pkg`.
4. `make verify`: encabezado PKG (magia, tamaño total = tamaño del archivo, content id, elementos), EBOOT = SELF, ELF PPC64 big endian con `.lib.stub`, contenido preparado = contenido declarado.

## En GitHub Actions
Agregar al final del job `diagnose-ps3` los pasos de `receivers/ps3/ci/workflow-steps.yml`. Ejecutar el workflow (Actions → build → Run workflow, o con un push). Artefactos: `opentv-ps3-pkg` (el PKG) y `opentv-ps3-debug`.

## Límites (leer)
- Que se genere el PKG **no demuestra** que el receptor funcione. Nunca se ejecutó en una PS3 ni en RPCS3.
- `mbedtls_hardware_poll()` (src/main.c) usa `sysGetRandomNumber` del sistema; si falla devuelve error y OpenTV Link **no arranca** (falla cerrado).
- La verificación del PKG es estructural. Instalar requiere CFW/HEN.
- Este procedimiento se probó en el entorno de desarrollo con un compilador sustituto (Clang) porque no se pudo descargar el SDK allí: **la primera ejecución con el GCC real puede mostrar errores que hay que corregir.**
