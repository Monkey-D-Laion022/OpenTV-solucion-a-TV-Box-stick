#!/bin/sh
# Prueba de OpenTV Link (receptor en C + mbedTLS) contra un emisor de prueba en Python. Requiere: gcc, python3 + cryptography, ffmpeg, mbedTLS compilado (ver README).
# Uso: sh test/run_link_tests.sh RUTA_LINK_HOST CLIP.ts
H=${1:-/tmp/link_host}; CLIP=${2:-/tmp/clip.ts}; D=$(mktemp -d); S="$(dirname "$0")/../tools/sender-py/opentv_sender_test.py"; fail=0
run() { # nombre modo_receptor args_emisor
  name=$1; mode=$2; shift 2; port=$((9100 + RANDOM % 500))
  $H $port $D/secrets $mode $D/$name.ts > $D/$name.rx 2>&1 &
  sleep 0.4; python3 -I $S --port $port --store $D/sender.json "$@" > $D/$name.tx 2>&1; echo "$name tx_rc=$?" >> $D/$name.tx; wait
}
chk() { if eval "$2"; then echo "OK   $1"; else echo "FALLA $1"; fail=1; fi; }
run pair pair --ts $CLIP --pair
SAS_RX=$(grep '^SAS' $D/pair.rx | cut -d' ' -f2); SAS_TX=$(grep '^SAS' $D/pair.tx | cut -d' ' -f2)
chk "emparejamiento: mismo código en ambos lados ($SAS_RX)" "[ -n \"$SAS_RX\" ] && [ \"$SAS_RX\" = \"$SAS_TX\" ]"
chk "emparejamiento: RESULT rc=1 (BYE)" "grep -q 'RESULT rc=1' $D/pair.rx"
chk "emparejamiento: el TS recibido es idéntico al enviado" "cmp -s $CLIP $D/pair.ts"
chk "ACCEPT limita a 30 fps y 8 Mbps" "grep -q 'ACCEPT 1 8000000 30 250' $D/pair.tx"
chk "STATS llegan al emisor" "grep -q '^STATS (120, 2, 1)' $D/pair.tx"
chk "PONG responde al PING" "grep -q '^PONG' $D/pair.tx"
run resume resume --ts $CLIP
chk "reconexión con secreto guardado: rc=1" "grep -q 'RESULT rc=1' $D/resume.rx && grep -q 'STATUS Autenticado' $D/resume.rx"
chk "reconexión: TS idéntico" "cmp -s $CLIP $D/resume.ts"
run tamper resume --ts $CLIP --tamper 5
chk "trama alterada: el receptor corta (rc=-3)" "grep -q 'RESULT rc=-3' $D/tamper.rx"
run badpsk resume --ts $CLIP --badpsk
chk "secreto equivocado: autenticación rechazada (rc=-3) y sin datos" "grep -q 'RESULT rc=-3 ts_bytes=0' $D/badpsk.rx"
rm -f $D/secrets; run unknown resume --ts $CLIP
chk "emisor no emparejado: rc=-7" "grep -q 'RESULT rc=-7' $D/unknown.rx"
# Réplica del emisor Android (Java/JCA) y lecturas TCP parciales (opcional: requiere java)
if command -v java >/dev/null 2>&1; then
  J="$(dirname "$0")/../tools/sender-java"
  runj() { name=$1; mode=$2; shift 2; port=$((9600 + RANDOM % 300)); rm -f $D/j.secrets
    $H $port $D/j.secrets $mode $D/$name.ts > $D/$name.rx 2>&1 & sleep 0.5; (cd $J && "$@" java KotlinParitySender.java 127.0.0.1 $port ${ARGS} > $D/$name.tx 2>&1); wait; }
  ARGS="pair $D/j.psk" runj kt pair env
  chk "réplica Android: emparejamiento, ACCEPT, STATS y PONG" "grep -q PAIRED $D/kt.tx && grep -q 'DONE pong=true stats=true' $D/kt.tx && grep -q 'RESULT rc=1' $D/kt.rx"
  ARGS="pair $D/j.psk" DRIBBLE=1 runj kt1 pair env DRIBBLE=1
  chk "réplica Android enviando 1 byte por vez (lecturas parciales): rc=1" "grep -q 'RESULT rc=1' $D/kt1.rx"
  ARGS="unknown" runj ktu resume env
  chk "teléfono que no conoce a la PS3 (ERROR NOT_PAIRED): rc=-7 y no -5" "grep -q 'RESULT rc=-7' $D/ktu.rx"
fi
echo; [ $fail = 0 ] && echo "TODAS LAS PRUEBAS DE LINK PASARON" || { echo "HAY FALLAS"; for f in $D/*.rx $D/*.tx; do echo "--- $f"; cat $f; done; }
rm -rf $D; exit $fail
