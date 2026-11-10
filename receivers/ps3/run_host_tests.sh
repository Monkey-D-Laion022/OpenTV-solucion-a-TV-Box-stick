#!/bin/sh
# Pruebas del núcleo portable en un PC (necesita gcc, ffmpeg, python3). No prueba la capa de la PS3.
set -e
cd "$(dirname "$0")/test"; T=$(mktemp -d)
gcc -std=c99 -Wall -Wextra -O2 -o $T/ts test_ts.c ../core/ot_ts.c
ffmpeg -loglevel error -y -f lavfi -i testsrc=size=640x360:rate=30 -f lavfi -i sine=frequency=440:sample_rate=48000 -t 5 -c:v libx264 -g 30 -bf 0 -c:a aac -f mpegts $T/a.ts
for c in 100000 188 7; do $T/ts $T/a.ts $c | grep -q "video=150 key=5 audio=236" && echo "chunk $c OK"; done
# mDNS (anuncio DNS-SD)
gcc -D_GNU_SOURCE -O2 -Wall -Wno-misleading-indentation -o $T/mdns test_mdns.c ../core/ot_mdns.c && $T/mdns
