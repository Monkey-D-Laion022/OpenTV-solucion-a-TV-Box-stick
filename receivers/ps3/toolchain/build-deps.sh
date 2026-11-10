
#!/usr/bin/env bash
set -Eeuo pipefail

# ============================================================
# OpenTV PS3 - Diagnostico de SDK, Clang y dependencias PPU
# No compila ni instala dependencias.
# ============================================================

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PS3_ROOT="$(cd "$HERE/.." && pwd)"
WRAPPER="$HERE/ppu-gcc.clang-wrapper"

BUILD_ROOT="${PS3_BUILD_ROOT:?ERROR: PS3_BUILD_ROOT no esta definido}"
PS3DEV="${PS3DEV:?ERROR: PS3DEV no esta definido}"

ERRORS=0
WARNINGS=0

ok() {
    echo "[OK] $1"
}

warn() {
    echo "[AVISO] $1"
    WARNINGS=$((WARNINGS + 1))
}

fail() {
    echo "[ERROR] $1"
    ERRORS=$((ERRORS + 1))
}

echo "============================================"
echo " OpenTV PS3 - Diagnostico de toolchain"
echo "============================================"

mkdir -p "$BUILD_ROOT/diagnostic"

# ------------------------------------------------------------
# 1. Variables de entorno
# ------------------------------------------------------------

echo
echo "1. Variables de entorno"

if [ ! -d "$PS3DEV" ]; then
    fail "No existe PS3DEV: $PS3DEV"
else
    ok "PS3DEV=$PS3DEV"
fi

if [ ! -d "$BUILD_ROOT" ]; then
    fail "No existe PS3_BUILD_ROOT: $BUILD_ROOT"
else
    ok "PS3_BUILD_ROOT=$BUILD_ROOT"
fi

if [ -n "${NEWLIB_SRC:-}" ] && [ -d "$NEWLIB_SRC" ]; then
    ok "Newlib fuente: $NEWLIB_SRC"
else
    warn "NEWLIB_SRC no esta disponible."
fi

# ------------------------------------------------------------
# 2. Herramientas host
# ------------------------------------------------------------

echo
echo "2. Herramientas host"

for tool in clang ld.lld make cmake git python3 ar ranlib file; do
    if command -v "$tool" >/dev/null 2>&1; then
        ok "$tool: $(command -v "$tool")"
    else
        fail "No se encontro $tool"
    fi
done

# ------------------------------------------------------------
# 3. Herramientas PS3DEV
# ------------------------------------------------------------

echo
echo "3. Herramientas PS3DEV"

for tool in ppu-gcc ppu-strip sprxlinker fself sfo pkg; do
    if command -v "$tool" >/dev/null 2>&1; then
        ok "$tool: $(command -v "$tool")"
    elif [ -x "$PS3DEV/bin/$tool" ]; then
        ok "$tool: $PS3DEV/bin/$tool"
    elif [ -x "$PS3DEV/ppu/bin/$tool" ]; then
        ok "$tool: $PS3DEV/ppu/bin/$tool"
    else
        warn "No se encontro $tool"
    fi
done

# ------------------------------------------------------------
# 4. Estructura SDK
# ------------------------------------------------------------

echo
echo "4. Estructura del SDK"

for path in \
    "$PS3DEV/ppu" \
    "$PS3DEV/ppu/bin" \
    "$PS3DEV/ppu/lib" \
    "$PS3DEV/ppu/powerpc64-ps3-elf/include" \
    "$PS3DEV/bin"
do
    if [ -d "$path" ]; then
        ok "Directorio: $path"
    else
        warn "Directorio ausente: $path"
    fi
done

echo
echo "Buscando headers PPU..."

find "$PS3DEV/ppu" \
    -type d \
    \( -name include -o -name headers \) \
    -print 2>/dev/null | head -30 || true

echo
echo "Buscando bibliotecas del SDK..."

find "$PS3DEV/ppu" \
    -type f \
    \( -name '*.a' -o -name '*.so' -o -name '*.o' \) \
    -print 2>/dev/null | head -60 || true

echo
echo "Buscando archivos CRT y linker..."

find "$PS3DEV/ppu" \
    -type f \
    \( -name 'lv2.ld' -o -name 'lv2-crt*.o' -o -name 'lv2-sprx.o' \) \
    -print 2>/dev/null | head -30 || true

