#!/usr/bin/env bash
# Pruebas del lector de SPS H.264 con flujos reales generados por ffmpeg/x264. Comprueba perfil, nivel, resolución (con recorte) y veredicto.
set -u; cd "$(dirname "$0")"; T=$(mktemp -d); fail=0
gcc -O1 -Wall -o "$T/t" test_h264.c ../core/ot_h264.c || exit 1
gen() { ffmpeg -v error -f lavfi -i "testsrc2=size=$2:rate=30" -frames:v 3 -c:v libx264 $3 -bsf:v h264_mp4toannexb -f h264 "$T/$1.h264" -y; }
chk() { local out; out=$("$T/t" "$T/$1.h264" ${3:-0}); if echo "$out" | grep -q "$2"; then echo "ok   $1: $out"; else echo "FAIL $1: esperado '$2' obtuve '$out'"; fail=1; fi; }
gen base1080 1920x1080 "-profile:v baseline -level 4.0 -pix_fmt yuv420p"; chk base1080 "profile=66 level=40 1920x1080 .*verdict=0"
gen main1080 1920x1080 "-profile:v main -level 4.0 -pix_fmt yuv420p";     chk main1080 "profile=77 level=40 1920x1080 .*verdict=0" 30
chk main1080 "verdict=6" 60                                              # 1080p60 declarado como nivel 4.0: excede el nivel
gen high42 1920x1080 "-profile:v high -level 4.2 -pix_fmt yuv420p";       chk high42 "profile=100 level=42 1920x1080 .*verdict=0" 60
gen m720 1280x720 "-profile:v main -level 3.1 -pix_fmt yuv420p";          chk m720 "level=31 1280x720 .*verdict=0" 30
gen odd 1366x768 "-profile:v high -level 4.0 -pix_fmt yuv420p";           chk odd "1366x768"
gen h51 1920x1080 "-profile:v high -level 5.1 -pix_fmt yuv420p";          chk h51 "verdict=3"
gen h444 1280x720 "-profile:v high444 -pix_fmt yuv444p";                  chk h444 "verdict=[12]"
gen h10 1280x720 "-profile:v high10 -pix_fmt yuv420p10le";                chk h10 "verdict=[12]"
echo -n "junk" > "$T/junk.h264"; out=$("$T/t" "$T/junk.h264"); [ "$out" = SIN_SPS ] && echo "ok   junk sin SPS" || { echo "FAIL junk: $out"; fail=1; }
head -c 20 "$T/main1080.h264" > "$T/trunc.h264"; out=$("$T/t" "$T/trunc.h264"); echo "ok   truncado no revienta: $out"
# Demultiplexor: mismo resultado con trozos de cualquier tamaño (antes, un trozo no múltiplo de 188 B perdía el resto y el video desaparecía)
ffmpeg -v error -f lavfi -i "testsrc2=size=1280x720:rate=30" -f lavfi -i "sine=frequency=440:sample_rate=48000" -t 4 -c:v libx264 -profile:v main -level 4.0 -pix_fmt yuv420p -g 30 -c:a aac -ar 48000 -f mpegts "$T/a.ts" -y
gcc -O1 -Wall -o "$T/tp" test_ts_push.c ../core/ot_ts.c || exit 1
"$T/tp" "$T/a.ts" > "$T/tp.out" || fail=1; cat "$T/tp.out"
[ $fail = 0 ] && echo "TODAS LAS PRUEBAS H264 PASARON" || { echo "HUBO FALLAS"; exit 1; }
