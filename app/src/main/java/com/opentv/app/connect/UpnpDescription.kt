package com.opentv.app.connect

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.ErrorHandler
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

/** Un televisor o reproductor DLNA (MediaRenderer) con servicio de control de reproducción. */
data class UpnpDevice(
    val udn: String,
    val friendlyName: String,
    val manufacturer: String,
    val modelName: String,
    val descriptionLocation: String,
    val avTransportServiceType: String,
    val controlEndpoint: LanEndpoint,
)

object UpnpDescription {

    const val MAX_XML_CHARS = 262_144
    private const val MAX_NAME_CHARS = 80
    private const val AV_TRANSPORT_PREFIX = "urn:schemas-upnp-org:service:AVTransport:"
    private val SERVICE_TYPE_FORMAT = Regex("[A-Za-z0-9:._-]{1,100}")

    /**
     * Interpreta la descripción XML de un equipo. Devuelve `null` si no es un reproductor DLNA
     * con control de reproducción, si el XML es sospechoso o si su URL de control apunta a otro
     * equipo. Se rechazan los documentos con DOCTYPE/ENTITY (ataques de entidades XML).
     */
    fun parse(xml: String, locationUrl: String): UpnpDevice? {
        val base = LanAddress.parseHttpUrl(locationUrl) ?: return null
        if (xml.length > MAX_XML_CHARS) return null
        if (xml.contains("<!DOCTYPE", ignoreCase = true) || xml.contains("<!ENTITY", ignoreCase = true)) {
            return null
        }

        val document = try {
            val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            builder.setErrorHandler(StrictErrorHandler)
            builder.parse(InputSource(StringReader(xml)))
        } catch (e: Exception) {
            return null
        }

        val devices = document.getElementsByTagName("device")
        val root = devices.item(0) as? Element ?: return null
        val deviceType = root.childText("deviceType") ?: return null
        if (!deviceType.contains(":device:MediaRenderer:")) return null

        val services = root.getElementsByTagName("service")
        for (index in 0 until services.length) {
            val service = services.item(index) as? Element ?: continue
            val serviceType = service.childText("serviceType") ?: continue
            if (!serviceType.startsWith(AV_TRANSPORT_PREFIX) || !SERVICE_TYPE_FORMAT.matches(serviceType)) continue
            val controlUrl = service.childText("controlURL") ?: continue
            val endpoint = LanAddress.resolveAgainst(base, controlUrl) ?: continue

            val model = root.childText("modelName").orEmpty()
            return UpnpDevice(
                udn = root.childText("UDN").orEmpty().ifBlank { locationUrl },
                friendlyName = clean(root.childText("friendlyName")).ifBlank { clean(model) }
                    .ifBlank { "TV sin nombre" },
                manufacturer = clean(root.childText("manufacturer")),
                modelName = clean(model),
                descriptionLocation = locationUrl,
                avTransportServiceType = serviceType,
                controlEndpoint = endpoint,
            )
        }
        return null
    }

    /** Quita caracteres de control y limita el largo: el nombre lo publica el equipo, no es de confianza. */
    private fun clean(text: String?): String =
        text.orEmpty().filter { it.code >= 0x20 && it.code != 0x7F }.trim().take(MAX_NAME_CHARS)

    private fun Element.childText(name: String): String? {
        var child: Node? = firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE && child.nodeName == name) {
                return child.textContent?.trim()?.takeIf { it.isNotEmpty() }
            }
            child = child.nextSibling
        }
        return null
    }

    /** Hace fallar el análisis ante cualquier error, en vez de imprimirlo en consola. */
    private object StrictErrorHandler : ErrorHandler {
        override fun warning(exception: SAXParseException) = Unit
        override fun error(exception: SAXParseException) = throw exception
        override fun fatalError(exception: SAXParseException) = throw exception
    }
}
