package com.opentv.app.proto

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.LinkedBlockingQueue

/** Dónde se guardan los secretos de los equipos ya emparejados (en la app: almacenamiento privado cifrado). */
interface OtPeerStore {
    fun secretFor(deviceId: ByteArray): ByteArray?
    fun save(deviceId: ByteArray, name: String, secret: ByteArray)
}

/** Memoria volátil: sirve para pruebas y para sesiones sin emparejamiento persistente. */
class OtMemoryPeerStore : OtPeerStore {
    private val map = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()
    private fun key(id: ByteArray) = id.joinToString("") { "%02x".format(it) }
    override fun secretFor(deviceId: ByteArray): ByteArray? = map[key(deviceId)]
    override fun save(deviceId: ByteArray, name: String, secret: ByteArray) { map[key(deviceId)] = secret.copyOf() }
    fun forget(deviceId: ByteArray) { map.remove(key(deviceId)) }
}

class OtIdentity(val deviceId: ByteArray, val name: String, val caps: Int) {
    init { require(deviceId.size == 8) }
}

/** Tramas sobre un par de flujos (en la app, un socket TCP). Escritura sincronizada; la lectura la hace un solo hilo. */
class OtChannel(private val input: InputStream, private val output: OutputStream) {
    private val reader = OtFrameReader()
    private val pending = LinkedBlockingQueue<OtFrame>()
    private val buf = ByteArray(32 * 1024)

    @Synchronized
    fun write(frame: OtFrame) {
        output.write(OtFrameCodec.encode(frame))
        output.flush()
    }

    /** Siguiente trama, o `null` si el otro lado cerró limpiamente entre tramas. */
    fun read(): OtFrame? {
        while (true) {
            pending.poll()?.let { return it }
            val n = input.read(buf)
            if (n < 0) {
                if (reader.pendingBytes > 0) throw EOFException("La conexión se cortó a mitad de una trama")
                return null
            }
            reader.feed(buf, 0, n).forEach { pending.add(it) }
        }
    }
}

/** Sesión ya autenticada y cifrada. `send` es seguro entre hilos; `receive` debe usarlo un solo hilo. */
class OtSecureSession(
    private val channel: OtChannel,
    private val sealer: OtFrameCipher,
    private val opener: OtFrameCipher,
    val peer: OtHello,
    val justPaired: Boolean,
) {
    @Synchronized
    fun send(type: Int, flags: Int = 0, payload: ByteArray = ByteArray(0)) {
        channel.write(sealer.seal(OtFrame(type, flags, payload)))
    }

    /** Siguiente trama descifrada, o `null` al cerrarse. Lanza [OtProtocolException] si algo no autentica. */
    fun receive(): OtFrame? = channel.read()?.let { opener.open(it) }

    fun sendError(code: Int, message: String) = runCatching { send(OtWire.Type.ERROR, 0, OtError(code, message).encode()) }
}

/** Saludo, emparejamiento y reconexión. Bloquea el hilo que lo llama; ejecutalo fuera del hilo principal. */
object OtHandshake {

    /** Lado emisor (el teléfono que transmite; escucha en TCP). [confirmSas] solo se usa al emparejar. */
    fun asSender(channel: OtChannel, me: OtIdentity, store: OtPeerStore, confirmSas: (String) -> Boolean): OtSecureSession {
        val rawR = channel.read()?.takeIf { it.type == OtWire.Type.HELLO }?.payload ?: throw OtProtocolException("se esperaba HELLO")
        val helloR = OtHello.decode(rawR)
        if (helloR.role != OtWire.ROLE_RECEIVER) throw OtProtocolException("el otro lado no es un receptor")
        val psk = if (helloR.mode == OtWire.MODE_RESUME) store.secretFor(helloR.deviceId) else null
        if (helloR.mode == OtWire.MODE_RESUME && psk == null) {
            channel.write(OtFrame(OtWire.Type.ERROR, 0, OtError(OtWire.ErrorCode.NOT_PAIRED, "Este equipo no está emparejado").encode()))
            throw OtProtocolException("receptor no emparejado")
        }
        val pair = helloR.mode == OtWire.MODE_PAIR
        val keys = if (pair) OtCrypto.generateKeyPair() else null
        val helloS = OtHello(
            OtWire.ROLE_SENDER, me.deviceId, me.name, me.caps, OtCrypto.randomBytes(16), helloR.mode,
            keys?.let { OtCrypto.encodePublic(it.public) } ?: ByteArray(0),
        )
        val rawS = helloS.encode()
        channel.write(OtFrame(OtWire.Type.HELLO, 0, rawS))
        return finish(channel, OtWire.ROLE_SENDER, helloR, helloS, rawS, rawR, keys, psk, store, confirmSas, rawS, rawR)
    }

