#!/usr/bin/env python3
"""
Receptor de referencia de OpenTV Link v1 (prototipo independiente, NO es parte de la app Android).

Sirve para comprobar el protocolo con otra implementación y para ver el canal en una PC:
    python3 opentv_receiver.py --host 192.168.1.20 --port 8765 --pair          # primera vez (compará el código)
    python3 opentv_receiver.py --host 192.168.1.20 --port 8765 | ffplay -i -   # después: reconecta solo y muestra el video

Requiere Python 3.9+ y el paquete `cryptography` (pip install cryptography). Especificación: docs/PROTOCOLO-OPENTV.md.
El video (MPEG-TS) sale por la salida estándar; los mensajes para el usuario salen por la salida de errores.
"""
import argparse, hashlib, hmac, json, os, socket, struct, sys, time

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.hkdf import HKDF

VERSION = 1
HELLO, AUTH, PAIR_CONFIRM, ERROR, OFFER, ACCEPT, TS_DATA, PING, PONG, STATS, CONTROL, BYE = (
    0x01, 0x02, 0x03, 0x04, 0x10, 0x11, 0x20, 0x30, 0x31, 0x32, 0x40, 0x50)
ALL_TYPES = {HELLO, AUTH, PAIR_CONFIRM, ERROR, OFFER, ACCEPT, TS_DATA, PING, PONG, STATS, CONTROL, BYE}
ROLE_SENDER, ROLE_RECEIVER = 1, 2
MODE_PAIR, MODE_RESUME = 0, 1
MAX_PAYLOAD = 4 * 1024 * 1024


def log(*a):
    print(*a, file=sys.stderr, flush=True)


def hkdf(salt, ikm, info, length):
    return HKDF(algorithm=hashes.SHA256(), length=length, salt=salt if salt else None, info=info).derive(ikm)


def recv_exact(sock, n):
    buf = bytearray()
    while len(buf) < n:
        chunk = sock.recv(n - len(buf))
        if not chunk:
            raise EOFError("conexión cerrada")
        buf += chunk
    return bytes(buf)


def read_frame(sock):
    head = recv_exact(sock, 6)
    ftype, flags, length = struct.unpack(">BBI", head)
    if ftype not in ALL_TYPES or length > MAX_PAYLOAD:
        raise ValueError("trama inválida")
    return ftype, flags, head, recv_exact(sock, length)


def write_frame(sock, ftype, flags, payload):
    sock.sendall(struct.pack(">BBI", ftype, flags, len(payload)) + payload)


class Cipher:
    def __init__(self, key, direction):
        self.aes, self.direction, self.counter = AESGCM(key), direction, 0

    def _nonce(self):
        n = bytes([self.direction, 0, 0, 0]) + struct.pack(">Q", self.counter)
        self.counter += 1
        return n

    def seal(self, ftype, flags, payload):
        header = struct.pack(">BBI", ftype, flags, len(payload) + 16)
        return self.aes.encrypt(self._nonce(), payload, header)

    def open(self, head, ct):
        return self.aes.decrypt(self._nonce(), ct, head)


def encode_hello(role, device_id, name, caps, nonce, mode, pub):
    nb = name.encode("utf-8")[:64]
    return (bytes([VERSION, role]) + device_id + bytes([len(nb)]) + nb + struct.pack(">H", caps) + nonce +
            bytes([mode, len(pub)]) + pub)


