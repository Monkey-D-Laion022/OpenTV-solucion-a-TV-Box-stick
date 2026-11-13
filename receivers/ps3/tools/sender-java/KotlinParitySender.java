// Réplica 1:1 del emisor Android (OtHandshake.asSender + OtCrypto + OtFrameCipher + OtWire) con las MISMAS primitivas JCA.
// Sirve para comprobar el receptor en C contra el comportamiento real del teléfono sin necesitar Android.
// Uso: java KotlinParitySender.java HOST PUERTO [pair|resume] [PSK_HEX_ARCHIVO]
import java.io.*; import java.math.BigInteger; import java.net.*; import java.nio.ByteBuffer; import java.security.*;
import java.security.interfaces.ECPublicKey; import java.security.spec.*; import javax.crypto.*; import javax.crypto.spec.*;

public class KotlinParitySender {
    static SecureRandom rnd = new SecureRandom();
    static byte[] cat(byte[]... p) { ByteArrayOutputStream o = new ByteArrayOutputStream(); for (byte[] b : p) o.writeBytes(b); return o.toByteArray(); }
    static byte[] hmac(byte[] k, byte[]... parts) throws Exception { Mac m = Mac.getInstance("HmacSHA256"); m.init(new SecretKeySpec(k, "HmacSHA256")); for (byte[] p : parts) m.update(p); return m.doFinal(); }
    static byte[] sha256(byte[]... parts) throws Exception { MessageDigest d = MessageDigest.getInstance("SHA-256"); for (byte[] p : parts) d.update(p); return d.digest(); }
    static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int len) throws Exception {
        byte[] prk = hmac(salt.length == 0 ? new byte[32] : salt, ikm); ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] t = new byte[0]; int c = 1;
        while (out.size() < len) { t = hmac(prk, t, info, new byte[]{(byte) c}); out.writeBytes(t); c++; } return java.util.Arrays.copyOf(out.toByteArray(), len);
    }
    static byte[] fixed32(BigInteger v) { byte[] raw = v.toByteArray(), out = new byte[32]; byte[] src = raw.length > 32 ? java.util.Arrays.copyOfRange(raw, raw.length - 32, raw.length) : raw; System.arraycopy(src, 0, out, 32 - src.length, src.length); return out; }
    static class Cipher2 { final SecretKeySpec k; final int dir; long ctr = 0; Cipher2(byte[] key, int d) { k = new SecretKeySpec(key, "AES"); dir = d; }
        byte[] nonce() { byte[] n = new byte[12]; n[0] = (byte) dir; long c = ctr++; for (int i = 11; i >= 4; i--) { n[i] = (byte) (c & 0xFF); c >>>= 8; } return n; } }
    static byte[] header(int type, int flags, int len) { return ByteBuffer.allocate(6).put((byte) type).put((byte) flags).putInt(len).array(); }
    static DataInputStream in; static OutputStream out;
    static final boolean DRIBBLE = System.getenv("DRIBBLE") != null; // 1 byte por envío: prueba lecturas TCP parciales en el receptor
    static void writeFrame(int type, int flags, byte[] payload) throws Exception {
        byte[] all = cat(header(type, flags, payload.length), payload);
        if (DRIBBLE) { for (byte b : all) { out.write(b); out.flush(); Thread.sleep(1); } } else { out.write(all); out.flush(); }
    }
    static byte[][] readFrame() throws Exception { byte[] h = new byte[6]; in.readFully(h); ByteBuffer bb = ByteBuffer.wrap(h); int type = bb.get() & 0xFF; int flags = bb.get() & 0xFF; int len = bb.getInt(); byte[] p = new byte[len]; in.readFully(p); return new byte[][]{{(byte) type}, {(byte) flags}, p}; }
    static void seal(Cipher2 c, int type, int flags, byte[] payload) throws Exception {
        javax.crypto.Cipher ci = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding"); ci.init(javax.crypto.Cipher.ENCRYPT_MODE, c.k, new GCMParameterSpec(128, c.nonce()));
        ci.updateAAD(header(type, flags, payload.length + 16)); writeFrame(type, flags, ci.doFinal(payload));
    }
    static byte[][] open(Cipher2 c) throws Exception {
        byte[][] f = readFrame(); javax.crypto.Cipher ci = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding"); ci.init(javax.crypto.Cipher.DECRYPT_MODE, c.k, new GCMParameterSpec(128, c.nonce()));
        ci.updateAAD(header(f[0][0] & 0xFF, f[1][0] & 0xFF, f[2].length)); f[2] = ci.doFinal(f[2]); return f;
    }
    public static void main(String[] a) throws Exception {
        String host = a[0]; int port = Integer.parseInt(a[1]); boolean pairMode = a.length < 3 || a[2].equals("pair") || a[2].equals("unknown");
        Socket s = new Socket(); s.connect(new InetSocketAddress(host, port), 5000); s.setSoTimeout(20000); s.setTcpNoDelay(true);
        in = new DataInputStream(s.getInputStream()); out = s.getOutputStream();
        byte[][] fr = readFrame(); if ((fr[0][0] & 0xFF) != 1) throw new RuntimeException("se esperaba HELLO, llegó tipo " + (fr[0][0] & 0xFF));
        byte[] rawR = fr[2]; ByteBuffer r = ByteBuffer.wrap(rawR);
        int ver = r.get() & 0xFF, role = r.get() & 0xFF; byte[] rid = new byte[8]; r.get(rid); int nl = r.get() & 0xFF; byte[] nm = new byte[nl]; r.get(nm); int caps = r.getShort() & 0xFFFF;
        byte[] nonceR = new byte[16]; r.get(nonceR); int mode = r.get() & 0xFF; byte[] pubR = new byte[r.get() & 0xFF]; r.get(pubR);
        System.out.println("HELLO_R ver=" + ver + " role=" + role + " name=" + new String(nm) + " caps=" + caps + " mode=" + mode + " publen=" + pubR.length);
        if (ver != 1 || role != 2) throw new RuntimeException("HELLO del receptor inválido");
        if (a.length > 2 && a[2].equals("unknown") && mode == 1) { // el receptor reconecta pero este teléfono no lo conoce: igual que OtHandshake.asSender (ERROR NOT_PAIRED)
            writeFrame(4, 0, cat(new byte[]{0, 1}, "Este equipo no está emparejado".getBytes("UTF-8"))); System.out.println("SENT_NOT_PAIRED"); s.close(); return; }
        boolean pair = mode == 0; if (pair != pairMode) throw new RuntimeException("modo del receptor distinto del pedido");
        KeyPair kp = null; if (pair) { KeyPairGenerator g = KeyPairGenerator.getInstance("EC"); g.initialize(new ECGenParameterSpec("secp256r1"), rnd); kp = g.generateKeyPair(); }
        byte[] myId = new byte[8]; rnd.nextBytes(myId); byte[] nonceS = new byte[16]; rnd.nextBytes(nonceS); byte[] name = "Redmi 14C".getBytes("UTF-8");
        byte[] pubS = new byte[0]; if (pair) { BigInteger[] w = {((ECPublicKey) kp.getPublic()).getW().getAffineX(), ((ECPublicKey) kp.getPublic()).getW().getAffineY()}; pubS = cat(new byte[]{4}, fixed32(w[0]), fixed32(w[1])); }
        ByteArrayOutputStream hb = new ByteArrayOutputStream(); DataOutputStream d = new DataOutputStream(hb);
        d.writeByte(1); d.writeByte(1); d.write(myId); d.writeByte(name.length); d.write(name); d.writeShort(0x0003); d.write(nonceS); d.writeByte(mode); d.writeByte(pubS.length); d.write(pubS);
        byte[] rawS = hb.toByteArray(); writeFrame(1, 0, rawS);
        byte[] ikm, z = null;
        if (pair) {
            AlgorithmParameters ap = AlgorithmParameters.getInstance("EC"); ap.init(new ECGenParameterSpec("secp256r1")); ECParameterSpec spec = ap.getParameterSpec(ECParameterSpec.class);
            BigInteger x = new BigInteger(1, java.util.Arrays.copyOfRange(pubR, 1, 33)), y = new BigInteger(1, java.util.Arrays.copyOfRange(pubR, 33, 65));
            PublicKey pk = KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(x, y), spec));
            KeyAgreement ka = KeyAgreement.getInstance("ECDH"); ka.init(kp.getPrivate()); ka.doPhase(pk, true); z = ka.generateSecret(); ikm = z;
        } else { ikm = hexFile(a[3]); }
        byte[] okm = hkdf(cat(nonceS, nonceR), ikm, "OTV1 session".getBytes(), 64);
        Cipher2 sealer = new Cipher2(java.util.Arrays.copyOfRange(okm, 0, 32), 1), opener = new Cipher2(java.util.Arrays.copyOfRange(okm, 32, 64), 2);
        if (pair) {
            byte[] mac = hmac(z, "OTV1 sas".getBytes(), rawS, rawR); long v = ((mac[0] & 0xFFL) << 24) | ((mac[1] & 0xFFL) << 16) | ((mac[2] & 0xFFL) << 8) | (mac[3] & 0xFFL);
            System.out.println("SAS " + String.format("%06d", v % 1_000_000L));
            seal(sealer, 3, 0, new byte[]{1}); byte[][] t = open(opener);
            if ((t[0][0] & 0xFF) != 3 || t[2].length != 1 || t[2][0] != 1) throw new RuntimeException("emparejamiento rechazado, tipo=" + (t[0][0] & 0xFF));
            byte[] psk = hkdf(sha256(rawS, rawR), z, "OTV1 psk".getBytes(), 32); try (FileWriter fw = new FileWriter(a.length > 3 ? a[3] : "/dev/null")) { StringBuilder sb = new StringBuilder(); for (byte b : psk) sb.append(String.format("%02x", b)); fw.write(sb.toString()); }
            System.out.println("PAIRED");
        } else {
            seal(sealer, 2, 0, hmac(ikm, "OTV1 auth".getBytes(), new byte[]{1}, rawS, rawR)); byte[][] t = open(opener);
            if ((t[0][0] & 0xFF) != 2 || !MessageDigest.isEqual(t[2], hmac(ikm, "OTV1 auth".getBytes(), new byte[]{2}, rawS, rawR))) throw new RuntimeException("autenticación fallida");
            System.out.println("AUTH_OK");
        }
        seal(sealer, 0x10, 0, ByteBuffer.allocate(14).put((byte) 1).putShort((short) 1920).putShort((short) 1080).put((byte) 60).putInt(8_000_000).put((byte) 1).put((byte) 1).array());
        byte[][] ac = open(opener); if ((ac[0][0] & 0xFF) != 0x11 || ac[2].length != 8) throw new RuntimeException("ACCEPT inválido");
        ByteBuffer ab = ByteBuffer.wrap(ac[2]); System.out.println("ACCEPT " + ab.get() + " " + (ab.getInt() & 0xFFFFFFFFL) + " " + (ab.get() & 0xFF) + " " + (ab.getShort() & 0xFFFF));
        byte[] ts = new byte[188 * 4]; for (int i = 0; i < 4; i++) ts[i * 188] = 0x47; seal(sealer, 0x20, 1, ts);
        seal(sealer, 0x30, 0, new byte[]{9, 9}); boolean pong = false, stats = false; s.setSoTimeout(4000);
        try { for (int i = 0; i < 6 && !(pong && stats); i++) { byte[][] f = open(opener); int t = f[0][0] & 0xFF; if (t == 0x31) { pong = true; System.out.println("PONG"); } if (t == 0x32) { stats = true; System.out.println("STATS len=" + f[2].length); } } } catch (SocketTimeoutException e) {}
        seal(sealer, 0x50, 0, new byte[0]); s.close(); System.out.println("DONE pong=" + pong + " stats=" + stats);
    }
    static byte[] hexFile(String p) throws Exception { String h = new String(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(p))).trim(); byte[] o = new byte[h.length() / 2]; for (int i = 0; i < o.length; i++) o[i] = (byte) Integer.parseInt(h.substring(2 * i, 2 * i + 2), 16); return o; }
}
