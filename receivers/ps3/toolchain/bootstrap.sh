
#!/usr/bin/env bash
set -Eeuo pipefail

# ============================================================
# OpenTV PS3 - Preparacion del entorno experimental Clang
# ============================================================

: "${RUNNER_TEMP:?ERROR: RUNNER_TEMP no esta definido}"
: "${GITHUB_ENV:?ERROR: GITHUB_ENV no esta definido}"
: "${GITHUB_PATH:?ERROR: GITHUB_PATH no esta definido}"

PS3DEV="$RUNNER_TEMP/ps3dev"
BUILD_ROOT="$RUNNER_TEMP/ps3build"
EXTRACT="$RUNNER_TEMP/ps3dev-extract"
ARCHIVE="$RUNNER_TEMP/ps3dev-sdk"
NEWLIB_SRC="$BUILD_ROOT/newlib-1.20.0-PS3"

echo "============================================"
echo " OpenTV PS3 - Preparando entorno"
echo "============================================"

# ------------------------------------------------------------
# 1. Dependencias del sistema
# ------------------------------------------------------------

echo "Instalando herramientas..."

sudo apt-get update

sudo apt-get install -y \
  curl \
  tar \
  zstd \
  python3 \
  git \
  clang \
  lld \
  make \
  cmake \
  autoconf \
  automake \
  libtool \
  texinfo \
  patch \
  pkg-config \
  bison \
  flex \
  gcc \
  g++ \
  libelf-dev \
  zlib1g-dev \
  libgmp-dev \
  libmpfr-dev \
  libmpc-dev

mkdir -p \
  "$PS3DEV" \
  "$EXTRACT" \
  "$BUILD_ROOT"

# ------------------------------------------------------------
# 2. Descargar Newlib adaptada para PS3
# ------------------------------------------------------------

echo "Descargando Newlib PS3..."

if [ ! -d "$NEWLIB_SRC/.git" ]; then
  git clone --depth 1 \
    https://github.com/ps3dev/newlib-1.20.0-PS3.git \
    "$NEWLIB_SRC"
fi

echo "Verificando codigo fuente de Newlib..."

if [ ! -f "$NEWLIB_SRC/newlib/configure" ]; then
  echo "ERROR: no existe newlib/configure"
  exit 1
fi

if [ ! -d "$NEWLIB_SRC/libgloss" ]; then
  echo "ERROR: no existe libgloss"
  exit 1
fi

echo "Newlib descargada correctamente."

# ------------------------------------------------------------
# 3. Buscar SDK oficial PS3DEV
# ------------------------------------------------------------

echo "Buscando SDK PS3DEV para Linux..."

DOWNLOAD_URL="$(
  curl -fsSL \
    -H "Accept: application/vnd.github+json" \
    https://api.github.com/repos/ps3dev/ps3dev/releases?per_page=30 |
  python3 -c '
import json
import sys

releases = json.load(sys.stdin)

for release in releases:
    for asset in release.get("assets", []):
        name = asset["name"].lower()

        linux = "ubuntu" in name or "linux" in name
        archive = name.endswith(".tar.gz") or name.endswith(".tar.zst")

        if linux and archive:
            print(asset["browser_download_url"])
            sys.exit(0)

sys.exit("ERROR: no se encontro un SDK Linux compatible")
'
)"

if [ -z "$DOWNLOAD_URL" ]; then
  echo "ERROR: URL de descarga vacia."
  exit 1
fi

echo "SDK encontrado:"
echo "$DOWNLOAD_URL"

# ------------------------------------------------------------
# 4. Descargar y extraer SDK
# ------------------------------------------------------------

echo "Descargando SDK..."

curl -fL \
  --retry 3 \
  --retry-delay 2 \
  "$DOWNLOAD_URL" \
  -o "$ARCHIVE"

echo "Extrayendo SDK..."

case "$DOWNLOAD_URL" in
  *.tar.gz)
    tar -xzf "$ARCHIVE" -C "$EXTRACT"
    ;;
  *.tar.zst)
    tar --zstd -xf "$ARCHIVE" -C "$EXTRACT"
    ;;
  *)
    echo "ERROR: formato de SDK desconocido."
    exit 1
    ;;
esac

# ------------------------------------------------------------
# 5. Encontrar instalacion PPU
# ------------------------------------------------------------

echo "Buscando compilador PPU..."

