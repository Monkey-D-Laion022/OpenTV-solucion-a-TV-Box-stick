Receptor OpenTV para PS3. Ver `../../docs/PS3-RECEPTOR.md` (estado real, instalación y pruebas) y `../../docs/PS3-TOOLCHAIN-CLANG.md`.
Compilar y empaquetar: `make ci PS3DEV=<SDK>` (ver `../../docs/PS3-BUILD-CI.md`). Pruebas en PC: `sh run_host_tests.sh`; Link: `test/run_link_tests.sh`.
`third_party/`: configuración mínima de mbedTLS. faad2 (GPL) y mbedTLS (Apache-2.0) NO se incluyen: se descargan (ver `toolchain/*.txt` para versiones). Atención: **faad2 es GPLv2+**; el PKG que lo enlaza queda bajo esa licencia (ver `toolchain/FAAD2-COPYING`).