    /** Lado receptor (se conecta al emisor). [pair] = true para emparejar por primera vez; false para reconectar. */
    fun asReceiver(
        channel: OtChannel, me: OtIdentity, store: OtPeerStore, pair: Boolean, confirmSas: (String) -> Boolean,
    ): OtSecureSession {
        val keys = if (pair) OtCrypto.generateKeyPair() else null
        val helloR = OtHello(
            OtWire.ROLE_RECEIVER, me.deviceId, me.name, me.caps, OtCrypto.randomBytes(16),
            if (pair) OtWire.MODE_PAIR else OtWire.MODE_RESUME, keys?.let { OtCrypto.encodePublic(it.public) } ?: ByteArray(0),
        )
        val rawR = helloR.encode()
        channel.write(OtFrame(OtWire.Type.HELLO, 0, rawR))
        val reply = channel.read() ?: throw EOFException("El emisor cerró la conexión")
        if (reply.type == OtWire.Type.ERROR) {
            val e = runCatching { OtError.decode(reply.payload) }.getOrNull()
            throw OtProtocolException("El emisor respondió: ${e?.message ?: "error"}")
        }
        if (reply.type != OtWire.Type.HELLO) throw OtProtocolException("se esperaba HELLO")
        val helloS = OtHello.decode(reply.payload)
        if (helloS.role != OtWire.ROLE_SENDER || helloS.mode != helloR.mode) throw OtProtocolException("saludo del emisor inválido")
        val psk = if (pair) null else store.secretFor(helloS.deviceId) ?: throw OtProtocolException("emisor no emparejado")
        return finish(channel, OtWire.ROLE_RECEIVER, helloS, helloR, reply.payload, rawR, keys, psk, store, confirmSas, reply.payload, rawR)
    }

    @Suppress("LongParameterList")
    private fun finish(
        channel: OtChannel, myRole: Int, peerHello: OtHello, myHello: OtHello, rawMyOrSender: ByteArray, rawOther: ByteArray,
        keys: java.security.KeyPair?, psk: ByteArray?, store: OtPeerStore, confirmSas: (String) -> Boolean,
        rawSender: ByteArray, rawReceiver: ByteArray,
    ): OtSecureSession {
        val ikm: ByteArray
        val pairing = keys != null
        val z: ByteArray?
        if (pairing) {
            z = OtCrypto.agree(keys!!.private, OtCrypto.decodePublic(peerHello.publicKey))
            ikm = z
        } else {
            z = null
            ikm = psk!!
        }
        val nonceS = if (myRole == OtWire.ROLE_SENDER) myHello.nonce else peerHello.nonce
        val nonceR = if (myRole == OtWire.ROLE_RECEIVER) myHello.nonce else peerHello.nonce
        val sk = OtCrypto.sessionKeys(ikm, nonceS, nonceR)
        val (sendKey, recvKey, sendDir, recvDir) =
            if (myRole == OtWire.ROLE_SENDER) {
                Quad(sk.senderToReceiver, sk.receiverToSender, OtFrameCipher.DIR_SENDER_TO_RECEIVER, OtFrameCipher.DIR_RECEIVER_TO_SENDER)
            } else {
                Quad(sk.receiverToSender, sk.senderToReceiver, OtFrameCipher.DIR_RECEIVER_TO_SENDER, OtFrameCipher.DIR_SENDER_TO_RECEIVER)
            }
        val sealer = OtFrameCipher(sendKey, sendDir)
        val opener = OtFrameCipher(recvKey, recvDir)
        val session = OtSecureSession(channel, sealer, opener, peerHello, pairing)

        if (pairing) {
            val sas = OtCrypto.sas(z!!, rawSender, rawReceiver)
            val ok = confirmSas(sas)
            session.send(OtWire.Type.PAIR_CONFIRM, 0, byteArrayOf(if (ok) 1 else 0))
            val theirs = session.receive() ?: throw EOFException("El otro equipo cerró durante el emparejamiento")
            if (theirs.type == OtWire.Type.ERROR) throw OtProtocolException("El otro equipo rechazó la conexión")
            if (theirs.type != OtWire.Type.PAIR_CONFIRM || theirs.payload.size != 1) throw OtProtocolException("se esperaba PAIR_CONFIRM")
            if (!ok || theirs.payload[0].toInt() != 1) throw OtProtocolException("emparejamiento rechazado")
            store.save(peerHello.deviceId, peerHello.name, OtCrypto.pairingSecret(z, rawSender, rawReceiver))
        } else {
            val mine = OtCrypto.authMac(psk!!, myRole, rawSender, rawReceiver)
            session.send(OtWire.Type.AUTH, 0, mine)
            val theirs = session.receive() ?: throw EOFException("El otro equipo cerró durante la autenticación")
            val peerRole = if (myRole == OtWire.ROLE_SENDER) OtWire.ROLE_RECEIVER else OtWire.ROLE_SENDER
            if (theirs.type != OtWire.Type.AUTH ||
                !OtCrypto.constantTimeEquals(theirs.payload, OtCrypto.authMac(psk, peerRole, rawSender, rawReceiver))
            ) throw OtProtocolException("autenticación fallida")
        }
        return session
    }

    private data class Quad(val a: ByteArray, val b: ByteArray, val c: Int, val d: Int)
}