COMPILER="$(
  find "$EXTRACT" \
    \( -type f -o -type l \) \
    -path '*/ppu/bin/ppu-gcc' \
    -print -quit
)"

if [ -z "$COMPILER" ]; then
  echo "ERROR: no se encontro ppu-gcc."
  echo "Contenido extraido:"
  find "$EXTRACT" -maxdepth 4 -print
  exit 1
fi

SDK_ROOT="${COMPILER%/ppu/bin/ppu-gcc}"

echo "SDK encontrado en:"
echo "$SDK_ROOT"

# ------------------------------------------------------------
# 6. Instalar SDK en ruta temporal
# ------------------------------------------------------------

echo "Instalando SDK..."

cp -a "$SDK_ROOT/." "$PS3DEV/"

if [ ! -e "$PS3DEV/ppu/bin/ppu-gcc" ]; then
  echo "ERROR: instalacion incompleta."
  exit 1
fi

# ------------------------------------------------------------
# 7. Exportar variables para siguientes pasos
# ------------------------------------------------------------

echo "Configurando variables de entorno..."

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

export PS3DEV
export PSL1GHT="$PS3DEV"
export PS3_BUILD_ROOT="$BUILD_ROOT"
export NEWLIB_SRC

export PATH="$PS3DEV/bin:$PS3DEV/ppu/bin:$PS3DEV/spu/bin:$PATH"

# ------------------------------------------------------------
# 8. Verificar herramientas PS3
# ------------------------------------------------------------

echo "Verificando herramientas..."

for tool in \
  ppu-gcc \
  ppu-strip \
  sprxlinker \
  fself \
  sfo \
  pkg
do
  if command -v "$tool" >/dev/null 2>&1; then
    echo "OK: $tool"
  elif [ -x "$PS3DEV/bin/$tool" ]; then
    echo "OK: $tool"
  elif [ -x "$PS3DEV/ppu/bin/$tool" ]; then
    echo "OK: $tool"
  else
    echo "AVISO: no encontrado: $tool"
  fi
done

# ------------------------------------------------------------
# 9. Verificar estructura PPU y archivos CRT
# ------------------------------------------------------------

echo "Verificando estructura PPU..."

REQUIRED_PATHS=(
  "$PS3DEV/ppu/powerpc64-ps3-elf/include"
  "$PS3DEV/ppu/ppu/lib/lv2.ld"
  "$PS3DEV/ppu/ppu/lib/lv2-crt0.o"
  "$PS3DEV/ppu/ppu/lib/lv2-crti.o"
  "$PS3DEV/ppu/ppu/lib/lv2-crtn.o"
  "$PS3DEV/ppu/ppu/lib/lv2-sprx.o"
)

for path in "${REQUIRED_PATHS[@]}"; do
  if [ -e "$path" ]; then
    echo "OK: $path"
  else
    echo "ERROR: archivo requerido no encontrado: $path"
    exit 1
  fi
done

# ------------------------------------------------------------
# 10. Buscar bibliotecas del sistema
# ------------------------------------------------------------

echo "Buscando bibliotecas del sistema..."

for library in libc.a libsysbase.a; do
  FOUND=0

  while IFS= read -r -d '' path; do
    echo "ENCONTRADA: $path"
    FOUND=1
  done < <(
    find "$PS3DEV/ppu" -type f -name "$library" -print0
  )

  if [ "$FOUND" -eq 0 ]; then
    echo "AVISO: $library no encontrada en el SDK."
    echo "Puede requerir compilacion de Newlib/libgloss."
  fi
done

# ------------------------------------------------------------
# 11. Verificar Clang y LLD
# ------------------------------------------------------------

echo "Verificando Clang..."

clang --version

echo "Verificando LLD..."

ld.lld --version

# ------------------------------------------------------------
# 12. Resumen
# ------------------------------------------------------------

echo "============================================"
echo " Entorno PS3 preparado"
echo "============================================"

echo "PS3DEV=$PS3DEV"
echo "PS3_BUILD_ROOT=$BUILD_ROOT"
echo "NEWLIB_SRC=$NEWLIB_SRC"

echo "IMPORTANTE:"
echo "Newlib fue descargada, pero aun no compilada."
echo "Las dependencias FAAD2 y mbedTLS aun no fueron compiladas."
echo "La compatibilidad ABI de Clang con PS3 aun no esta validada."
echo "El enlazado final permanece deshabilitado."

echo "Preparacion finalizada."
