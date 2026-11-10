
#!/usr/bin/env bash
set -Eeuo pipefail

# ============================================================
# OpenTV PS3 - Compilacion de FAAD2 para PPU con Clang
# No genera ejecutables Linux.
# No habilita enlazado final PS3.
# ============================================================

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

BUILD_ROOT="${PS3_BUILD_ROOT:?ERROR: PS3_BUILD_ROOT no esta definido}"
PS3DEV="${PS3DEV:?ERROR: PS3DEV no esta definido}"

FAAD_DIR="$BUILD_ROOT/faad2"
BUILD_DIR="$BUILD_ROOT/faad-build"
OUT_DIR="$BUILD_ROOT/faad-out"
INSPECT_DIR="$BUILD_ROOT/faad-inspect"

WRAPPER="$HERE/ppu-gcc.clang-wrapper"
TOOLCHAIN="$HERE/ps3-ppu-clang.cmake"

EXPECTED_COMMIT="a335040022e14ff9b6c54bbd459590d4c7edffe8"

echo "============================================"
echo " OpenTV PS3 - Construccion de FAAD2"
echo "============================================"

# ------------------------------------------------------------
# 1. Comprobar herramientas
# ------------------------------------------------------------

for tool in cmake clang ar ranlib file git; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        echo "ERROR: falta herramienta: $tool"
        exit 1
    fi
done

if [ ! -d "$PS3DEV/ppu/powerpc64-ps3-elf/include" ]; then
    echo "ERROR: faltan los headers PPU del SDK."
    exit 1
fi

if [ ! -f "$WRAPPER" ]; then
    echo "ERROR: falta el wrapper Clang."
    exit 1
fi

if [ ! -f "$TOOLCHAIN" ]; then
    echo "ERROR: falta ps3-ppu-clang.cmake."
    exit 1
fi

chmod +x "$WRAPPER"

# ------------------------------------------------------------
# 2. Comprobar fuentes
# ------------------------------------------------------------

echo
echo "1. Comprobando fuentes FAAD2..."

if [ ! -d "$FAAD_DIR/.git" ]; then
    echo "ERROR: no existe el repositorio FAAD2."
    echo "Ejecutar primero fetch-deps.sh."
    exit 1
fi

ACTUAL_COMMIT="$(git -C "$FAAD_DIR" rev-parse HEAD)"

if [ "$ACTUAL_COMMIT" != "$EXPECTED_COMMIT" ]; then
    echo "ERROR: commit FAAD2 incorrecto."
    echo "Esperado: $EXPECTED_COMMIT"
    echo "Actual:   $ACTUAL_COMMIT"
    exit 1
fi

if [ ! -f "$FAAD_DIR/CMakeLists.txt" ] ||
   [ ! -f "$FAAD_DIR/include/neaacdec.h" ] ||
   [ ! -f "$FAAD_DIR/include/faad.h.in" ]; then
    echo "ERROR: fuentes o plantilla de headers FAAD2 incompletas."
    exit 1
fi

echo "[OK] Fuentes FAAD2 verificadas."

# ------------------------------------------------------------
# 3. Preparar directorios
# ------------------------------------------------------------

echo
echo "2. Preparando directorios..."

rm -rf "$BUILD_DIR" "$OUT_DIR" "$INSPECT_DIR"

mkdir -p "$BUILD_DIR"
mkdir -p "$OUT_DIR/include"
mkdir -p "$INSPECT_DIR"

# ------------------------------------------------------------
# 4. Configurar CMake
# ------------------------------------------------------------

echo
echo "3. Configurando compilacion cruzada..."

cmake \
    -S "$FAAD_DIR" \
    -B "$BUILD_DIR" \
    -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" \
    -DCMAKE_BUILD_TYPE=Release \
    -DBUILD_SHARED_LIBS=OFF \
    -DFAAD_BUILD_CLI=OFF \
    -DFAAD_BUNDLED_MODE=OFF \
    -DCMAKE_TRY_COMPILE_TARGET_TYPE=STATIC_LIBRARY \
    -DCMAKE_C_FLAGS="-O2 -ffunction-sections -fdata-sections" \
    -DCMAKE_INSTALL_PREFIX="$OUT_DIR"

# ------------------------------------------------------------
# 5. Comprobar compilador detectado
# ------------------------------------------------------------

echo
echo "4. Comprobando compilador..."

if ! grep -Fq "CMAKE_C_COMPILER:FILEPATH=$WRAPPER" \
    "$BUILD_DIR/CMakeCache.txt" &&
   ! grep -Fq "CMAKE_C_COMPILER:STRING=$WRAPPER" \
    "$BUILD_DIR/CMakeCache.txt" &&
   ! grep -Fq "CMAKE_C_COMPILER:UNINITIALIZED=$WRAPPER" \
    "$BUILD_DIR/CMakeCache.txt"; then

    echo "ERROR: CMake no registro el wrapper como compilador."
    grep "CMAKE_C_COMPILER:" "$BUILD_DIR/CMakeCache.txt" || true
    exit 1
fi

echo "[OK] CMake registro el wrapper."

# ------------------------------------------------------------
# 6. Comprobar headers generados
# ------------------------------------------------------------

echo
echo "5. Comprobando headers generados por CMake..."

