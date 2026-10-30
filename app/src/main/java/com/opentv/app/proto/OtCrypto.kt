package com.opentv.app.proto

import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Criptografía de OpenTV Link v1, solo con las primitivas estándar de la plataforma (JCA): ECDH P-256, HKDF-SHA256,
 * HMAC-SHA256 y AES-256-GCM. No hay criptografía inventada: lo propio es cómo se combinan (ver el documento del protocolo).
 */
object OtCrypto {
    private val random = SecureRandom()

    fun randomBytes(n: Int): ByteArray = ByteArray(n).also { random.nextBytes(it) }

    fun hmac(key: ByteArray, vararg parts: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        for (p in parts) mac.update(p)
        return mac.doFinal()
    }

    fun sha256(vararg parts: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        for (p in parts) md.update(p)
        return md.digest()
    }

    /** HKDF (RFC 5869) con SHA-256. */
    fun hkdf(salt: ByteArray, ikm: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length in 1..(255 * 32))
        val prk = hmac(if (salt.isEmpty()) ByteArray(32) else salt, ikm)
        val out = java.io.ByteArrayOutputStream()
        var t = ByteArray(0)
        var counter = 1
        while (out.size() < length) {
            t = hmac(prk, t, info, byteArrayOf(counter.toByte()))
            out.write(t)
            counter++
        }
        return out.toByteArray().copyOf(length)
    }

    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)

    // --- Emparejamiento: ECDH efímero + comparación de código ----------------------------------

    private val curve: ECParameterSpec by lazy {
        val p = AlgorithmParameters.getInstance("EC")
        p.init(ECGenParameterSpec("secp256r1"))
        p.getParameterSpec(ECParameterSpec::class.java)
    }

    fun generateKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1"), random) }.generateKeyPair()

    /** Punto sin comprimir X9.62: `04 || X(32) || Y(32)`. */
    fun encodePublic(key: PublicKey): ByteArray {
        val w = (key as java.security.interfaces.ECPublicKey).w
        return byteArrayOf(4) + fixed32(w.affineX) + fixed32(w.affineY)
    }

    fun decodePublic(bytes: ByteArray): PublicKey {
        if (bytes.size != 65 || bytes[0].toInt() != 4) throw OtProtocolException("clave pública inválida")
        val x = BigInteger(1, bytes.copyOfRange(1, 33))
        val y = BigInteger(1, bytes.copyOfRange(33, 65))
        // JCA no siempre valida que el punto esté en la curva (ataque de curva inválida): se comprueba a mano.
        val field = (curve.curve.field as java.security.spec.ECFieldFp).p
        if (x >= field || y >= field) throw OtProtocolException("la clave pública no está en la curva")
        val lhs = y.multiply(y).mod(field)
        val rhs = x.multiply(x).multiply(x).add(curve.curve.a.multiply(x)).add(curve.curve.b).mod(field)
        if (lhs != rhs) throw OtProtocolException("la clave pública no está en la curva")
        try {
            return KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(ECPoint(x, y), curve))
        } catch (e: java.security.GeneralSecurityException) {
            throw OtProtocolException("la clave pública no está en la curva")
        }
    }

    private fun fixed32(v: BigInteger): ByteArray {
        val raw = v.toByteArray()
        val out = ByteArray(32)
        val src = if (raw.size > 32) raw.copyOfRange(raw.size - 32, raw.size) else raw
        System.arraycopy(src, 0, out, 32 - src.size, src.size)
        return out
    }

    fun agree(privateKey: PrivateKey, peer: PublicKey): ByteArray =
        KeyAgreement.getInstance("ECDH").apply { init(privateKey); doPhase(peer, true) }.generateSecret()

    private val SAS_LABEL = "OTV1 sas".toByteArray()
    private val PSK_LABEL = "OTV1 psk".toByteArray()
    private val SESSION_LABEL = "OTV1 session".toByteArray()
    private val AUTH_LABEL = "OTV1 auth".toByteArray()

    /** Código de 6 dígitos que ambos equipos muestran y el usuario compara. Depende de los dos saludos completos. */
    fun sas(sharedSecret: ByteArray, helloSender: ByteArray, helloReceiver: ByteArray): String {
        val mac = hmac(sharedSecret, SAS_LABEL, helloSender, helloReceiver)
        val v = ((mac[0].toLong() and 0xFF) shl 24) or ((mac[1].toLong() and 0xFF) shl 16) or ((mac[2].toLong() and 0xFF) shl 8) or (mac[3].toLong() and 0xFF)
        return String.format(java.util.Locale.US, "%06d", v % 1_000_000L)
    }

    /** Secreto compartido que se guarda al emparejar y permite reconectar sin repetir la comparación. */
    fun pairingSecret(sharedSecret: ByteArray, helloSender: ByteArray, helloReceiver: ByteArray): ByteArray =
        hkdf(sha256(helloSender, helloReceiver), sharedSecret, PSK_LABEL, 32)

    class SessionKeys(val senderToReceiver: ByteArray, val receiverToSender: ByteArray)

    /** Claves de la sesión: de [ikm] (secreto ECDH al emparejar, secreto guardado al reconectar) y los dos nonces. */
    fun sessionKeys(ikm: ByteArray, nonceSender: ByteArray, nonceReceiver: ByteArray): SessionKeys {
        val okm = hkdf(nonceSender + nonceReceiver, ikm, SESSION_LABEL, 64)
        return SessionKeys(okm.copyOfRange(0, 32), okm.copyOfRange(32, 64))
    }

    /** Prueba de que quien reconecta conoce el secreto guardado, atada a esta sesión (nonces) y a este rol. */
    fun authMac(psk: ByteArray, role: Int, helloSender: ByteArray, helloReceiver: ByteArray): ByteArray =
        hmac(psk, AUTH_LABEL, byteArrayOf(role.toByte()), helloSender, helloReceiver)
}

/**
 * Cifra y descifra las cargas de una sesión con AES-256-GCM. El nonce de 12 bytes no viaja: es `dirección (1 byte) ·
 * 000 · contador u64`, y el contador sube con cada trama (TCP no pierde ni reordena, así que ambos lados coinciden).
 * La cabecera de la trama es el AAD: cambiar tipo, flags o largo hace fallar la autenticación.
 */
class OtFrameCipher(key: ByteArray, private val direction: Int) {
    private val keySpec = SecretKeySpec(key, "AES")
    private var counter = 0L

    private fun nextNonce(): ByteArray {
        val n = ByteArray(12)
        n[0] = direction.toByte()
        var c = counter++
        for (i in 11 downTo 4) { n[i] = (c and 0xFF).toByte(); c = c ushr 8 }
        return n
    }

    fun seal(frame: OtFrame): OtFrame {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(128, nextNonce()))
        val header = OtFrameCodec.header(frame.type, frame.flags, frame.payload.size + TAG)
        cipher.updateAAD(header)
        return OtFrame(frame.type, frame.flags, cipher.doFinal(frame.payload))
    }

    fun open(frame: OtFrame): OtFrame {
        if (frame.payload.size < TAG) throw OtProtocolException("trama cifrada demasiado corta")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(128, nextNonce()))
        cipher.updateAAD(OtFrameCodec.header(frame.type, frame.flags, frame.payload.size))
        try {
            return OtFrame(frame.type, frame.flags, cipher.doFinal(frame.payload))
        } catch (e: java.security.GeneralSecurityException) {
            throw OtProtocolException("la trama no pasó la autenticación")
        }
    }

    companion object {
        const val TAG = 16
        const val DIR_SENDER_TO_RECEIVER = 1
        const val DIR_RECEIVER_TO_SENDER = 2
    }
}
