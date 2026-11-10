#!/usr/bin/env bash
set -euo pipefail

PS3DEV="$RUNNER_TEMP/ps3dev"
BUILD_ROOT="$RUNNER_TEMP/ps3build"
EXTRACT="$RUNNER_TEMP/ps3dev-extract"
ARCHIVE="$RUNNER_TEMP/ps3dev-sdk"
NEWLIB_SRC="$BUILD_ROOT/newlib-1.20.0-PS3"

echo "Preparando entorno PS3..."

sudo apt-get update
sudo apt-get install -y \
  curl tar zstd python3 git \
  clang lld make cmake \
  autoconf automake libtool \
  texinfo patch pkg-config

mkdir -p "$PS3DEV" "$EXTRACT" "$BUILD_ROOT"

echo "Descargando código fuente oficial de Newlib PS3..."

if [ ! -d "$NEWLIB_SRC/.git" ]; then
  git clone --depth 1 \
    https://github.com/ps3dev/newlib-1.20.0-PS3.git \
    "$NEWLIB_SRC"
fi

echo "Verificando fuentes de Newlib..."

test -f "$NEWLIB_SRC/newlib/configure" || {
  echo "ERROR: falta newlib/configure"
  exit 1
}

test -d "$NEWLIB_SRC/libgloss" || {
  echo "ERROR: falta libgloss"
  exit 1
}

echo "Newlib descargada correctamente."
echo "Ruta: $NEWLIB_SRC"

echo "Buscando SDK PS3DEV..."

DOWNLOAD_URL="$(curl -fsSL \
  -H "Accept: application/vnd.github+json" \
  https://api.github.com/repos/ps3dev/ps3dev/releases?per_page=30 |
  python3 -c '
import json, sys
releases = json.load(sys.stdin)
for release in releases:
    for asset in release.get("assets", []):
        name = asset["name"].lower()
        if ("ubuntu" in name or "linux" in name) and (
            name.endswith(".tar.gz") or name.endswith(".tar.zst")
        ):
            print(asset["browser_download_url"])
            sys.exit(0)
sys.exit("No se encontró un SDK Linux compatible")
')"

echo "Descargando SDK..."
curl -fL "$DOWNLOAD_URL" -o "$ARCHIVE"

echo "Extrayendo SDK..."

case "$DOWNLOAD_URL" in
  *.tar.gz)
    tar -xzf "$ARCHIVE" -C "$EXTRACT"
    ;;
  *.tar.zst)
    tar --zstd -xf "$ARCHIVE" -C "$EXTRACT"
    ;;
  *)
    echo "ERROR: formato desconocido."
    exit 1
    ;;
esac

COMPILER="$(find "$EXTRACT" -type f \
  -path '*/ppu/bin/ppu-gcc' -print -quit)"

if [ -z "$COMPILER" ]; then
  echo "ERROR: no se encontró ppu-gcc."
  exit 1
fi

SDK_ROOT="${COMPILER%/ppu/bin/ppu-gcc}"

echo "Instalando SDK en $PS3DEV..."
cp -a "$SDK_ROOT/." "$PS3DEV/"

echo "Exportando variables..."

{
  echo "PS3DEV=$PS3DEV"
  echo "PSL1GHT=$PS3DEV"
  echo "PS3_BUILD_ROOT=$BUILD_ROOT"
  echo "NEWLIB_SRC=$NEWLIB_SRC"
} >> "$GITHUB_ENV"

{
  echo "$PS3DEV/bin"
  echo "$PS3DEV/ppu/bin"
  echo "$PS3DEV/spu/bin"
} >> "$GITHUB_PATH"

echo "Verificando herramientas..."

for tool in sprxlinker fself sfo pkg; do
  if [ -x "$PS3DEV/bin/$tool" ]; then
    echo "OK: $tool"
  else
    echo "AVISO: falta $tool"
  fi
done

echo "Verificando Clang..."
clang --version
ld.lld --version

echo "Verificando fuentes Newlib..."
echo "NEWLIB_SRC=$NEWLIB_SRC"

echo "Entorno preparado."
echo "ATENCION: Newlib aun NO esta compilada para Clang."
