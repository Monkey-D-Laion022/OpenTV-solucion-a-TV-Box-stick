package com.opentv.app.link

import android.content.Context
import android.util.Base64
import com.opentv.app.catalog.SecretStore
import com.opentv.app.proto.OtCaps
import com.opentv.app.proto.OtCrypto
import com.opentv.app.proto.OtIdentity
import com.opentv.app.proto.OtPeerStore

/** Equipo ya emparejado. */
data class LinkPeer(val idHex: String, val name: String)

/**
 * Identidad de este teléfono y secretos de los equipos emparejados. Los secretos van cifrados con el Keystore (mismo
 * mecanismo que las claves del catálogo); la lista de nombres, en almacenamiento privado. No se guarda nada en la nube.
 */
class LinkStore(context: Context) : OtPeerStore {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("opentv_link", Context.MODE_PRIVATE)
    private val secrets = SecretStore(app)

    fun identity(caps: Int): OtIdentity {
        var idHex = prefs.getString("device_id", null)
        if (idHex == null || idHex.length != 16) {
            idHex = hex(OtCrypto.randomBytes(8))
            prefs.edit().putString("device_id", idHex).apply()
        }
        val name = prefs.getString("device_name", null) ?: (android.os.Build.MODEL ?: "OpenTV").take(40)
        return OtIdentity(unhex(idHex), name, caps)
    }

    fun senderCaps(): Int = OtCaps.H264 or OtCaps.AAC or OtCaps.MPEG_TS or OtCaps.CONTROL
    fun receiverCaps(): Int = OtCaps.H264 or OtCaps.AAC or OtCaps.MPEG_TS or OtCaps.CONTROL

    override fun secretFor(deviceId: ByteArray): ByteArray? =
        secrets.get("link.secret." + hex(deviceId))?.let { runCatching { Base64.decode(it, Base64.NO_WRAP) }.getOrNull() }

    override fun save(deviceId: ByteArray, name: String, secret: ByteArray) {
        val id = hex(deviceId)
        secrets.put("link.secret.$id", Base64.encodeToString(secret, Base64.NO_WRAP))
        val list = peers().filter { it.idHex != id } + LinkPeer(id, name.replace('|', ' ').replace('\n', ' ').take(40))
        prefs.edit().putString("peers", list.joinToString("\n") { it.idHex + "|" + it.name }).apply()
    }

    fun peers(): List<LinkPeer> = (prefs.getString("peers", "") ?: "").lines().mapNotNull { row ->
        val i = row.indexOf('|')
        if (i != 16) null else LinkPeer(row.substring(0, 16), row.substring(17))
    }

    fun forget(idHex: String) {
        secrets.remove("link.secret.$idHex")
        prefs.edit().putString("peers", peers().filter { it.idHex != idHex }.joinToString("\n") { it.idHex + "|" + it.name }).apply()
    }

    /** Último emisor al que se conectó este receptor (para reconectar sin volver a escribir la dirección). */
    var lastSender: String
        get() = prefs.getString("last_sender", "") ?: ""
        set(value) { prefs.edit().putString("last_sender", value).apply() }

    companion object {
        fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
        fun unhex(s: String) = ByteArray(s.length / 2) { s.substring(2 * it, 2 * it + 2).toInt(16).toByte() }
    }
}
