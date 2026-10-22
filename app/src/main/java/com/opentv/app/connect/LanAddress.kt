package com.opentv.app.connect

/** Destino de una petición HTTP en la red local. [host] es siempre una IPv4 privada literal. */
data class LanEndpoint(val host: String, val port: Int, val path: String)

/**
 * Validación de direcciones para el cliente de red local. Solo se habla con IPv4 privadas
 * escritas como número (nunca nombres de dominio), para que un equipo malicioso de la red no
 * pueda desviar las peticiones hacia Internet o hacia otros destinos.
 */
object LanAddress {

    private const val MAX_URL_LENGTH = 2048

    /** 10/8, 172.16/12, 192.168/16 y 169.254/16 (enlace local). */
    fun isPrivateIpv4(host: String): Boolean {
        val parts = host.split(".")
        if (parts.size != 4) return false
        val octets = parts.map { part ->
            val valid = part.isNotEmpty() && part.length <= 3 && part.all { it in '0'..'9' } &&
                (part.length == 1 || part[0] != '0')
            if (!valid) return false
            val value = part.toInt()
            if (value > 255) return false
            value
        }
        val (a, b) = octets
        return a == 10 ||
            (a == 172 && b in 16..31) ||
            (a == 192 && b == 168) ||
            (a == 169 && b == 254)
    }

    /** Interpreta `http://IP[:puerto]/ruta`. Devuelve `null` si no cumple todas las reglas. */
    fun parseHttpUrl(url: String): LanEndpoint? {
        if (url.length > MAX_URL_LENGTH || hasUnsafeChars(url)) return null
        if (!url.startsWith("http://", ignoreCase = true)) return null
        val rest = url.substring("http://".length)

        val authorityEnd = rest.indexOfAny(charArrayOf('/', '?', '#')).let { if (it < 0) rest.length else it }
        val authority = rest.substring(0, authorityEnd)
        if (authority.isEmpty() || '@' in authority) return null

        val host: String
        val port: Int
        val colon = authority.indexOf(':')
        if (colon < 0) {
            host = authority
            port = 80
        } else {
            host = authority.substring(0, colon)
            val portText = authority.substring(colon + 1)
            if (portText.isEmpty() || portText.length > 5 || !portText.all { it in '0'..'9' }) return null
            port = portText.toInt()
            if (port !in 1..65535) return null
        }
        if (!isPrivateIpv4(host)) return null

        var path = rest.substring(authorityEnd).substringBefore('#')
        if (path.isEmpty()) path = "/"
        if (path.startsWith("?")) path = "/$path"
        return LanEndpoint(host, port, path)
    }

    /**
     * Resuelve una URL de control publicada por un dispositivo respecto de [base] (la URL de su
     * descripción). Rechaza cualquier destino que no sea el mismo equipo.
     */
    fun resolveAgainst(base: LanEndpoint, reference: String): LanEndpoint? {
        val ref = reference.trim()
        if (ref.isEmpty() || ref.length > MAX_URL_LENGTH || hasUnsafeChars(ref)) return null
        return when {
            ref.startsWith("http://", ignoreCase = true) ->
                parseHttpUrl(ref)?.takeIf { it.host == base.host }
            ref.startsWith("/") -> LanEndpoint(base.host, base.port, ref.substringBefore('#'))
            ref.contains("://") -> null
            else -> {
                val directory = base.path.substringBefore('?').substringBeforeLast('/', "")
                LanEndpoint(base.host, base.port, "$directory/${ref.substringBefore('#')}")
            }
        }
    }

    private fun hasUnsafeChars(text: String): Boolean = text.any { it.code <= 0x20 || it.code == 0x7F }
}

/** Resultado de validar el enlace de video que escribe el usuario. */
sealed interface MediaUrlCheck {
    data class Valid(val url: String) : MediaUrlCheck
    data class Invalid(val reason: String) : MediaUrlCheck
}

object MediaUrlPolicy {

    private const val MAX_LENGTH = 2048

    fun check(input: String): MediaUrlCheck {
        val url = input.trim()
        return when {
            url.isEmpty() -> MediaUrlCheck.Invalid("Escribí el enlace del video.")
            url.length > MAX_LENGTH -> MediaUrlCheck.Invalid("El enlace es demasiado largo.")
            url.any { it.code <= 0x20 || it.code == 0x7F } ->
                MediaUrlCheck.Invalid("El enlace no puede tener espacios ni saltos de línea.")
            !(url.startsWith("http://", true) || url.startsWith("https://", true)) ->
                MediaUrlCheck.Invalid("El enlace debe empezar con http:// o https://.")
            else -> {
                val authority = url.substringAfter("://").substringBefore('/').substringBefore('?')
                when {
                    authority.isEmpty() -> MediaUrlCheck.Invalid("Falta la dirección del servidor.")
                    '@' in authority ->
                        MediaUrlCheck.Invalid("No se admiten enlaces con usuario y contraseña.")
                    else -> MediaUrlCheck.Valid(url)
                }
            }
        }
    }
}
