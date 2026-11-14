#!/usr/bin/env bash
# Instala el SDK REAL de PS3 (ps3dev: ppu-gcc 13 + Newlib + PSL1GHT) y comprueba que sirve ANTES de compilar nada.
# Entradas: RUNNER_TEMP (o SDK_PARENT), opcional SDK_ARCHIVE (archivo local; sin descarga, para pruebas).
# Salida: PS3DEV/PSL1GHT en $GITHUB_ENV y rutas en $GITHUB_PATH si existen; imprime PS3DEV=... en cualquier caso.
# Por qué existe: el flujo viejo con Clang (toolchain/*.sh) usaba un wrapper con rutas fijas de otra máquina y fallaba con
# «'stdio.h' file not found». Aquí SOLO se usa el ppu-gcc del SDK, que ya trae sus propios headers (Newlib) y su sysroot.
set -Eeuo pipefail
PARENT="${SDK_PARENT:-${RUNNER_TEMP:-/tmp}}"
PS3DEV="$PARENT/ps3dev"; EXTRACT="$PARENT/ps3dev-extract"; ARCHIVE="${SDK_ARCHIVE:-$PARENT/ps3dev-sdk}"
mkdir -p "$EXTRACT"
if [ -z "${SDK_ARCHIVE:-}" ]; then
  echo "Buscando el SDK ps3dev para Linux…"
  URL="$(curl -fsSL ${GITHUB_TOKEN:+-H "Authorization: Bearer $GITHUB_TOKEN"} -H "Accept: application/vnd.github+json" \
    "https://api.github.com/repos/ps3dev/ps3dev/releases?per_page=30" | python3 -c '
import json, sys
for release in json.load(sys.stdin):
    for asset in release.get("assets", []):
        n = asset["name"].lower()
        if ("ubuntu" in n or "linux" in n) and (n.endswith(".tar.gz") or n.endswith(".tar.zst")):
            print(asset["browser_download_url"]); sys.exit(0)
sys.exit("ERROR: no se encontro un SDK Linux compatible")')"
  echo "SDK: $URL"
  curl -fL --retry 3 --retry-delay 2 "$URL" -o "$ARCHIVE"
  EXT="$URL"
else EXT="$ARCHIVE"; fi
echo "Extrayendo…"
case "$EXT" in *.zst) tar --zstd -xf "$ARCHIVE" -C "$EXTRACT";; *) tar -xzf "$ARCHIVE" -C "$EXTRACT";; esac
GCC="$(find "$EXTRACT" \( -type f -o -type l \) -path '*/ppu/bin/ppu-gcc' -print -quit)"
[ -n "$GCC" ] || { echo "ERROR: no se encontro ppu-gcc en el SDK extraido"; find "$EXTRACT" -maxdepth 3; exit 1; }
rm -rf "$PS3DEV"; mkdir -p "$PS3DEV"; cp -a "${GCC%/ppu/bin/ppu-gcc}/." "$PS3DEV/"
export PS3DEV PSL1GHT="$PS3DEV" PATH="$PS3DEV/bin:$PS3DEV/ppu/bin:$PATH"
CC="$PS3DEV/ppu/bin/ppu-gcc"

echo "── Comprobaciones del SDK ──"
[ "$("$CC" -dumpmachine)" = "powerpc64-ps3-elf" ] || { echo "ERROR: ppu-gcc no es powerpc64-ps3-elf"; exit 1; }
for f in ppu/powerpc64-ps3-elf/include/stdio.h ppu/powerpc64-ps3-elf/include/string.h ppu/powerpc64-ps3-elf/include/stdint.h ppu/powerpc64-ps3-elf/include/math.h \
         ppu/ppu/lib/lv2.ld ppu/ppu/lib/lv2-crt0.o; do
  [ -e "$PS3DEV/$f" ] || { echo "ERROR: el SDK no trae $f"; exit 1; }
done
# La prueba que importa: el compilador REAL compila e incluye stdio.h desde su propio sysroot, sin tocar headers del host Linux.
T="$(mktemp -d)"; printf '#include <stdio.h>\n#include <string.h>\n#include <stdint.h>\n#include <math.h>\nint main(void){return (int)strlen("ok")+(int)sqrt(4.0);}\n' > "$T/t.c"
"$CC" -c "$T/t.c" -o "$T/t.o" || { echo "ERROR: ppu-gcc no compila un programa con stdio.h"; exit 1; }
HOSTHDR="$("$CC" -M "$T/t.c" | tr ' \\' '\n\n' | grep -E '^/usr/(include|lib)' || true)"
[ -z "$HOSTHDR" ] || { echo "ERROR: el compilador usa headers del sistema Linux:"; echo "$HOSTHDR"; exit 1; }
for tool in sprxlinker sfo pkg; do command -v "$tool" >/dev/null 2>&1 || [ -x "$PS3DEV/bin/$tool" ] || [ -x "$PS3DEV/ppu/bin/$tool" ] || { echo "ERROR: falta $tool"; exit 1; }; done
echo "OK: ppu-gcc $("$CC" -dumpversion) compila con los headers de Newlib del SDK (stdio.h, string.h, stdint.h, math.h)"
echo "PS3DEV=$PS3DEV"
if [ -n "${GITHUB_ENV:-}" ]; then { echo "PS3DEV=$PS3DEV"; echo "PSL1GHT=$PS3DEV"; } >> "$GITHUB_ENV"; fi
if [ -n "${GITHUB_PATH:-}" ]; then { echo "$PS3DEV/bin"; echo "$PS3DEV/ppu/bin"; echo "$PS3DEV/spu/bin"; } >> "$GITHUB_PATH"; fi
