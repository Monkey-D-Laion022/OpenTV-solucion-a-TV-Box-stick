# Flujo experimental con Clang: OBSOLETO

Los scripts de esta carpeta (`bootstrap.sh`, `fetch-deps.sh`, `build-faad2.sh`, `build-mbedtls.sh`, `build-deps.sh`, `ps3-ppu-clang.cmake`,
`ppu-gcc.clang-wrapper`) pertenecen a un experimento con Clang. **El build oficial NO los usa.** Se usa el `ppu-gcc` real del SDK ps3dev:

1. `ci/setup-sdk.sh` instala el SDK y comprueba que compila con los headers Newlib propios (stdio.h, string.h, stdint.h, math.h) sin tocar `/usr/include`.
2. `make ci` → `ci/build-deps.sh` (FAAD2 + mbedTLS con ppu-gcc) → receptor → PKG → `ci/verify-pkg.sh`.

Por qué falló el experimento (job `diagnose-ps3`): CMake usaba `ppu-gcc.clang-wrapper`, que apuntaba a `/home/claude/ps3dev` (ruta de otro equipo). En GitHub esa ruta no existe,
Clang quedaba sin `-isystem` de Newlib y fallaba con «'stdio.h' file not found». Mezclar Clang del host con el ABI de PS3 tampoco está validado. Se conservan por referencia; no los ejecutes.
