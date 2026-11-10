#!/bin/sh
# Compila faad2 (solo AAC-LC) y mbedTLS (solo lo que usa OpenTV Link) con el ppu-gcc real del SDK.
# Entradas: PS3DEV (raíz del SDK), DEPS (salida; por defecto receivers/ps3/build/deps).
# Salida: $DEPS/faad-out/{libfaad.a,include/} y $DEPS/mbed-out/{libmbedcrypto.a,include/}
# Versiones fijadas: faad2 a335040022e14ff9b6c54bbd459590d4c7edffe8 · mbedTLS v3.6.2. Si $DEPS/src/{faad2,mbedtls} ya existen se reutilizan (fetch-deps.sh).
set -eu
HERE=$(cd "$(dirname "$0")/.." && pwd)
: "${PS3DEV:?definir PS3DEV}"; DEPS=${DEPS:-$HERE/build/deps}
CC=${CC:-$PS3DEV/ppu/bin/ppu-gcc}; AR=${AR:-$PS3DEV/ppu/bin/ppu-ar}
FAAD_COMMIT=a335040022e14ff9b6c54bbd459590d4c7edffe8; MBED_TAG=v3.6.2
MACHDEP="-mcpu=cell -mhard-float -fmodulo-sched -ffunction-sections -fdata-sections"
[ -n "${OT_TEST_STANDIN:-}" ] || [ "$($CC -dumpmachine)" = "powerpc64-ps3-elf" ] || { echo "ERROR: $CC no es powerpc64-ps3-elf"; exit 1; }
mkdir -p "$DEPS/src"
# ── fuentes ──
if [ ! -d "$DEPS/src/faad2/libfaad" ]; then
  git clone -q https://github.com/knik0/faad2.git "$DEPS/src/faad2"; git -C "$DEPS/src/faad2" checkout -q "$FAAD_COMMIT"
fi
if [ ! -d "$DEPS/src/mbedtls/library" ]; then
  git clone -q --depth 1 -b "$MBED_TAG" https://github.com/Mbed-TLS/mbedtls.git "$DEPS/src/mbedtls"
fi
[ "$(git -C "$DEPS/src/faad2" rev-parse HEAD)" = "$FAAD_COMMIT" ] || { echo "ERROR: faad2 no está en el commit fijado"; exit 1; }
# ── faad2 ──
F="$DEPS/faad-build"; rm -rf "$F" "$DEPS/faad-out"; mkdir -p "$F" "$DEPS/faad-out/include"
for f in bits cfft common decoder drc error filtbank hcr huffman ic_predict is lt_predict mdct mp4 ms output pns pulse rvlc specrec syntax tns; do
  "$CC" -c -O2 $MACHDEP -DLC_ONLY_DECODER -DDISABLE_SBR -DPACKAGE_VERSION='"2.11"' -DHAVE_STDINT_H=1 -DHAVE_MATH_H=1 -DHAVE_LRINTF=1 -DWORDS_BIGENDIAN=1 -DHAVE_STRING_H=1 \
     -I"$DEPS/src/faad2/libfaad" -I"$DEPS/src/faad2/include" "$DEPS/src/faad2/libfaad/$f.c" -o "$F/$f.o"
done
"$AR" rcs "$DEPS/faad-out/libfaad.a" "$F"/*.o; cp "$DEPS/src/faad2/include/"*.h "$DEPS/faad-out/include/"
# ── mbedTLS (configuración mínima propia; sin TLS, certificados ni PSA) ──
M="$DEPS/mbed-build"; rm -rf "$M" "$DEPS/mbed-out"; mkdir -p "$M" "$DEPS/mbed-out/include"
for b in aes bignum bignum_core bignum_mod bignum_mod_raw constant_time ctr_drbg ecdh ecp ecp_curves ecp_curves_new entropy entropy_poll error gcm hkdf md platform platform_util sha256 cipher cipher_wrap version version_features; do
  "$CC" -c -O2 $MACHDEP -DMBEDTLS_CONFIG_FILE='"ot_mbedtls_config.h"' -I"$HERE/third_party" -I"$DEPS/src/mbedtls/include" -I"$DEPS/src/mbedtls/library" "$DEPS/src/mbedtls/library/$b.c" -o "$M/$b.o"
done
"$AR" rcs "$DEPS/mbed-out/libmbedcrypto.a" "$M"/*.o
cp -r "$DEPS/src/mbedtls/include/mbedtls" "$DEPS/mbed-out/include/"; cp "$HERE/third_party/ot_mbedtls_config.h" "$DEPS/mbed-out/include/"
test -s "$DEPS/faad-out/libfaad.a" && test -s "$DEPS/mbed-out/libmbedcrypto.a" || { echo "ERROR: dependencias no generadas"; exit 1; }
echo "OK deps: $(ls -la "$DEPS/faad-out/libfaad.a" "$DEPS/mbed-out/libmbedcrypto.a" | awk '{print $5, $9}' | tr '\n' ' ')"
