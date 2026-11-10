#!/usr/bin/env bash
set -Eeuo pipefail

# ============================================================
# OpenTV PS3 - Compilacion experimental de mbedTLS 3.6.2
# PPU mediante Clang + CMake.
# Solo construye la biblioteca criptografica estatica.
# ============================================================

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PS3_ROOT="$(cd "$HERE/../../.." && pwd)"

BUILD_ROOT="${PS3_BUILD_ROOT:?ERROR: PS3_BUILD_ROOT no esta definido}"
PS3DEV="${PS3DEV:?ERROR: PS3DEV no esta definido}"

MBED_DIR="$BUILD_ROOT/mbedtls"
BUILD_DIR="$BUILD_ROOT/mbed-build"
OUT_DIR="$BUILD_ROOT/mbed-out"

WRAPPER="$HERE/ppu-gcc.clang-wrapper"
TOOLCHAIN="$HERE/ps3-ppu-clang.cmake"
CONFIG="$PS3_ROOT/third_party/ot_mbedtls_config.h"

EXPECTED_TAG="v3.6.2"

LIBRARY="$OUT_DIR/libmbed_ppu.a"

echo "============================================"
echo " OpenTV PS3 - Construccion de mbedTLS"
echo "============================================"

# ------------------------------------------------------------
# 1. Comprobar herramientas
# ------------------------------------------------------------

echo
echo "1. Comprobando herramientas..."

for tool in cmake clang ar ranlib git file; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        echo "ERROR: falta la herramienta: $tool"
        exit 1
    fi
done

if [ ! -d "$PS3DEV/ppu/powerpc64-ps3-elf/include" ]; then
    echo "ERROR: faltan los headers PPU del SDK."
    exit 1
fi

if [ ! -f "$WRAPPER" ]; then
    echo "ERROR: falta ppu-gcc.clang-wrapper."
    exit 1
fi

if [ ! -f "$TOOLCHAIN" ]; then
    echo "ERROR: falta ps3-ppu-clang.cmake."
    exit 1
fi

if [ ! -f "$CONFIG" ]; then
    echo "ERROR: falta ot_mbedtls_config.h."
    exit 1
fi

chmod +x "$WRAPPER"

# ------------------------------------------------------------
# 2. Comprobar fuentes
# ------------------------------------------------------------

echo
echo "2. Comprobando fuentes mbedTLS..."

if [ ! -d "$MBED_DIR/.git" ]; then
    echo "ERROR: no existe el repositorio mbedTLS."
    echo "Ejecutar primero fetch-deps.sh."
    exit 1
fi

ACTUAL_TAG="$(git -C "$MBED_DIR" describe --tags --exact-match 2>/dev/null || true)"

if [ "$ACTUAL_TAG" != "$EXPECTED_TAG" ]; then
    echo "ERROR: version incorrecta de mbedTLS."
    echo "Esperada: $EXPECTED_TAG"
    echo "Actual:   ${ACTUAL_TAG:-sin etiqueta}"
    exit 1
fi

if [ ! -f "$MBED_DIR/CMakeLists.txt" ]; then
    echo "ERROR: falta CMakeLists.txt."
    exit 1
fi

if [ ! -f "$MBED_DIR/library/CMakeLists.txt" ]; then
    echo "ERROR: falta library/CMakeLists.txt."
    exit 1
fi

if [ ! -f "$MBED_DIR/include/mbedtls/ssl.h" ]; then
    echo "ERROR: faltan los encabezados de mbedTLS."
    exit 1
fi

echo "[OK] Fuentes mbedTLS $ACTUAL_TAG verificadas."

# ------------------------------------------------------------
# 3. Preparar directorios
# ------------------------------------------------------------

echo
echo "3. Preparando directorios..."

rm -rf "$BUILD_DIR"
rm -rf "$OUT_DIR"

mkdir -p "$BUILD_DIR"
mkdir -p "$OUT_DIR/include"

# ------------------------------------------------------------
# 4. Configurar CMake
# ------------------------------------------------------------

echo
echo "4. Configurando compilacion cruzada..."

cmake \
    -S "$MBED_DIR" \
    -B "$BUILD_DIR" \
    -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" \
    -DCMAKE_BUILD_TYPE=Release \
    -DBUILD_SHARED_LIBS=OFF \
    -DUSE_STATIC_MBEDTLS_LIBRARY=ON \
    -DUSE_SHARED_MBEDTLS_LIBRARY=OFF \
    -DENABLE_PROGRAMS=OFF \
    -DENABLE_TESTING=OFF \
    -DCMAKE_TRY_COMPILE_TARGET_TYPE=STATIC_LIBRARY \
    -DCMAKE_C_FLAGS="-O2 -ffunction-sections -fdata-sections -I$PS3_ROOT/third_party -DMBEDTLS_CONFIG_FILE=\\\"ot_mbedtls_config.h\\\""

