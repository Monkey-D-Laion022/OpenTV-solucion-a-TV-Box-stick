package com.opentv.app.proto

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer

/** Error de formato o de seguridad del protocolo OpenTV Link: el que lo recibe corta la conexión. */
class OtProtocolException(message: String) : Exception(message)

/**
 * OpenTV Link v1 — formato de trama. Especificación completa en `docs/PROTOCOLO-OPENTV.md`.
 *
 * Trama: `tipo u8 · flags u8 · largo u32 (big-endian) · carga` (6 bytes de cabecera). Después del saludo la carga va
 * cifrada con AES-256-GCM y la cabecera es el dato autenticado adicional (AAD). Lógica pura, sin Android.
 */
object OtWire {
    const val VERSION = 1
    const val HEADER_SIZE = 6
    const val MAX_PAYLOAD = 4 * 1024 * 1024

    const val ROLE_SENDER = 1
    const val ROLE_RECEIVER = 2

    const val MODE_PAIR = 0
    const val MODE_RESUME = 1

    object Type {
        const val HELLO = 0x01
        const val AUTH = 0x02
        const val PAIR_CONFIRM = 0x03
        const val ERROR = 0x04
        const val OFFER = 0x10
        const val ACCEPT = 0x11
        const val TS_DATA = 0x20
        const val PING = 0x30
        const val PONG = 0x31
        const val STATS = 0x32
        const val CONTROL = 0x40
        const val BYE = 0x50

        val ALL = setOf(HELLO, AUTH, PAIR_CONFIRM, ERROR, OFFER, ACCEPT, TS_DATA, PING, PONG, STATS, CONTROL, BYE)
    }

    /** Flags de `TS_DATA`. */
    const val FLAG_KEY_START = 0x01

    object ErrorCode {
        const val NOT_PAIRED = 1
        const val AUTH_FAILED = 2
        const val REJECTED = 3
        const val UNSUPPORTED = 4
        const val BUSY = 5
    }

    object Control {
        const val PAUSE = 1
        const val RESUME = 2
        const val STOP = 3
        const val REQUEST_KEYFRAME = 4
        const val SET_BITRATE = 5
    }
}

class OtFrame(val type: Int, val flags: Int, val payload: ByteArray) {
    init {
        require(type in 0..255 && flags in 0..255) { "tipo/flags fuera de rango" }
    }
}

object OtFrameCodec {
    fun header(type: Int, flags: Int, length: Int): ByteArray {
        require(length in 0..OtWire.MAX_PAYLOAD) { "largo fuera de rango: $length" }
        return ByteBuffer.allocate(OtWire.HEADER_SIZE).put(type.toByte()).put(flags.toByte()).putInt(length).array()
    }

    fun encode(frame: OtFrame): ByteArray = header(frame.type, frame.flags, frame.payload.size) + frame.payload
}

/** Arma tramas a partir de bytes que llegan en pedazos arbitrarios (TCP no respeta los límites). */
class OtFrameReader {
    private var buffer = ByteArray(0)

    /** Agrega bytes y devuelve las tramas completas. Lanza [OtProtocolException] si el largo o el tipo son inválidos. */
    fun feed(data: ByteArray, offset: Int = 0, length: Int = data.size): List<OtFrame> {
        buffer += data.copyOfRange(offset, offset + length)
        val out = ArrayList<OtFrame>()
        while (buffer.size >= OtWire.HEADER_SIZE) {
            val bb = ByteBuffer.wrap(buffer)
            val type = bb.get().toInt() and 0xFF
            val flags = bb.get().toInt() and 0xFF
            val len = bb.int
            if (type !in OtWire.Type.ALL) throw OtProtocolException("tipo de trama desconocido: $type")
            if (len < 0 || len > OtWire.MAX_PAYLOAD) throw OtProtocolException("largo de trama inválido: $len")
            if (buffer.size < OtWire.HEADER_SIZE + len) break
            out.add(OtFrame(type, flags, buffer.copyOfRange(OtWire.HEADER_SIZE, OtWire.HEADER_SIZE + len)))
            buffer = buffer.copyOfRange(OtWire.HEADER_SIZE + len, buffer.size)
        }
        return out
    }

    val pendingBytes: Int get() = buffer.size
}

// --- Mensajes ---------------------------------------------------------------------------------

data class OtHello(
    val role: Int,
    val deviceId: ByteArray,
    val name: String,
    val caps: Int,
    val nonce: ByteArray,
    val mode: Int,
    /** Clave pública efímera P-256 (65 bytes, X9.62 sin comprimir); vacía en modo reconexión. */
    val publicKey: ByteArray,
) {
    init {
        require(deviceId.size == 8) { "deviceId debe medir 8 bytes" }
        require(nonce.size == 16) { "nonce debe medir 16 bytes" }
    }

    fun encode(): ByteArray {
        val nameBytes = name.toByteArray(Charsets.UTF_8).let { if (it.size > 64) it.copyOf(64) else it }
        val out = ByteArrayOutputStream()
        DataOutputStream(out).apply {
            writeByte(OtWire.VERSION); writeByte(role); write(deviceId)
            writeByte(nameBytes.size); write(nameBytes)
            writeShort(caps); write(nonce); writeByte(mode)
            writeByte(publicKey.size); write(publicKey)
        }
        return out.toByteArray()
    }

    companion object {
        fun decode(data: ByteArray): OtHello {
            val r = Reader(data)
            val version = r.u8()
            if (version != OtWire.VERSION) throw OtProtocolException("versión no soportada: $version")
            val role = r.u8()
            if (role != OtWire.ROLE_SENDER && role != OtWire.ROLE_RECEIVER) throw OtProtocolException("rol inválido: $role")
            val id = r.bytes(8)
            val name = String(r.bytes(r.u8()), Charsets.UTF_8)
            val caps = r.u16()
            val nonce = r.bytes(16)
            val mode = r.u8()
            if (mode != OtWire.MODE_PAIR && mode != OtWire.MODE_RESUME) throw OtProtocolException("modo inválido: $mode")
            val pub = r.bytes(r.u8())
            if (mode == OtWire.MODE_PAIR && pub.size != 65) throw OtProtocolException("falta la clave pública del emparejamiento")
            return OtHello(role, id, name, caps, nonce, mode, pub)
        }
    }

    override fun equals(other: Any?) = other is OtHello && encode().contentEquals(other.encode())
    override fun hashCode() = encode().contentHashCode()
}

