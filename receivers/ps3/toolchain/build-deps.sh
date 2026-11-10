
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

DIAG="$BUILD_ROOT/diagnostic"

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

mkdir -p "$DIAG"

# ------------------------------------------------------------
# 1. Variables de entorno
# ------------------------------------------------------------

echo
echo "1. Variables de entorno"

if [ -d "$PS3DEV" ]; then
    ok "PS3DEV=$PS3DEV"
else
    fail "No existe PS3DEV: $PS3DEV"
fi

if [ -d "$BUILD_ROOT" ]; then
    ok "PS3_BUILD_ROOT=$BUILD_ROOT"
else
    fail "No existe PS3_BUILD_ROOT: $BUILD_ROOT"
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
# 3. Herramientas PS3 necesarias
# ------------------------------------------------------------

echo
echo "3. Herramientas PS3DEV"

for tool in \
    "$PS3DEV/ppu/bin/ppu-strip" \
    "$PS3DEV/bin/sprxlinker" \
    "$PS3DEV/bin/fself" \
    "$PS3DEV/bin/sfo" \
    "$PS3DEV/bin/pkg"
do
    if [ -x "$tool" ]; then
        ok "Herramienta encontrada: $tool"
    else
        fail "Falta herramienta ejecutable: $tool"
    fi
done

# ppu-gcc se inspecciona como referencia del SDK.
if [ -x "$PS3DEV/ppu/bin/ppu-gcc" ]; then
    ok "Compilador original del SDK encontrado."

    "$PS3DEV/ppu/bin/ppu-gcc" -dumpmachine \
        > "$DIAG/ppu-gcc-dumpmachine.txt" 2>&1 || true

    "$PS3DEV/ppu/bin/ppu-gcc" --version \
        > "$DIAG/ppu-gcc-version.txt" 2>&1 || true

    echo "Target informado por ppu-gcc:"
    cat "$DIAG/ppu-gcc-dumpmachine.txt"
else
    warn "No se encontro ppu-gcc para comparar el target."
fi

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
        fail "Directorio ausente: $path"
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
    "$PS3_ROOT/third_party/ot_mbedtls_config.h"
do
    if [ -f "$f" ]; then
        ok "Encontrado: $f"
    else
        fail "Falta archivo: $f"
    fi
done

if [ -x "$WRAPPER" ]; then
    ok "Wrapper ejecutable."
else
    warn "El wrapper no tiene permiso de ejecucion; se intentara corregir."
    chmod +x "$WRAPPER" 2>/dev/null || fail "No se pudo dar permiso al wrapper."
fi

# ------------------------------------------------------------
# 6. Fuentes descargadas
# ------------------------------------------------------------

echo
echo "6. Fuentes de dependencias"

if [ -f "$BUILD_ROOT/faad2/CMakeLists.txt" ]; then
    ok "Fuentes FAAD2 encontradas."
else
    fail "No se encontraron las fuentes FAAD2."
fi

if [ -f "$BUILD_ROOT/mbedtls/CMakeLists.txt" ]; then
    ok "Fuentes mbedTLS encontradas."
else
    fail "No se encontraron las fuentes mbedTLS."
fi

# ------------------------------------------------------------
# 7. Bibliotecas compiladas y arquitectura de sus objetos
# ------------------------------------------------------------

echo
echo "7. Bibliotecas compiladas"

FAAD_LIB="$BUILD_ROOT/faad-out/libfaad.a"
MBED_LIB="$BUILD_ROOT/mbed-out/libmbed_ppu.a"

check_archive() {
    local lib="$1"
    local name="$2"
    local extract_dir="$DIAG/${name}-objects"
    local members="$DIAG/${name}-members.txt"
    local object
    local description
    local object_count=0

    if [ ! -s "$lib" ]; then
        fail "Biblioteca ausente o vacia: $lib"
        return
    fi

    ok "Biblioteca encontrada: $lib"

    if ! ar t "$lib" > "$members"; then
        fail "Archivo AR invalido: $lib"
        return
    fi

    if [ ! -s "$members" ]; then
        fail "La biblioteca no contiene miembros: $lib"
        return
    fi

    rm -rf "$extract_dir"
    mkdir -p "$extract_dir"

    if ! (
        cd "$extract_dir"
        ar x "$lib"
    ); then
        fail "No se pudieron extraer los objetos de $lib"
        return
    fi

    while IFS= read -r -d '' object; do
        object_count=$((object_count + 1))
        description="$(file -b "$object")"

        echo "$description" >> "$DIAG/${name}-object-types.txt"

        if ! grep -Eqi 'PowerPC|ppc64' <<< "$description"; then
            fail "Objeto no identificado como PowerPC: $object ($description)"
        fi
    done < <(find "$extract_dir" -type f -name '*.o' -print0)

    if [ "$object_count" -eq 0 ]; then
        fail "No se encontraron objetos .o dentro de $lib"
    else
        ok "$name: $object_count objetos inspeccionados."
    fi
}

: > "$DIAG/faad-object-types.txt"
: > "$DIAG/mbed-object-types.txt"

check_archive "$FAAD_LIB" "faad"
check_archive "$MBED_LIB" "mbed"

# ------------------------------------------------------------
# 8. Encabezados de dependencias
# ------------------------------------------------------------

echo
echo "8. Encabezados"

for header in \
    "$BUILD_ROOT/faad-out/include/neaacdec.h" \
    "$BUILD_ROOT/faad-out/include/faad.h" \
    "$BUILD_ROOT/mbed-out/include/mbedtls/ssl.h" \
    "$BUILD_ROOT/mbed-out/include/ot_mbedtls_config.h"
do
    if [ -s "$header" ]; then
        ok "Encabezado encontrado: $header"
    else
        fail "Falta encabezado: $header"
    fi
done

# ------------------------------------------------------------
# 9. Prueba de compilacion PPU
# ------------------------------------------------------------

echo
echo "9. Prueba de compilacion PPU"

PROBE_C="$DIAG/ppu_probe.c"
PROBE_O="$DIAG/ppu_probe.o"

cat > "$PROBE_C" <<'EOF'
typedef unsigned int u32;

u32 opentv_ppu_probe(u32 value)
{
    return value + 1;
}
EOF

if [ -x "$WRAPPER" ]; then
    if "$WRAPPER" -c "$PROBE_C" -o "$PROBE_O" \
        > "$DIAG/clang-probe.log" 2>&1; then

        if [ -s "$PROBE_O" ]; then
            ok "Objeto de prueba generado."

            DESCRIPTION="$(file "$PROBE_O")"
            echo "$DESCRIPTION" | tee "$DIAG/ppu-probe-file.txt"

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
        cat "$DIAG/clang-probe.log"
    fi
else
    fail "No se puede probar: falta el wrapper ejecutable."
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

echo
echo "Informes guardados en:"
echo "$DIAG"

if [ "$ERRORS" -gt 0 ]; then
    echo
    echo "DIAGNOSTICO FALLIDO."
    echo "No continuar con el receptor."
    exit 1
fi

echo
echo "DIAGNOSTICO BASICO COMPLETADO."
echo "Los objetos se identificaron por arquitectura."
echo "Esto NO confirma ABI PS3."
echo "Tampoco confirma el enlazado ni el PKG."
