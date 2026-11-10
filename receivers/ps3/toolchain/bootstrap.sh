#!/usr/bin/env bash
set -euo pipefail

PS3DEV="$RUNNER_TEMP/ps3dev"
EXTRACT="$RUNNER_TEMP/ps3dev-extract"
ARCHIVE="$RUNNER_TEMP/ps3dev-sdk"

echo "Preparando entorno PS3DEV..."

sudo apt-get update
sudo apt-get install -y curl tar zstd python3

mkdir -p "$PS3DEV" "$EXTRACT"

echo "Buscando la última compilación disponible..."

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
    echo "ERROR: formato de archivo desconocido."
    exit 1
    ;;
esac

COMPILER="$(find "$EXTRACT" -type f \
  -path '*/ppu/bin/ppu-gcc' -print -quit)"

if [ -z "$COMPILER" ]; then
  echo "ERROR: no se encontró ppu-gcc en el SDK."
  find "$EXTRACT" -maxdepth 4 -type d
  exit 1
fi

SDK_ROOT="${COMPILER%/ppu/bin/ppu-gcc}"

echo "SDK encontrado en: $SDK_ROOT"

cp -a "$SDK_ROOT/." "$PS3DEV/"

if [ ! -x "$PS3DEV/ppu/bin/ppu-gcc" ]; then
  echo "ERROR: no se pudo instalar ppu-gcc."
  exit 1
fi

echo "PS3DEV=$PS3DEV" >> "$GITHUB_ENV"
echo "PSL1GHT=$PS3DEV" >> "$GITHUB_ENV"
echo "$PS3DEV/bin" >> "$GITHUB_PATH"
echo "$PS3DEV/ppu/bin" >> "$GITHUB_PATH"
echo "$PS3DEV/spu/bin" >> "$GITHUB_PATH"

echo "Verificando compilador..."
"$PS3DEV/ppu/bin/ppu-gcc" --version

echo "Verificando herramientas..."
for tool in sprxlinker fself sfo pkg; do
  if [ -x "$PS3DEV/bin/$tool" ]; then
    echo "OK: $tool"
  else
    echo "AVISO: no se encontró $PS3DEV/bin/$tool"
  fi
done

echo "PS3DEV preparado correctamente."
