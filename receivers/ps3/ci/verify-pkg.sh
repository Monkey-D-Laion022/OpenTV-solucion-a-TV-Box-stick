#!/bin/sh
# Verifica que lo generado sea un paquete PS3 estructuralmente válido (NO prueba que funcione en consola).
# Uso: verify-pkg.sh PKG CONTENTID STRIPPED_ELF EBOOT READELF PKGDIR
set -eu
PKG=$1; CID=$2; ELF=$3; EBOOT=$4; READELF=${5:-readelf}; PKGDIR=${6:?PKGDIR}; fail=0
ok() { echo "OK    $1"; }; bad() { echo "FALLA $1"; fail=1; }
[ -s "$PKG" ] && ok "existe $PKG ($(wc -c < "$PKG") bytes)" || { bad "no existe $PKG"; exit 1; }
python3 - "$PKG" "$CID" <<'PY' || fail=1
import struct, sys, os
p, cid = sys.argv[1], sys.argv[2]; d = open(p, "rb").read(); e = []
if d[:4] != b"\x7fPKG": e.append("magia distinta de 7F 'PKG'")
rev, typ, info_off, _, hdr, items, total, data_off, data_sz = struct.unpack(">HHIIIIQQQ", d[4:0x30])
if total != len(d): e.append("tamaño total del encabezado (%d) != tamaño del archivo (%d)" % (total, len(d)))
if d[0x30:0x30 + len(cid)].decode("ascii", "replace") != cid: e.append("content id distinto de " + cid)
if items < 3: e.append("pocos elementos (%d)" % items)
if data_off + data_sz > len(d): e.append("datos fuera del archivo")
print(("FALLA " + "; ".join(e)) if e else "OK    encabezado PKG: magia, tamaño total, content id y %d elementos coherentes" % items); sys.exit(1 if e else 0)
PY
# EBOOT.BIN: SELF ("SCE\0") 
head -c4 "$EBOOT" | od -An -c | grep -q 'S   C   E' && ok "EBOOT.BIN es un SELF (SCE)" || bad "EBOOT.BIN no empieza por SCE"
# ELF PPC64 big endian, con tabla de importaciones del sistema rellenada por sprxlinker
"$READELF" -h "$ELF" | grep -q 'ELF64' && "$READELF" -h "$ELF" | grep -q 'big endian' && "$READELF" -h "$ELF" | grep -q 'PowerPC64' && ok "ELF64 PowerPC64 big endian" || bad "ELF no es PPC64 big endian"
"$READELF" -S "$ELF" | grep -q 'lib.stub' && ok "sección .lib.stub presente (sprxlinker)" || bad "falta .lib.stub"
# El PKG de depuración ofusca la tabla de nombres: se contrastan el contenido preparado (PKGDIR) y los totales del encabezado.
for s in PARAM.SFO ICON0.PNG USRDIR/EBOOT.BIN; do [ -s "$PKGDIR/$s" ] && ok "preparado: $s" || bad "falta en el directorio del paquete: $s"; done
n=$(find "$PKGDIR" -mindepth 1 | wc -l); sz=$(find "$PKGDIR" -type f -exec cat {} + | wc -c)
python3 - "$PKG" "$n" "$sz" <<'PY' && ok "el PKG declara $n elementos y datos >= $sz bytes (igual que el directorio preparado)" || bad "el contenido del PKG no coincide con el directorio preparado"
import struct, sys
d = open(sys.argv[1], "rb").read(); n, sz = int(sys.argv[2]), int(sys.argv[3])
_, _, _, _, _, items, total, data_off, data_sz = struct.unpack(">HHIIIIQQQ", d[4:0x30])
sys.exit(0 if items == n and data_sz >= sz else 1)
PY
exit $fail
