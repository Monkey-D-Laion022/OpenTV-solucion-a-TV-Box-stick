#!/bin/sh
# Compila el receptor PS3 y genera opentv-ps3.pkg con la toolchain clang del proyecto (docs/PS3-TOOLCHAIN-CLANG.md).
# Variables: PS3DEV (por defecto /home/claude/ps3dev), MBED (mbedTLS 3.6.x ya compilado: libmbed_ppu.a), FAAD (libfaad.a), MBED_SRC/FAAD_SRC (cabeceras).
set -e
HERE=$(cd "$(dirname "$0")" && pwd); PS3DEV=${PS3DEV:-/home/claude/ps3dev}; OUT=${OUT:-$HERE/build}
MBED_A=${MBED_A:-/home/claude/ps3build/mbed-out/libmbed_ppu.a}; FAAD_A=${FAAD_A:-/home/claude/ps3build/faad-out/libfaad.a}
MBED_INC=${MBED_INC:-/home/claude/ps3build/mbedtls/include}; FAAD_INC=${FAAD_INC:-/home/claude/ps3build/faad2/include}
CC=$PS3DEV/ppu/bin/ppu-gcc; mkdir -p "$OUT/o" "$OUT/pkg/USRDIR"; rm -f "$OUT"/o/*.o
CF="-O2 -std=gnu99 -D__PS3__ -I$PS3DEV/ppu/include -I$HERE/core -I$HERE/src -I$HERE/third_party -I$MBED_INC -I$FAAD_INC -DMBEDTLS_CONFIG_FILE=\"ot_mbedtls_config.h\""
for f in src/main.c src/ps3_av.c core/ot_link.c core/ot_ts.c core/ot_http.c core/ot_mdns.c; do $CC -c $CF "$HERE/$f" -o "$OUT/o/$(basename $f .c).o"; done
$CC -o "$OUT/opentv.elf" "$OUT"/o/*.o "$FAAD_A" "$MBED_A" -lnet -lnetctl -lsysutil -lsysmodule -lvdec -laudio -lrsx -lgcm_sys -lio -lrt -llv2 -lm
$PS3DEV/ppu/bin/ppu-strip -s "$OUT/opentv.elf" -o "$OUT/opentv.stripped.elf"
$PS3DEV/bin/sprxlinker "$OUT/opentv.stripped.elf"
$PS3DEV/bin/fself -n "$OUT/opentv.stripped.elf" "$OUT/pkg/USRDIR/EBOOT.BIN"
$PS3DEV/bin/sfo --title "OpenTV Receptor" --appid "OPENTV001" -f "$HERE/pkg/sfo.xml" "$OUT/pkg/PARAM.SFO"
cp "$HERE/pkg/ICON0.PNG" "$OUT/pkg/ICON0.PNG"
[ -f "$HERE/pkg/opentv.cfg.example" ] && cp "$HERE/pkg/opentv.cfg.example" "$OUT/pkg/USRDIR/opentv.cfg.example"
$PS3DEV/bin/pkg --contentid UP0001-OPENTV001_00-0000000000000000 "$OUT/pkg" "$OUT/opentv-ps3.pkg"
ls -la "$OUT/opentv-ps3.pkg"
