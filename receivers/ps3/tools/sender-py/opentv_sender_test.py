#!/usr/bin/env python3
"""Emisor de PRUEBA de OpenTV Link v1 (solo para verificar receptores en un PC; NO es la app Android).
Se conecta a un receptor que escucha, hace el emparejamiento/reconexión, envía una oferta y un archivo .ts, y verifica lo que el receptor responde.
  opentv_sender_test.py --port 9000 --ts clip.ts --store st.json [--pair] [--tamper N] [--badpsk]"""
import argparse, hashlib, hmac, json, os, socket, struct, sys, time
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.hkdf import HKDF
HELLO, AUTH, PAIR, ERROR, OFFER, ACCEPT, TS, PING, PONG, STATS, CONTROL, BYE = 1, 2, 3, 4, 0x10, 0x11, 0x20, 0x30, 0x31, 0x32, 0x40, 0x50
def hkdf(salt, ikm, info, n): return HKDF(algorithm=hashes.SHA256(), length=n, salt=salt, info=info).derive(ikm)
def rx_exact(s, n):
    b = b""
    while len(b) < n:
        c = s.recv(n - len(b))
        if not c: raise EOFError("cerrado")
        b += c
    return b
def rd(s):
    h = rx_exact(s, 6); t, f, l = struct.unpack(">BBI", h); return t, f, h, rx_exact(s, l)
class C:
    def __init__(s, key, d): s.a, s.d, s.n = AESGCM(key), d, 0
    def nonce(s): n = bytes([s.d, 0, 0, 0]) + struct.pack(">Q", s.n); s.n += 1; return n
def main():
    ap = argparse.ArgumentParser(); ap.add_argument("--port", type=int, required=True); ap.add_argument("--ts"); ap.add_argument("--store", required=True)
    ap.add_argument("--pair", action="store_true"); ap.add_argument("--tamper", type=int, default=0); ap.add_argument("--badpsk", action="store_true")
    ap.add_argument("--replay", action="store_true"); a = ap.parse_args()
    st = json.load(open(a.store)) if os.path.exists(a.store) else {"id": os.urandom(8).hex(), "peers": {}}
    sid = bytes.fromhex(st["id"]); s = socket.create_connection(("127.0.0.1", a.port), timeout=20)
    t, _, _, raw_r = rd(s); assert t == HELLO
    p = 2; rid = raw_r[p:p + 8]; p += 8; nl = raw_r[p]; p += 1 + nl; p += 2; nonce_r = raw_r[p:p + 16]; p += 16; mode = raw_r[p]; p += 1; pl = raw_r[p]; pub_r = raw_r[p + 1:p + 1 + pl]
    assert mode == (0 if a.pair else 1), "modo del receptor"
    nonce_s = os.urandom(16); priv = ec.generate_private_key(ec.SECP256R1()) if a.pair else None
    pub = priv.public_key().public_bytes(serialization.Encoding.X962, serialization.PublicFormat.UncompressedPoint) if a.pair else b""
    name = b"Telefono de prueba"
    raw_s = bytes([1, 1]) + sid + bytes([len(name)]) + name + struct.pack(">H", 5) + nonce_s + bytes([0 if a.pair else 1, len(pub)]) + pub
    s.sendall(struct.pack(">BBI", HELLO, 0, len(raw_s)) + raw_s)
    if a.pair: ikm = priv.exchange(ec.ECDH(), ec.EllipticCurvePublicKey.from_encoded_point(ec.SECP256R1(), pub_r))
    else:
        ikm = bytes.fromhex(st["peers"][rid.hex()]) if rid.hex() in st["peers"] else b"\0" * 32
        if a.badpsk: ikm = b"\x55" * 32
    okm = hkdf(nonce_s + nonce_r, ikm, b"OTV1 session", 64); tx, rxc = C(okm[:32], 1), C(okm[32:], 2)
    def send(t, f=0, pl=b""):
        h = struct.pack(">BBI", t, f, len(pl) + 16); s.sendall(h + tx.a.encrypt(tx.nonce(), pl, h))
    def recv():
        t, f, h, ct = rd(s); return t, f, rxc.a.decrypt(rxc.nonce(), ct, h)
    if a.pair:
        mac = hmac.new(ikm, b"OTV1 sas" + raw_s + raw_r, hashlib.sha256).digest(); print("SAS %06d" % (int.from_bytes(mac[:4], "big") % 1000000), flush=True)
        send(PAIR, 0, b"\x01"); t, _, pl = recv(); assert t == PAIR and pl == b"\x01", "confirmación"
        st["peers"][rid.hex()] = hkdf(hashlib.sha256(raw_s + raw_r).digest(), ikm, b"OTV1 psk", 32).hex(); json.dump(st, open(a.store, "w"))
    else:
        au = lambda role: hmac.new(ikm, b"OTV1 auth" + bytes([role]) + raw_s + raw_r, hashlib.sha256).digest()
        send(AUTH, 0, au(1))
        try: t, _, pl = recv()
        except Exception as e: print("AUTH_REJECTED", type(e).__name__); return 3
        assert t == AUTH and hmac.compare_digest(pl, au(2)), "autenticación del receptor"
    send(OFFER, 0, struct.pack(">BHHBIBB", 1, 1280, 720, 60, 12_000_000, 1, 1) + b"\0\0")  # Kotlin reserva 14 B (allocate(14))
    t, _, pl = recv(); assert t == ACCEPT, "recibido tipo %#x" % t; ok, br, fps, buf = struct.unpack(">BIBH", pl); print("ACCEPT", ok, br, fps, buf)
    data = open(a.ts, "rb").read() if a.ts else b""; CH = 188 * 7; sent = 0; n = 0; stats_seen = 0
    import select
    for off in range(0, len(data) - len(data) % 188, CH):
        blk = data[off:off + CH]; n += 1
        if a.tamper and n == a.tamper:
            h = struct.pack(">BBI", TS, 0, len(blk) + 16); ct = bytearray(tx.a.encrypt(tx.nonce(), blk, h)); ct[5] ^= 1; s.sendall(h + bytes(ct)); print("TAMPERED"); break
        send(TS, 1 if n == 1 else 0, blk); sent += len(blk)
        if a.replay and n == 3: pass
        try:
            while select.select([s], [], [], 0)[0]:
                t, _, pl = recv()
                if t == STATS: stats_seen += 1; print("STATS", struct.unpack(">HII", pl), flush=True)
        except EOFError: print("CLOSED_BY_RECEIVER"); return 4
    time.sleep(1.3); send(PING, 0, b"12345678")
    try:
        for _ in range(5):
            t, _, pl = recv()
            if t == PONG: print("PONG", pl); break
            if t == STATS: print("STATS", struct.unpack(">HII", pl), flush=True)
    except Exception as e: print("NO_PONG", type(e).__name__)
    send(BYE); print("SENT", sent); return 0
sys.exit(main())