/** Capacidades anunciadas en el saludo (bits). */
object OtCaps {
    const val H264 = 0x0001
    const val AAC = 0x0002
    const val MPEG_TS = 0x0004
    const val CONTROL = 0x0008
}

data class OtOffer(
    val videoCodec: Int, // 1 = H.264
    val width: Int,
    val height: Int,
    val fps: Int,
    val bitrateBps: Long,
    val audioCodec: Int, // 0 = sin audio, 1 = AAC-LC
    val container: Int, // 1 = MPEG-TS
) {
    fun encode(): ByteArray = ByteBuffer.allocate(14)
        .put(videoCodec.toByte()).putShort(width.toShort()).putShort(height.toShort()).put(fps.toByte())
        .putInt(bitrateBps.toInt()).put(audioCodec.toByte()).put(container.toByte()).array()

    companion object {
        fun decode(data: ByteArray): OtOffer {
            if (data.size != 14) throw OtProtocolException("oferta con largo inválido: ${data.size}")
            val bb = ByteBuffer.wrap(data)
            return OtOffer(
                bb.get().toInt() and 0xFF, bb.short.toInt() and 0xFFFF, bb.short.toInt() and 0xFFFF, bb.get().toInt() and 0xFF,
                bb.int.toLong() and 0xFFFFFFFFL, bb.get().toInt() and 0xFF, bb.get().toInt() and 0xFF,
            )
        }
    }
}

data class OtAccept(val accepted: Boolean, val bitrateBps: Long, val fps: Int, val bufferTargetMs: Int) {
    fun encode(): ByteArray = ByteBuffer.allocate(8)
        .put(if (accepted) 1 else 0).putInt(bitrateBps.toInt()).put(fps.toByte()).putShort(bufferTargetMs.toShort()).array()

    companion object {
        fun decode(data: ByteArray): OtAccept {
            if (data.size != 8) throw OtProtocolException("aceptación con largo inválido: ${data.size}")
            val bb = ByteBuffer.wrap(data)
            return OtAccept(bb.get().toInt() == 1, bb.int.toLong() and 0xFFFFFFFFL, bb.get().toInt() and 0xFF, bb.short.toInt() and 0xFFFF)
        }
    }
}

/** Informe del receptor al emisor, una vez por segundo. */
data class OtStats(val bufferMs: Int, val dropped: Long, val lateFrames: Long) {
    fun encode(): ByteArray = ByteBuffer.allocate(10)
        .putShort(bufferMs.coerceIn(0, 0xFFFF).toShort()).putInt(dropped.toInt()).putInt(lateFrames.toInt()).array()

    companion object {
        fun decode(data: ByteArray): OtStats {
            if (data.size != 10) throw OtProtocolException("informe con largo inválido: ${data.size}")
            val bb = ByteBuffer.wrap(data)
            return OtStats(bb.short.toInt() and 0xFFFF, bb.int.toLong() and 0xFFFFFFFFL, bb.int.toLong() and 0xFFFFFFFFL)
        }
    }
}

data class OtControl(val command: Int, val argument: Long = 0) {
    fun encode(): ByteArray = ByteBuffer.allocate(5).put(command.toByte()).putInt(argument.toInt()).array()

    companion object {
        fun decode(data: ByteArray): OtControl {
            if (data.size != 5) throw OtProtocolException("control con largo inválido: ${data.size}")
            val bb = ByteBuffer.wrap(data)
            return OtControl(bb.get().toInt() and 0xFF, bb.int.toLong() and 0xFFFFFFFFL)
        }
    }
}

data class OtError(val code: Int, val message: String) {
    fun encode(): ByteArray {
        val m = message.toByteArray(Charsets.UTF_8).let { if (it.size > 200) it.copyOf(200) else it }
        return ByteBuffer.allocate(2 + m.size).putShort(code.toShort()).put(m).array()
    }

    companion object {
        fun decode(data: ByteArray): OtError {
            if (data.size < 2) throw OtProtocolException("error con largo inválido")
            return OtError(((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF), String(data, 2, data.size - 2, Charsets.UTF_8))
        }
    }
}

private class Reader(private val data: ByteArray) {
    private var pos = 0
    fun u8(): Int { need(1); return data[pos++].toInt() and 0xFF }
    fun u16(): Int { need(2); return ((data[pos++].toInt() and 0xFF) shl 8) or (data[pos++].toInt() and 0xFF) }
    fun bytes(n: Int): ByteArray { need(n); return data.copyOfRange(pos, pos + n).also { pos += n } }
    private fun need(n: Int) { if (n < 0 || pos + n > data.size) throw OtProtocolException("mensaje truncado") }
}
