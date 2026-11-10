
#!/usr/bin/env bash
set -Eeuo pipefail

# ============================================================
# OpenTV PS3 - Diagnostico de SDK, Clang y dependencias PPU
# No compila ni enlaza dependencias.
# ============================================================

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PS3_ROOT="$(cd "$HERE/../../.." && pwd)"

BUILD_ROOT="${PS3_BUILD_ROOT:?ERROR: PS3_BUILD_ROOT no esta definido}"
PS3DEV="${PS3DEV:?ERROR: PS3DEV no esta definido}"

WRAPPER="$HERE/ppu-gcc.clang-wrapper"
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
# 1. Variables y rutas
# ------------------------------------------------------------

echo
echo "1. Variables y rutas"

[ -d "$PS3DEV" ] && ok "PS3DEV: $PS3DEV" || fail "No existe PS3DEV."
[ -d "$BUILD_ROOT" ] && ok "BUILD_ROOT: $BUILD_ROOT" || fail "No existe BUILD_ROOT."

if [ -d "$PS3_ROOT" ]; then
    ok "Raiz del repositorio: $PS3_ROOT"
else
    fail "No se encontro la raiz del repositorio."
fi

# La configuracion real esta en receivers/ps3/third_party.
CONFIG="$PS3_ROOT/receivers/ps3/third_party/ot_mbedtls_config.h"

echo "Ruta de configuracion: $CONFIG"

if [ -s "$CONFIG" ]; then
    ok "Configuracion mbedTLS encontrada."
else
    fail "No se encontro receivers/ps3/third_party/ot_mbedtls_config.h."
fi

if [ -n "${NEWLIB_SRC:-}" ] && [ -d "$NEWLIB_SRC" ]; then
    ok "Fuentes Newlib: $NEWLIB_SRC"
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
        fail "No se encontro $tool."
    fi
done

if command -v readelf >/dev/null 2>&1; then
    ok "readelf disponible."
else
    warn "readelf no esta disponible; se usara file."
fi

# ------------------------------------------------------------
# 3. Herramientas PS3DEV
# ------------------------------------------------------------

echo
echo "3. Herramientas PS3DEV"

for tool in \
    "$PS3DEV/ppu/bin/ppu-gcc" \
    "$PS3DEV/ppu/bin/ppu-strip" \
    "$PS3DEV/bin/sprxlinker" \
    "$PS3DEV/bin/fself" \
    "$PS3DEV/bin/sfo" \
    "$PS3DEV/bin/pkg"
do
    if [ -x "$tool" ]; then
        ok "Encontrada: $tool"
    else
        warn "No encontrada o no ejecutable: $tool"
    fi
done

if [ -x "$PS3DEV/ppu/bin/ppu-gcc" ]; then
    "$PS3DEV/ppu/bin/ppu-gcc" -dumpmachine \
        > "$DIAG/ppu-gcc-dumpmachine.txt" 2>&1 || true

    "$PS3DEV/ppu/bin/ppu-gcc" --version \
        > "$DIAG/ppu-gcc-version.txt" 2>&1 || true

    echo "Target de referencia:"
    cat "$DIAG/ppu-gcc-dumpmachine.txt"
fi

# ------------------------------------------------------------
# 4. Estructura del SDK
# ------------------------------------------------------------

echo
echo "4. Estructura del SDK"

for path in \
    "$PS3DEV/ppu" \
    "$PS3DEV/ppu/bin" \
    "$PS3DEV/ppu/ppu/lib" \
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
echo "Buscando bibliotecas del SDK en todas sus carpetas..."

: > "$DIAG/sdk-libraries.txt"

find "$PS3DEV" \
    \( -type f -o -type l \) \
    \( -name 'libc.a' -o -name 'libsysbase.a' -o -name 'lib*.a' \) \
    -print 2>/dev/null |
    sort -u > "$DIAG/sdk-libraries.txt" || true

if [ -s "$DIAG/sdk-libraries.txt" ]; then
    cat "$DIAG/sdk-libraries.txt"
else
    warn "No se encontraron bibliotecas estaticas en el SDK."
fi

LIBC_FOUND=0
SYSBASE_FOUND=0

while IFS= read -r path; do
    case "$(basename "$path")" in
        libc.a) LIBC_FOUND=1 ;;
        libsysbase.a) SYSBASE_FOUND=1 ;;
    esac
done < "$DIAG/sdk-libraries.txt"

if [ "$LIBC_FOUND" -eq 1 ]; then
    ok "Se encontro libc.a."
