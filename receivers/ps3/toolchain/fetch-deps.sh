
#!/usr/bin/env bash
set -Eeuo pipefail

# ============================================================
# OpenTV PS3 - Descarga y verificacion de dependencias
# FAAD2 + mbedTLS 3.6.2
# ============================================================

BUILD_ROOT="${PS3_BUILD_ROOT:?ERROR: PS3_BUILD_ROOT no esta definido}"

FAAD_DIR="$BUILD_ROOT/faad2"
MBED_DIR="$BUILD_ROOT/mbedtls"

FAAD_URL="https://github.com/knik0/faad2.git"
MBED_URL="https://github.com/Mbed-TLS/mbedtls.git"

FAAD_COMMIT="a335040022e14ff9b6c54bbd459590d4c7edffe8"
MBED_TAG="v3.6.2"

echo "============================================"
echo " OpenTV PS3 - Descarga de dependencias"
echo "============================================"

if ! command -v git >/dev/null 2>&1; then
    echo "ERROR: falta Git."
    exit 1
fi

mkdir -p "$BUILD_ROOT"

# ------------------------------------------------------------
# 1. FAAD2
# ------------------------------------------------------------

echo
echo "1. Preparando FAAD2..."

if [ -d "$FAAD_DIR/.git" ]; then
    CURRENT="$(git -C "$FAAD_DIR" rev-parse HEAD)"

    if [ "$CURRENT" != "$FAAD_COMMIT" ]; then
        echo "El commit existente no coincide."
        rm -rf "$FAAD_DIR"
    fi
elif [ -e "$FAAD_DIR" ]; then
    echo "ERROR: existe FAAD2 pero no es un repositorio Git."
    exit 1
fi

if [ ! -d "$FAAD_DIR/.git" ]; then
    git init "$FAAD_DIR"
    git -C "$FAAD_DIR" remote add origin "$FAAD_URL"
    git -C "$FAAD_DIR" fetch --depth 1 origin "$FAAD_COMMIT"
    git -C "$FAAD_DIR" checkout --detach FETCH_HEAD
fi

ACTUAL_COMMIT="$(git -C "$FAAD_DIR" rev-parse HEAD)"

if [ "$ACTUAL_COMMIT" != "$FAAD_COMMIT" ]; then
    echo "ERROR: commit FAAD2 incorrecto."
    echo "Esperado: $FAAD_COMMIT"
    echo "Actual: $ACTUAL_COMMIT"
    exit 1
fi

if [ ! -f "$FAAD_DIR/CMakeLists.txt" ] ||
   [ ! -f "$FAAD_DIR/include/neaacdec.h" ]; then
    echo "ERROR: fuentes FAAD2 incompletas."
    exit 1
fi

echo "[OK] FAAD2 verificada: $ACTUAL_COMMIT"

# ------------------------------------------------------------
# 2. mbedTLS
# ------------------------------------------------------------

echo
echo "2. Preparando mbedTLS $MBED_TAG..."

if [ -d "$MBED_DIR/.git" ]; then
    CURRENT_TAG="$(git -C "$MBED_DIR" describe --tags --exact-match 2>/dev/null || true)"

    if [ "$CURRENT_TAG" != "$MBED_TAG" ]; then
        echo "La version existente no coincide."
        rm -rf "$MBED_DIR"
    fi
elif [ -e "$MBED_DIR" ]; then
    echo "ERROR: existe mbedTLS pero no es un repositorio Git."
    exit 1
fi

if [ ! -d "$MBED_DIR/.git" ]; then
    git clone \
        --depth 1 \
        --branch "$MBED_TAG" \
        --recurse-submodules \
        --shallow-submodules \
        "$MBED_URL" \
        "$MBED_DIR"
fi

ACTUAL_TAG="$(git -C "$MBED_DIR" describe --tags --exact-match 2>/dev/null || true)"

if [ "$ACTUAL_TAG" != "$MBED_TAG" ]; then
    echo "ERROR: version mbedTLS incorrecta."
    echo "Esperada: $MBED_TAG"
    echo "Actual: ${ACTUAL_TAG:-sin etiqueta}"
    exit 1
fi

echo "Inicializando submodulos..."

git -C "$MBED_DIR" submodule update --init --recursive

if git -C "$MBED_DIR" submodule status --recursive | grep -qE '^[-+U]'; then
    echo "ERROR: hay submodulos sin inicializar o con revision incorrecta."
    git -C "$MBED_DIR" submodule status --recursive
    exit 1
fi

if [ ! -f "$MBED_DIR/CMakeLists.txt" ] ||
   [ ! -f "$MBED_DIR/library/CMakeLists.txt" ] ||
   [ ! -f "$MBED_DIR/include/mbedtls/ssl.h" ]; then
    echo "ERROR: fuentes mbedTLS incompletas."
    exit 1
fi

echo "[OK] mbedTLS verificado: $ACTUAL_TAG"

# ------------------------------------------------------------
# 3. Resultado
# ------------------------------------------------------------

echo
echo "============================================"
echo " DEPENDENCIAS DESCARGADAS Y VERIFICADAS"
echo "============================================"

echo "FAAD2:"
echo "$FAAD_DIR"
echo "$FAAD_COMMIT"

echo
echo "mbedTLS:"
echo "$MBED_DIR"
echo "$MBED_TAG"

echo
echo "Las fuentes estan listas para los scripts de compilacion."
