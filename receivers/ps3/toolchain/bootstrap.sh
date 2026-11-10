
#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
PS3DEV="$RUNNER_TEMP/ps3dev"

echo "Preparando entorno PS3DEV..."

sudo apt-get update
sudo apt-get install -y curl tar zstd

mkdir -p "$PS3DEV"

URL="https://github.com/ps3dev/ps3dev/releases/latest"

API_URL="https://api.github.com/repos/ps3dev/ps3dev/releases/latest"

DOWNLOAD_URL="$(curl -fsSL "$API_URL" |
  python3 -c 'import json,sys; d=json.load(sys.stdin); a=[x["browser_download_url"] for x in d.get("assets",[]) if "ubuntu" in x["name"].lower() and (x["name"].endswith(".tar.gz") or x["name"].endswith(".tar.zst"))]; print(a[0] if a else "")')"

if [ -z "$DOWNLOAD_URL" ]; then
  echo "ERROR: No se encontró un SDK precompilado para Ubuntu."
  exit 1
fi

echo "Descargando: $DOWNLOAD_URL"

curl -fL "$DOWNLOAD_URL" -o "$RUNNER_TEMP/ps3dev-sdk"

case "$DOWNLOAD_URL" in
  *.tar.gz)
    tar -xzf "$RUNNER_TEMP/ps3dev-sdk" -C "$RUNNER_TEMP"
    ;;
  *.tar.zst)
    sudo apt-get install -y zstd
    tar --zstd -xf "$RUNNER_TEMP/ps3dev-sdk" -C "$RUNNER_TEMP"
    ;;
  *)
    echo "ERROR: Formato de SDK desconocido."
    exit 1
    ;;
esac

if [ ! -d "$PS3DEV" ]; then
  FOUND="$(find "$RUNNER_TEMP" -maxdepth 3 -type f -name ppu-gcc -printf '%h\n' | head -n 1)"

  if [ -z "$FOUND" ]; then
    echo "ERROR: No se encontró ppu-gcc en el SDK descargado."
    exit 1
  fi

  echo "ERROR: El SDK se extrajo en una ruta inesperada: $FOUND"
  exit 1
fi

echo "PS3DEV=$PS3DEV" >> "$GITHUB_ENV"
echo "PSL1GHT=$PS3DEV" >> "$GITHUB_ENV"
echo "$PS3DEV/bin" >> "$GITHUB_PATH"
echo "$PS3DEV/ppu/bin" >> "$GITHUB_PATH"
echo "$PS3DEV/spu/bin" >> "$GITHUB_PATH"

echo "Verificando compilador..."
"$PS3DEV/ppu/bin/ppu-gcc" --version

echo "Toolchain preparado."

