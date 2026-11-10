
#!/usr/bin/env bash
set -Eeuo pipefail

# ============================================================
# OpenTV PS3 - Diagnostico de SDK y compilador PPU con Clang
# Esta fase NO compila ni instala dependencias.
# ============================================================

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PS3_ROOT="$(cd "$HERE/.." && pwd)"
WRAPPER="$HERE/ppu-gcc.clang-wrapper"

echo "============================================"
echo " OpenTV PS3 - Verificacion de toolchain"
echo "============================================"

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

# ------------------------------------------------------------
# 1. Variables de entorno
# ------------------------------------------------------------

echo
echo "1. Variables de entorno"

if [ -z "${PS3DEV:-}" ]; then
    fail "PS3DEV no esta definido."
    exit 1
fi

if [ -z "${PS3_BUILD_ROOT:-}" ]; then
    fail "PS3_BUILD_ROOT no esta definido."
    exit 1
fi

if [ ! -d "$PS3DEV" ]; then
    fail "No existe el directorio PS3DEV: $PS3DEV"
    exit 1
fi

ok "PS3DEV=$PS3DEV"
ok "PS3_BUILD_ROOT=$PS3_BUILD_ROOT"

if [ -n "${NEWLIB_SRC:-}" ] && [ -d "$NEWLIB_SRC" ]; then
    ok "Newlib fuente encontrada: $NEWLIB_SRC"
else
    warn "No se encontro NEWLIB_SRC."
fi

mkdir -p "$PS3_BUILD_ROOT/diagnostic"

# ------------------------------------------------------------
# 2. Herramientas del sistema
# ------------------------------------------------------------

echo
echo "2. Herramientas host"

for tool in clang ld.lld make cmake git python3; do
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
# 4. Estructura del SDK
# ------------------------------------------------------------

echo
echo "4. Estructura del SDK"

for path in \
    "$PS3DEV/ppu" \
    "$PS3DEV/ppu/bin" \
    "$PS3DEV/ppu/lib" \
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
echo "Buscando bibliotecas del sistema..."

find "$PS3DEV/ppu" \
    -type f \
    \( -name '*.a' -o -name '*.so' -o -name '*.o' \) \
    -print 2>/dev/null | head -60 || true

echo
echo "Buscando archivos de arranque y linker..."

find "$PS3DEV/ppu" \
    -type f \
    \( -name 'lv2.ld' -o -name 'lv2-crt*.o' -o -name 'lv2-sprx.o' \) \
    -print 2>/dev/null | head -30 || true

# ------------------------------------------------------------
# 5. Archivos del proyecto
# ------------------------------------------------------------

echo
echo "5. Archivos de OpenTV"

if [ -f "$WRAPPER" ]; then
    ok "Wrapper encontrado: $WRAPPER"
else
    fail "No existe el wrapper: $WRAPPER"
fi

if [ -f "$HERE/ppc-asm.h" ]; then
    ok "ppc-asm.h encontrado"
else
    warn "No se encontro ppc-asm.h"
fi

if [ -f "$PS3_ROOT/third_party/ot_mbedtls_config.h" ]; then
    ok "Configuracion mbedTLS encontrada"
else
    warn "No se encontro ot_mbedtls_config.h"
fi

# ------------------------------------------------------------
# 6. Dependencias compiladas
# ------------------------------------------------------------

echo
echo "6. Dependencias"

for lib in \
    "$PS3_BUILD_ROOT/mbed-out/libmbed_ppu.a" \
    "$PS3_BUILD_ROOT/faad-out/libfaad.a"
do
    if [ -f "$lib" ]; then
        ok "Biblioteca encontrada: $lib"
    else
        warn "Biblioteca pendiente: $lib"
    fi
done

# ------------------------------------------------------------
# 7. Prueba real de compilacion PPU con Clang
# ------------------------------------------------------------

echo
echo "7. Prueba de compilacion PPU"

if [ ! -f "$WRAPPER" ]; then
    fail "No se puede probar: falta el wrapper."
else
    PROBE_DIR="$PS3_BUILD_ROOT/diagnostic"
    PROBE_C="$PROBE_DIR/ppu_probe.c"
    PROBE_O="$PROBE_DIR/ppu_probe.o"

    cat > "$PROBE_C" <<'EOF'
typedef unsigned int u32;

u32 opentv_ppu_probe(u32 value)
{
    return value + 1;
}
EOF

    echo "Compilando archivo de prueba..."

    if bash "$WRAPPER" \
        -c "$PROBE_C" \
        -o "$PROBE_O"
    then
        if [ -s "$PROBE_O" ]; then
            ok "Clang genero el objeto PPU: $PROBE_O"
        else
            fail "La compilacion termino sin generar un objeto valido."
        fi
    else
        fail "El wrapper Clang no pudo compilar el archivo de prueba."
    fi
fi

# ------------------------------------------------------------
# 8. Resultado
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
    echo "No continuar con la compilacion del receptor."
    exit 1
fi

echo
echo "DIAGNOSTICO BASICO COMPLETADO."
echo "Esto NO confirma que el enlazado o el paquete PKG funcionen."
echo "Tampoco confirma que FAAD2 y mbedTLS esten compiladas."