if [ ! -s "$BUILD_DIR/include/faad.h" ] ||
   [ ! -s "$BUILD_DIR/include/neaacdec.h" ]; then
    echo "ERROR: CMake no genero los headers esperados."
    echo "Contenido de include:"
    find "$BUILD_DIR" -maxdepth 3 -type f -path '*/include/*' -print
    exit 1
fi

echo "[OK] Headers generados."

# ------------------------------------------------------------
# 7. Compilar biblioteca
# ------------------------------------------------------------

echo
echo "6. Compilando biblioteca FAAD2..."

JOBS="${JOBS:-2}"

cmake \
    --build "$BUILD_DIR" \
    --target faad \
    --parallel "$JOBS"

# ------------------------------------------------------------
# 8. Localizar y verificar biblioteca
# ------------------------------------------------------------

echo
echo "7. Verificando biblioteca..."

LIBRARY="$BUILD_DIR/libfaad.a"

if [ ! -s "$LIBRARY" ]; then
    echo "ERROR: no se genero libfaad.a en la ruta esperada."
    echo "Buscando posibles ubicaciones..."
    find "$BUILD_DIR" -name "libfaad.a" -type f -print
    exit 1
fi

if ! ar t "$LIBRARY" > "$BUILD_ROOT/faad-archive-members.txt"; then
    echo "ERROR: el archivo generado no es un archivo AR valido."
    exit 1
fi

if [ ! -s "$BUILD_ROOT/faad-archive-members.txt" ]; then
    echo "ERROR: la biblioteca no contiene objetos."
    exit 1
fi

# ------------------------------------------------------------
# 9. Verificar arquitectura de los objetos
# ------------------------------------------------------------

echo
echo "8. Inspeccionando arquitectura de objetos..."

OBJECT="$INSPECT_DIR/object.tmp"
OBJECT_COUNT=0

while IFS= read -r member; do
    [ -n "$member" ] || continue

    if ! ar p "$LIBRARY" "$member" > "$OBJECT"; then
        echo "ERROR: no se pudo extraer el objeto: $member"
        exit 1
    fi

    if [ ! -s "$OBJECT" ]; then
        echo "ERROR: objeto vacio: $member"
        exit 1
    fi

    DESCRIPTION="$(file -b "$OBJECT")"

    echo "$member: $DESCRIPTION"

    if ! printf '%s\n' "$DESCRIPTION" | grep -Eqi 'PowerPC|powerpc'; then
        echo "ERROR: objeto sin arquitectura PowerPC reconocible."
        echo "Objeto: $member"
        exit 1
    fi

    OBJECT_COUNT=$((OBJECT_COUNT + 1))

done < "$BUILD_ROOT/faad-archive-members.txt"

rm -f "$OBJECT"

if [ "$OBJECT_COUNT" -eq 0 ]; then
    echo "ERROR: no se inspeccionaron objetos."
    exit 1
fi

echo "[OK] $OBJECT_COUNT objetos con arquitectura PowerPC reconocible."

# ------------------------------------------------------------
# 10. Verificar headers
# ------------------------------------------------------------

echo
echo "9. Comprobando headers..."

if [ ! -s "$FAAD_DIR/include/neaacdec.h" ]; then
    echo "ERROR: falta neaacdec.h."
    exit 1
fi

if [ ! -s "$BUILD_DIR/include/faad.h" ] ||
   [ ! -s "$BUILD_DIR/include/neaacdec.h" ]; then
    echo "ERROR: faltan headers generados por CMake."
    exit 1
fi

# ------------------------------------------------------------
# 11. Instalar biblioteca y headers
# ------------------------------------------------------------

echo
echo "10. Instalando salida..."

install -m 0644 "$LIBRARY" "$OUT_DIR/libfaad.a"
install -m 0644 "$BUILD_DIR/include/neaacdec.h" "$OUT_DIR/include/neaacdec.h"
install -m 0644 "$BUILD_DIR/include/faad.h" "$OUT_DIR/include/faad.h"

# ------------------------------------------------------------
# 12. Verificacion final
# ------------------------------------------------------------

if [ ! -s "$OUT_DIR/libfaad.a" ]; then
    echo "ERROR: biblioteca final vacia."
    exit 1
fi

if [ ! -s "$OUT_DIR/include/neaacdec.h" ] ||
   [ ! -s "$OUT_DIR/include/faad.h" ]; then
    echo "ERROR: faltan headers en la salida final."
    exit 1
fi

if ! ar t "$OUT_DIR/libfaad.a" >/dev/null; then
    echo "ERROR: biblioteca instalada invalida."
    exit 1
fi

echo
echo "============================================"
echo " FAAD2 COMPILADA Y VERIFICADA"
echo "============================================"

echo "Biblioteca:"
ls -lh "$OUT_DIR/libfaad.a"

echo
echo "Headers:"
ls -lh "$OUT_DIR/include/"

echo
echo "Objetos inspeccionados:"
echo "$OBJECT_COUNT"

echo
echo "IMPORTANTE:"
echo "La arquitectura PowerPC reconocible fue comprobada."
echo "Esto NO demuestra compatibilidad ABI PS3."
echo "El enlazado final permanece deshabilitado."
echo "La biblioteca aun debe superar las siguientes verificaciones."

echo
echo "Proceso FAAD2 finalizado."