else
    warn "No se encontro libc.a. Su ubicacion o construccion sigue pendiente."
fi

if [ "$SYSBASE_FOUND" -eq 1 ]; then
    ok "Se encontro libsysbase.a."
else
    warn "No se encontro libsysbase.a. Su ubicacion o construccion sigue pendiente."
fi

echo
echo "Archivos CRT y linker:"

find "$PS3DEV/ppu" \
    \( -type f -o -type l \) \
    \( -name 'lv2.ld' -o -name 'lv2-crt*.o' -o -name 'lv2-sprx.o' \) \
    -print 2>/dev/null |
    sort -u | tee "$DIAG/sdk-crt-files.txt" || true

# ------------------------------------------------------------
# 5. Archivos del proyecto
# ------------------------------------------------------------

echo
echo "5. Archivos del proyecto"

for f in \
    "$WRAPPER" \
    "$HERE/ps3-ppu-clang.cmake" \
    "$CONFIG"
do
    if [ -s "$f" ]; then
        ok "Encontrado: $f"
    else
        fail "Falta archivo: $f"
    fi
done

if [ -f "$WRAPPER" ]; then
    chmod +x "$WRAPPER" || fail "No se pudo dar permiso al wrapper."
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
# 7. Inspeccion de bibliotecas estaticas
# ------------------------------------------------------------

echo
echo "7. Bibliotecas compiladas"

: > "$DIAG/faad-object-types.txt"
: > "$DIAG/mbed-object-types.txt"

check_archive() {
    local lib="$1"
    local name="$2"
    local extract_dir="$DIAG/${name}-objects"
    local members="$DIAG/${name}-members.txt"
    local types="$DIAG/${name}-object-types.txt"
    local object
    local description
    local object_count=0
    local member_count=0

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

    member_count="$(wc -l < "$members")"

    rm -rf "$extract_dir"
    mkdir -p "$extract_dir"

    if ! (cd "$extract_dir" && ar x "$lib"); then
        fail "No se pudieron extraer los objetos: $lib"
        return
    fi

    while IFS= read -r -d '' object; do
        object_count=$((object_count + 1))
        description="$(file -b "$object")"

        echo "$description" >> "$types"

        if ! grep -Eqi 'PowerPC|ppc64' <<< "$description"; then
            fail "Objeto no identificado como PowerPC: $object ($description)"
        fi

        if command -v readelf >/dev/null 2>&1; then
            readelf -h "$object" \
                >> "$DIAG/${name}-elf-headers.txt" 2>&1 || true
        fi
    done < <(find "$extract_dir" -type f -print0)

    if [ "$object_count" -eq 0 ]; then
        fail "No se encontraron objetos extraidos de $lib."
        return
    fi

    if [ "$object_count" -ne "$member_count" ]; then
        warn "$name: miembros AR ($member_count) y archivos extraidos ($object_count) no coinciden. Revisar duplicados."
    fi

    ok "$name: $object_count archivos inspeccionados."
}

FAAD_LIB="$BUILD_ROOT/faad-out/libfaad.a"

# La Fase 4 genera libmbedcrypto.a, no libmbed_ppu.a.
MBED_LIB="$BUILD_ROOT/mbed-out/libmbedcrypto.a"

: > "$DIAG/faad-elf-headers.txt"
: > "$DIAG/mbed-elf-headers.txt"

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
        ok "Encabezado: $header"
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

            DESCRIPTION="$(file -b "$PROBE_O")"
            echo "$DESCRIPTION" | tee "$DIAG/ppu-probe-file.txt"

            if grep -Eqi 'PowerPC|ppc64' <<< "$DESCRIPTION"; then
                ok "Objeto identificado como PowerPC."
            else
                fail "El objeto no fue identificado como PowerPC."
            fi

            if command -v readelf >/dev/null 2>&1; then
                readelf -h "$PROBE_O" \
                    > "$DIAG/ppu-probe-elf-header.txt" 2>&1 || true
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

echo
echo "IMPORTANTE:"
echo "La arquitectura PowerPC detectada NO confirma ABI PS3."
echo "La presencia de bibliotecas NO confirma que sean compatibles."
echo "No se ha probado el enlazado ni la generacion de PKG."
echo "El wrapper mantiene bloqueado el enlazado final."

if [ "$ERRORS" -gt 0 ]; then
    echo
    echo "DIAGNOSTICO FALLIDO."
    echo "No continuar con el receptor."
    exit 1
fi

echo
echo "DIAGNOSTICO BASICO COMPLETADO."
echo "Revisar los avisos antes de habilitar nuevas etapas."