def decode_hello(data):
    p = 0
    def take(n):
        nonlocal p
        if p + n > len(data):
            raise ValueError("saludo truncado")
        out = data[p:p + n]; p += n; return out
    if take(1)[0] != VERSION:
        raise ValueError("versión no soportada")
    role = take(1)[0]
    device_id = take(8)
    name = take(take(1)[0]).decode("utf-8", "replace")
    caps = struct.unpack(">H", take(2))[0]
    nonce = take(16)
    mode = take(1)[0]
    pub = take(take(1)[0])
    return dict(role=role, id=device_id, name=name, caps=caps, nonce=nonce, mode=mode, pub=pub)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--host", required=True)
    ap.add_argument("--port", type=int, default=8765)
    ap.add_argument("--pair", action="store_true", help="emparejar (primera vez)")
    ap.add_argument("--yes", action="store_true", help="aceptar el código sin preguntar (solo para pruebas automáticas)")
    ap.add_argument("--store", default=os.path.expanduser("~/.opentv-receiver.json"))
    ap.add_argument("--name", default="Receptor Python")
    ap.add_argument("--max-frames", type=int, default=0, help="salir tras N tramas de video (0 = sin límite)")
    args = ap.parse_args()

    store = json.load(open(args.store)) if os.path.exists(args.store) else {"id": os.urandom(8).hex(), "peers": {}}
    my_id = bytes.fromhex(store["id"])
    sock = socket.create_connection((args.host, args.port), timeout=10)
    sock.settimeout(30)

    priv = ec.generate_private_key(ec.SECP256R1()) if args.pair else None
    pub = priv.public_key().public_bytes(serialization.Encoding.X962, serialization.PublicFormat.UncompressedPoint) if priv else b""
    nonce_r = os.urandom(16)
    raw_r = encode_hello(ROLE_RECEIVER, my_id, args.name, 0x0005, nonce_r, MODE_PAIR if args.pair else MODE_RESUME, pub)
    write_frame(sock, HELLO, 0, raw_r)
    ftype, _, _, raw_s = read_frame(sock)
    if ftype == ERROR:
        raise SystemExit("El emisor respondió con un error: " + raw_s[2:].decode("utf-8", "replace"))
    if ftype != HELLO:
        raise SystemExit("se esperaba HELLO")
    hs = decode_hello(raw_s)
    if hs["role"] != ROLE_SENDER or hs["mode"] != (MODE_PAIR if args.pair else MODE_RESUME):
        raise SystemExit("saludo del emisor inválido")

    if args.pair:
        peer_pub = ec.EllipticCurvePublicKey.from_encoded_point(ec.SECP256R1(), hs["pub"])  # valida que esté en la curva
        ikm = priv.exchange(ec.ECDH(), peer_pub)
    else:
        secret_hex = store["peers"].get(hs["id"].hex())
        if not secret_hex:
            raise SystemExit("Este emisor no está emparejado: ejecutá una vez con --pair")
        ikm = bytes.fromhex(secret_hex)

    okm = hkdf(hs["nonce"] + nonce_r, ikm, b"OTV1 session", 64)
    s2r, r2s = okm[:32], okm[32:]
    tx, rx = Cipher(r2s, 2), Cipher(s2r, 1)

    def send(ftype, flags=0, payload=b""):
        write_frame(sock, ftype, flags, tx.seal(ftype, flags, payload))

    def receive():
        ftype, flags, head, ct = read_frame(sock)
        return ftype, flags, rx.open(head, ct)

    if args.pair:
        mac = hmac.new(ikm, b"OTV1 sas" + raw_s + raw_r, hashlib.sha256).digest()
        sas = "%06d" % (int.from_bytes(mac[:4], "big") % 1_000_000)
        log("CÓDIGO DE EMPAREJAMIENTO:", sas, "(debe ser igual al que muestra el teléfono)")
        ok = True if args.yes else input("¿Coincide? [s/N] ").strip().lower() in ("s", "si", "sí", "y", "yes")
        send(PAIR_CONFIRM, 0, bytes([1 if ok else 0]))
        ftype, _, payload = receive()
        if not ok or ftype != PAIR_CONFIRM or payload != b"\x01":
            raise SystemExit("emparejamiento rechazado")
        psk = hkdf(hashlib.sha256(raw_s + raw_r).digest(), ikm, b"OTV1 psk", 32)
        store["peers"][hs["id"].hex()] = psk.hex()
        json.dump(store, open(args.store, "w"))
        os.chmod(args.store, 0o600)
        log("Emparejado con", hs["name"])
    else:
        def auth(role):
            return hmac.new(ikm, b"OTV1 auth" + bytes([role]) + raw_s + raw_r, hashlib.sha256).digest()
        send(AUTH, 0, auth(ROLE_RECEIVER))
        ftype, _, payload = receive()
        if ftype != AUTH or not hmac.compare_digest(payload, auth(ROLE_SENDER)):
            raise SystemExit("autenticación fallida")
        log("Autenticado con", hs["name"])

    frames = 0
    dropped = 0
    last_stats = time.time()
    out = sys.stdout.buffer
    while True:
        ftype, flags, payload = receive()
        if ftype == OFFER:
            send(ACCEPT, 0, bytes([1]) + payload[5:9] + bytes([payload[4]]) + struct.pack(">H", 250))
            log("Oferta recibida:", struct.unpack(">HH", payload[1:5]), "px")
        elif ftype == TS_DATA:
            if len(payload) % 188 != 0:
                dropped += 1
            else:
                out.write(payload); out.flush()
            frames += 1
            if args.max_frames and frames >= args.max_frames:
                send(BYE)
                break
        elif ftype == PING:
            send(PONG, 0, payload)
        elif ftype == BYE:
            break
        elif ftype == ERROR:
            raise SystemExit("error del emisor: " + payload[2:].decode("utf-8", "replace"))
        if time.time() - last_stats >= 1:
            send(STATS, 0, struct.pack(">HII", 0, dropped, 0))
            last_stats = time.time()
    log("Tramas de video recibidas:", frames)


if __name__ == "__main__":
    main()
