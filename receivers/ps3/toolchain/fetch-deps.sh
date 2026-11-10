
#!/usr/bin/env bash
set -Eeuo pipefail

# ============================================================
# OpenTV PS3 - Descarga y verificacion de dependencias
# FAAD2 + mbedTLS 3.6.2
# ============================================================

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
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

for tool in git; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        echo "ERROR: falta la herramienta $tool"
        exit 1
    fi
done

mkdir -p "$BUILD_ROOT"

# ------------------------------------------------------------
# 1. FAAD2
# ------------------------------------------------------------

echo
echo "1. Descargando FAAD2..."

if [ -d "$FAAD_DIR/.git" ]; then
    CURRENT="$(git -C "$FAAD_DIR" rev-parse HEAD)"

    if [ "$CURRENT" = "$FAAD_COMMIT" ]; then
        echo "[OK] FAAD2 ya esta en el commit esperado."
    else
        echo "La version existente no coincide. Se descargara nuevamente."
        rm -rf "$FAAD_DIR"
    fi
fi

if [ ! -d "$FAAD_DIR/.git" ]; then
    git clone "$FAAD_URL" "$FAAD_DIR"
    git -C "$FAAD_DIR" checkout --detach "$FAAD_COMMIT"
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
echo "2. Descargando mbedTLS $MBED_TAG..."

if [ -d "$MBED_DIR/.git" ]; then
    CURRENT_TAG="$(git -C "$MBED_DIR" describe --tags --exact-match 2>/dev/null || true)"

    if [ "$CURRENT_TAG" = "$MBED_TAG" ]; then
        echo "[OK] mbedTLS ya esta en $MBED_TAG."
    else
        echo "La version existente no coincide. Se descargara nuevamente."
        rm -rf "$MBED_DIR"
    fi
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

git -C "$MBED_DIR" submodule update --init --recursive

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
