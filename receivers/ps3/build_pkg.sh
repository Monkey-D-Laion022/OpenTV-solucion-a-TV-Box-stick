
#!/usr/bin/env bash
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
PS3DEV="${PS3DEV:?ERROR: PS3DEV no está definido}"
BUILD_ROOT="${PS3_BUILD_ROOT:?ERROR: PS3_BUILD_ROOT no está definido}"

OUT="${OUT:-$HERE/build}"

CC="$HERE/toolchain/ppu-gcc.clang-wrapper"

MBED_A="${MBED_A:-$BUILD_ROOT/mbed-out/libmbed_ppu.a}"
FAAD_A="${FAAD_A:-$BUILD_ROOT/faad-out/libfaad.a}"

MBED_INC="${MBED_INC:-$BUILD_ROOT/mbedtls/include}"
FAAD_INC="${FAAD_INC:-$BUILD_ROOT/faad2/include}"

echo "Comprobando dependencias..."

for f in "$CC" "$MBED_A" "$FAAD_A" \
         "$MBED_INC" "$FAAD_INC"; do
  if [ ! -e "$f" ]; then
    echo "ERROR: falta dependencia: $f"
    exit 1
  fi
done

mkdir -p "$OUT/o" "$OUT/pkg/USRDIR"
rm -f "$OUT"/o/*.o

CF="-O2 -std=gnu99 -D__PS3__ \
-I$PS3DEV/ppu/include \
-I$HERE/core -I$HERE/src -I$HERE/third_party \
-I$MBED_INC -I$FAAD_INC \
-DMBEDTLS_CONFIG_FILE=\"ot_mbedtls_config.h\""

echo "Compilando receptor con Clang..."

for f in \
  src/main.c \
  src/ps3_av.c \
  core/ot_link.c \
  core/ot_ts.c \
  core/ot_http.c \
  core/ot_mdns.c
do
  "$CC" -c $CF "$HERE/$f" \
    -o "$OUT/o/$(basename "$f" .c).o"
done

echo "Enlazando..."

"$CC" -o "$OUT/opentv.elf" "$OUT"/o/*.o \
  "$FAAD_A" "$MBED_A" \
  -lnet -lnetctl -lsysutil -lsysmodule \
  -lvdec -laudio -lrsx -lgcm_sys \
  -lio -lrt -llv2 -lm

echo "Generando archivos del paquete..."

"$PS3DEV/ppu/bin/ppu-strip" -s \
  "$OUT/opentv.elf" -o "$OUT/opentv.stripped.elf"

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

"$PS3DEV/bin/pkg" \
  --contentid UP0001-OPENTV001_00-0000000000000000 \
  "$OUT/pkg" "$OUT/opentv-ps3.pkg"

ls -lh "$OUT/opentv-ps3.pkg"

echo "Paquete OpenTV PS3 generado."
