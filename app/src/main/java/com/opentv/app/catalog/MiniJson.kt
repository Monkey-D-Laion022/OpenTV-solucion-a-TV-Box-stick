package com.opentv.app.catalog

/**
 * Lector JSON mínimo y PURO. Se escribe a mano a propósito: `org.json` de Android no funciona en los tests de JVM del
 * proyecto (son «stubs» que fallan) y agregar una librería exigiría tocar Gradle. Resultado: `Map<String, Any?>`,
 * `List<Any?>`, `String`, `Long`, `Double`, `Boolean` o `null`. Lanza [JsonException] ante cualquier error de formato.
 */
class JsonException(message: String) : Exception(message)

object MiniJson {
    private const val MAX_DEPTH = 40

    fun parse(text: String): Any? {
        val p = Parser(text)
        val v = p.value(0)
        p.skipWhitespace()
        if (p.pos != text.length) throw JsonException("Texto sobrante después del valor")
        return v
    }

    @Suppress("UNCHECKED_CAST")
    fun asMap(v: Any?): Map<String, Any?>? = v as? Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    fun asList(v: Any?): List<Any?>? = v as? List<Any?>

    fun string(map: Map<String, Any?>?, key: String): String? = map?.get(key) as? String

    fun long(map: Map<String, Any?>?, key: String): Long? = when (val v = map?.get(key)) {
        is Long -> v
        is Double -> if (v % 1.0 == 0.0) v.toLong() else null
        else -> null
    }

    fun double(map: Map<String, Any?>?, key: String): Double? = when (val v = map?.get(key)) {
        is Long -> v.toDouble()
        is Double -> v
        else -> null
    }

    private class Parser(private val s: String) {
        var pos = 0

        fun skipWhitespace() {
            while (pos < s.length && (s[pos] == ' ' || s[pos] == '\n' || s[pos] == '\r' || s[pos] == '\t')) pos++
        }

        fun value(depth: Int): Any? {
            if (depth > MAX_DEPTH) throw JsonException("Anidación demasiado profunda")
            skipWhitespace()
            if (pos >= s.length) throw JsonException("Fin inesperado")
            return when (val c = s[pos]) {
                '{' -> obj(depth)
                '[' -> arr(depth)
                '"' -> str()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c in '0'..'9') num() else throw JsonException("Carácter inesperado '$c' en $pos")
            }
        }

        private fun literal(word: String, result: Any?): Any? {
            if (!s.startsWith(word, pos)) throw JsonException("Literal inválido en $pos")
            pos += word.length
            return result
        }

        private fun obj(depth: Int): Map<String, Any?> {
            val out = LinkedHashMap<String, Any?>()
            pos++ // {
            skipWhitespace()
            if (pos < s.length && s[pos] == '}') { pos++; return out }
            while (true) {
                skipWhitespace()
                if (pos >= s.length || s[pos] != '"') throw JsonException("Se esperaba una clave en $pos")
                val key = str()
                skipWhitespace()
                if (pos >= s.length || s[pos] != ':') throw JsonException("Se esperaba ':' en $pos")
                pos++
                out[key] = value(depth + 1)
                skipWhitespace()
                if (pos >= s.length) throw JsonException("Objeto sin cerrar")
                when (s[pos++]) {
                    ',' -> continue
                    '}' -> return out
                    else -> throw JsonException("Se esperaba ',' o '}' en ${pos - 1}")
                }
            }
        }

        private fun arr(depth: Int): List<Any?> {
            val out = ArrayList<Any?>()
            pos++ // [
            skipWhitespace()
            if (pos < s.length && s[pos] == ']') { pos++; return out }
            while (true) {
                out.add(value(depth + 1))
                skipWhitespace()
                if (pos >= s.length) throw JsonException("Lista sin cerrar")
                when (s[pos++]) {
                    ',' -> continue
                    ']' -> return out
                    else -> throw JsonException("Se esperaba ',' o ']' en ${pos - 1}")
                }
            }
        }

        private fun str(): String {
            pos++ // comilla inicial
            val sb = StringBuilder()
            while (true) {
                if (pos >= s.length) throw JsonException("Texto sin cerrar")
                val c = s[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (pos >= s.length) throw JsonException("Escape incompleto")
                        when (val e = s[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > s.length) throw JsonException("Escape \\u incompleto")
                                val code = s.substring(pos, pos + 4).toIntOrNull(16) ?: throw JsonException("Escape \\u inválido")
                                sb.append(code.toChar())
                                pos += 4
                            }
                            else -> throw JsonException("Escape desconocido '\\$e'")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun num(): Any {
            val start = pos
            while (pos < s.length && (s[pos] in '0'..'9' || s[pos] == '-' || s[pos] == '+' || s[pos] == '.' || s[pos] == 'e' || s[pos] == 'E')) pos++
            val text = s.substring(start, pos)
            val isInt = text.none { it == '.' || it == 'e' || it == 'E' }
            if (isInt) text.toLongOrNull()?.let { return it }
            return text.toDoubleOrNull() ?: throw JsonException("Número inválido '$text'")
        }
    }
}