# ------------------------------------------------------------
# 5. Comprobar compilador
# ------------------------------------------------------------

echo
echo "5. Verificando compilador..."

if ! grep -F "$WRAPPER" "$BUILD_DIR/CMakeCache.txt" \
    | grep -q "CMAKE_C_COMPILER"; then
    echo "ERROR: CMake no registro el wrapper como compilador."
    grep "CMAKE_C_COMPILER" "$BUILD_DIR/CMakeCache.txt" || true
    exit 1
fi

echo "[OK] Wrapper registrado."

# ------------------------------------------------------------
# 6. Compilar biblioteca criptografica
# ------------------------------------------------------------

echo
echo "6. Compilando mbedcrypto..."

JOBS="${JOBS:-2}"

cmake \
    --build "$BUILD_DIR" \
    --target mbedcrypto \
    --parallel "$JOBS"

# ------------------------------------------------------------
# 7. Encontrar biblioteca
# ------------------------------------------------------------

echo
echo "7. Buscando libmbedcrypto.a..."

mapfile -t LIBRARIES < <(
    find "$BUILD_DIR" -type f -name "libmbedcrypto.a" -print
)

if [ "${#LIBRARIES[@]}" -ne 1 ]; then
    echo "ERROR: se esperaba exactamente una libmbedcrypto.a."
    echo "Encontradas: ${#LIBRARIES[@]}"
    printf '%s\n' "${LIBRARIES[@]:-}"
    exit 1
fi

cp "${LIBRARIES[0]}" "$LIBRARY"

if [ ! -s "$LIBRARY" ]; then
    echo "ERROR: biblioteca vacia."
    exit 1
fi

# ------------------------------------------------------------
# 8. Verificar archivo estatico
# ------------------------------------------------------------

echo
echo "8. Verificando archivo estatico..."

ar t "$LIBRARY" > "$BUILD_ROOT/mbed-archive-members.txt"

if [ ! -s "$BUILD_ROOT/mbed-archive-members.txt" ]; then
    echo "ERROR: biblioteca sin objetos."
    exit 1
fi

# ------------------------------------------------------------
# 9. Inspeccionar arquitectura
# ------------------------------------------------------------

echo
echo "9. Inspeccionando objetos..."

INSPECT_DIR="$BUILD_ROOT/mbed-inspect"

rm -rf "$INSPECT_DIR"
mkdir -p "$INSPECT_DIR"

(
    cd "$INSPECT_DIR"
    ar x "$LIBRARY"
)

OBJECT_COUNT=0

while IFS= read -r -d '' obj; do
    DESCRIPTION="$(file "$obj")"
    echo "$DESCRIPTION"

    if ! grep -Eqi 'PowerPC|ppc64' <<< "$DESCRIPTION"; then
        echo "ERROR: objeto que no parece PowerPC:"
        echo "$obj"
        exit 1
    fi

    OBJECT_COUNT=$((OBJECT_COUNT + 1))
done < <(find "$INSPECT_DIR" -type f -print0)

if [ "$OBJECT_COUNT" -eq 0 ]; then
    echo "ERROR: no se encontraron objetos."
    exit 1
fi

# ------------------------------------------------------------
# 10. Copiar encabezados
# ------------------------------------------------------------

echo
echo "10. Copiando encabezados..."

cp -R "$MBED_DIR/include/mbedtls" "$OUT_DIR/include/"

cp "$CONFIG" "$OUT_DIR/include/ot_mbedtls_config.h"

# ------------------------------------------------------------
# 11. Resultado
# ------------------------------------------------------------

echo
echo "============================================"
echo " MBEDTLS: COMPILACION TERMINADA"
echo "============================================"

echo "Biblioteca esperada por build_pkg.sh:"
ls -lh "$LIBRARY"

echo
echo "Encabezados:"
ls -lh "$OUT_DIR/include/"

echo
echo "Objetos inspeccionados: $OBJECT_COUNT"

echo
echo "IMPORTANTE:"
echo "La arquitectura PowerPC detectada no demuestra"
echo "compatibilidad completa con el ABI de PS3."
echo "La implementacion de mbedtls_hardware_poll()"
echo "y el enlazado final deben verificarse aparte."