# ------------------------------------------------------------
# 5. Archivos OpenTV
# ------------------------------------------------------------

echo
echo "5. Archivos del proyecto"

for f in \
    "$WRAPPER" \
    "$HERE/ps3-ppu-clang.cmake" \
    "$HERE/ppc-asm.h" \
    "$PS3_ROOT/third_party/ot_mbedtls_config.h"
do
    if [ -f "$f" ]; then
        ok "Encontrado: $f"
    else
        fail "Falta archivo: $f"
    fi
done

# ------------------------------------------------------------
# 6. Fuentes descargadas
# ------------------------------------------------------------

echo
echo "6. Fuentes de dependencias"

if [ -f "$BUILD_ROOT/faad2/CMakeLists.txt" ]; then
    ok "Fuentes FAAD2 encontradas."
else
    warn "Fuentes FAAD2 pendientes."
fi

if [ -f "$BUILD_ROOT/mbedtls/CMakeLists.txt" ]; then
    ok "Fuentes mbedTLS encontradas."
else
    warn "Fuentes mbedTLS pendientes."
fi

# ------------------------------------------------------------
# 7. Bibliotecas compiladas
# ------------------------------------------------------------

echo
echo "7. Bibliotecas compiladas"

FAAD_LIB="$BUILD_ROOT/faad-out/libfaad.a"
MBED_LIB="$BUILD_ROOT/mbed-out/libmbed_ppu.a"

for lib in "$FAAD_LIB" "$MBED_LIB"; do
    if [ -s "$lib" ]; then
        ok "Biblioteca encontrada: $lib"

        if ar t "$lib" \
            > "$BUILD_ROOT/diagnostic/$(basename "$lib").members"; then
            ok "Archivo AR legible: $lib"
        else
            fail "Archivo AR invalido: $lib"
        fi
    else
        warn "Biblioteca pendiente o vacia: $lib"
    fi
done

# ------------------------------------------------------------
# 8. Encabezados de dependencias
# ------------------------------------------------------------

echo
echo "8. Encabezados"

for header in \
    "$BUILD_ROOT/faad-out/include/neaacdec.h" \
    "$BUILD_ROOT/mbed-out/include/mbedtls/ssl.h" \
    "$BUILD_ROOT/mbed-out/include/ot_mbedtls_config.h"
do
    if [ -s "$header" ]; then
        ok "Encabezado encontrado: $header"
    else
        warn "Encabezado pendiente: $header"
    fi
done

# ------------------------------------------------------------
# 9. Prueba de compilacion PPU
# ------------------------------------------------------------

echo
echo "9. Prueba de compilacion PPU"

if [ -f "$WRAPPER" ]; then
    chmod +x "$WRAPPER"

    PROBE_DIR="$BUILD_ROOT/diagnostic"
    PROBE_C="$PROBE_DIR/ppu_probe.c"
    PROBE_O="$PROBE_DIR/ppu_probe.o"

    cat > "$PROBE_C" <<'EOF'
typedef unsigned int u32;

u32 opentv_ppu_probe(u32 value)
{
    return value + 1;
}
EOF

    if "$WRAPPER" -c "$PROBE_C" -o "$PROBE_O"; then
        if [ -s "$PROBE_O" ]; then
            ok "Objeto de prueba generado."

            DESCRIPTION="$(file "$PROBE_O")"
            echo "$DESCRIPTION"

            if grep -Eqi 'PowerPC|ppc64' <<< "$DESCRIPTION"; then
                ok "Objeto identificado como PowerPC."
            else
                fail "El objeto no fue identificado como PowerPC."
            fi
        else
            fail "La compilacion no genero un objeto valido."
        fi
    else
        fail "Clang no pudo compilar el archivo de prueba."
    fi
else
    fail "No se puede probar: falta el wrapper."
fi

# ------------------------------------------------------------
# 10. Resultado
# ------------------------------------------------------------

echo
echo "============================================"
echo " RESULTADO DEL DIAGNOSTICO"
echo "============================================"

echo "Errores: $ERRORS"
echo "Avisos:  $WARNINGS"

if [ "$ERRORS" -gt 0 ]; then
    echo
    echo "DIAGNOSTICO FALLIDO."
    echo "No continuar con el receptor."
    exit 1
fi

echo
echo "DIAGNOSTICO BASICO COMPLETADO."
echo "Esto NO confirma el ABI PS3."
echo "Tampoco confirma el enlazado ni el PKG."
