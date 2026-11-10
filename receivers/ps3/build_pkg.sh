
#!/usr/bin/env bash
set -Eeuo pipefail

# ============================================================
# OpenTV PS3 - Compilacion y empaquetado del receptor
# Toolchain experimental Clang / PPU
# ============================================================

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

PS3DEV="${PS3DEV:?ERROR: PS3DEV no esta definido}"
BUILD_ROOT="${PS3_BUILD_ROOT:?ERROR: PS3_BUILD_ROOT no esta definido}"

OUT="${OUT:-$HERE/build}"

CC="$HERE/toolchain/ppu-gcc.clang-wrapper"

# Bibliotecas compiladas
MBED_A="${MBED_A:-$BUILD_ROOT/mbed-out/libmbed_ppu.a}"
FAAD_A="${FAAD_A:-$BUILD_ROOT/faad-out/libfaad.a}"

# Headers preparados por los scripts de compilacion
MBED_INC="${MBED_INC:-$BUILD_ROOT/mbed-out/include}"
FAAD_INC="${FAAD_INC:-$BUILD_ROOT/faad-out/include}"

echo "============================================"
echo " OpenTV PS3 - Receptor"
echo "============================================"

# ------------------------------------------------------------
# 1. Comprobar herramientas y dependencias
# ------------------------------------------------------------

echo
echo "1. Comprobando dependencias..."

for f in \
    "$CC" \
    "$MBED_A" \
    "$FAAD_A" \
    "$MBED_INC/mbedtls/ssl.h" \
    "$MBED_INC/ot_mbedtls_config.h" \
    "$FAAD_INC/neaacdec.h" \
    "$FAAD_INC/faad.h" \
    "$HERE/third_party/ot_mbedtls_config.h" \
    "$PS3DEV/ppu/include"
do
    if [ ! -e "$f" ]; then
        echo "ERROR: falta dependencia: $f"
        exit 1
    fi
done

if [ ! -x "$CC" ]; then
    echo "ERROR: el wrapper no es ejecutable: $CC"
    exit 1
fi

if [ ! -x "$PS3DEV/ppu/bin/ppu-strip" ]; then
    echo "ERROR: falta ppu-strip."
    exit 1
fi

for tool in sprxlinker fself sfo pkg; do
    if ! command -v "$tool" >/dev/null 2>&1 &&
       [ ! -x "$PS3DEV/bin/$tool" ]; then
        echo "ERROR: falta herramienta PS3: $tool"
        exit 1
    fi
done

echo "[OK] Dependencias encontradas."

# ------------------------------------------------------------
# 2. Comprobar fuentes
# ------------------------------------------------------------

echo
echo "2. Comprobando fuentes..."

for f in \
    src/main.c \
    src/ps3_av.c \
    core/ot_link.c \
    core/ot_ts.c \
    core/ot_http.c \
    core/ot_mdns.c \
    pkg/sfo.xml \
    pkg/ICON0.PNG
do
    if [ ! -f "$HERE/$f" ]; then
        echo "ERROR: falta archivo fuente: $HERE/$f"
        exit 1
    fi
done

echo "[OK] Fuentes encontradas."

# ------------------------------------------------------------
# 3. Preparar directorios
# ------------------------------------------------------------

echo
echo "3. Preparando directorios..."

mkdir -p "$OUT/o"
mkdir -p "$OUT/pkg/USRDIR"

rm -f "$OUT"/o/*.o

# ------------------------------------------------------------
# 4. Configurar compilacion
# ------------------------------------------------------------

echo
echo "4. Configurando compilacion..."

CF=(
    -O2
    -std=gnu99
    -D__PS3__
    "-I$PS3DEV/ppu/include"
    "-I$HERE/core"
    "-I$HERE/src"
    "-I$HERE/third_party"
    "-I$MBED_INC"
    "-I$FAAD_INC"
    '-DMBEDTLS_CONFIG_FILE="ot_mbedtls_config.h"'
)

# ------------------------------------------------------------
# 5. Compilar receptor
# ------------------------------------------------------------

echo
echo "5. Compilando receptor con Clang..."

SOURCES=(
    src/main.c
    src/ps3_av.c
    core/ot_link.c
    core/ot_ts.c
    core/ot_http.c
    core/ot_mdns.c
)

for f in "${SOURCES[@]}"; do
    OBJECT="$OUT/o/$(basename "$f" .c).o"

    echo "Compilando: $f"

    "$CC" -c "${CF[@]}" "$HERE/$f" -o "$OBJECT"

    if [ ! -s "$OBJECT" ]; then
        echo "ERROR: no se genero el objeto: $OBJECT"
        exit 1
    fi
done

echo "[OK] Objetos compilados."

# ------------------------------------------------------------
# 6. Enlazar receptor
# ------------------------------------------------------------

echo
echo "6. Enlazando receptor..."

"$CC" \
    -o "$OUT/opentv.elf" \
    "$OUT"/o/*.o \
    "$FAAD_A" \
    "$MBED_A" \
    -lnet \
    -lnetctl \
    -lsysutil \
    -lsysmodule \
    -lvdec \
    -laudio \
    -lrsx \
    -lgcm_sys \
    -lio \
    -lrt \
    -llv2 \
    -lm

if [ ! -s "$OUT/opentv.elf" ]; then
    echo "ERROR: no se genero opentv.elf."
    exit 1
fi

# ------------------------------------------------------------
# 7. Generar archivos del paquete
# ------------------------------------------------------------

echo
echo "7. Generando archivos del paquete..."

"$PS3DEV/ppu/bin/ppu-strip" -s \
    "$OUT/opentv.elf" \
    -o "$OUT/opentv.stripped.elf"

"$PS3DEV/bin/sprxlinker" "$OUT/opentv.stripped.elf"

"$PS3DEV/bin/fself" -n \
    "$OUT/opentv.stripped.elf" \
    "$OUT/pkg/USRDIR/EBOOT.BIN"

"$PS3DEV/bin/sfo" \
    --title "OpenTV Receptor" \
    --appid "OPENTV001" \
    -f "$HERE/pkg/sfo.xml" \
    "$OUT/pkg/PARAM.SFO"

cp "$HERE/pkg/ICON0.PNG" "$OUT/pkg/ICON0.PNG"

if [ -f "$HERE/pkg/opentv.cfg.example" ]; then
    cp "$HERE/pkg/opentv.cfg.example" \
        "$OUT/pkg/USRDIR/opentv.cfg.example"
fi

# ------------------------------------------------------------
# 8. Crear PKG
# ------------------------------------------------------------

echo
echo "8. Creando paquete PKG..."

"$PS3DEV/bin/pkg" \
    --contentid UP0001-OPENTV001_00-0000000000000000 \
    "$OUT/pkg" \
    "$OUT/opentv-ps3.pkg"

if [ ! -s "$OUT/opentv-ps3.pkg" ]; then
    echo "ERROR: no se genero el paquete."
    exit 1
fi

echo
echo "============================================"
echo " PAQUETE OPEN TV PS3 GENERADO"
echo "============================================"

ls -lh "$OUT/opentv-ps3.pkg"

echo
echo "IMPORTANTE:"
echo "La compilacion no demuestra compatibilidad ABI ni funcionamiento."
echo "El receptor debera probarse en RPCS3 o hardware PS3."
