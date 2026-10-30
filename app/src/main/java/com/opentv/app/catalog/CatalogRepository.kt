package com.opentv.app.catalog

import java.io.File

typealias Fetch = (CatalogRequest, Int) -> FetchResult

/**
 * Descarga y guarda el catálogo (JSON de TMDB). PURO respecto de Android: recibe la carpeta de caché, la función de
 * descarga y el reloj, por eso se prueba en la JVM. Reglas: las filas se refrescan como máximo cada [ROW_TTL_MS]
 * (salvo `force`), la configuración de imágenes cada [CONFIG_TTL_MS]; si algo falla se conservan los datos guardados
 * y se informa el problema; con 401/403/429 se corta sin insistir. La clave no se guarda en estos archivos.
 */
class CatalogRepository(
    private val dir: File,
    private val fetch: Fetch,
    private val now: () -> Long,
) {
    companion object {
        const val ROW_TTL_MS = 6L * 60 * 60 * 1000
        const val CONFIG_TTL_MS = 7L * 24 * 60 * 60 * 1000
        const val MAX_JSON_BYTES = 1_000_000
        private const val CONFIG_FILE = "configuration.json"
    }

    data class Snapshot(val config: ImageConfig?, val rows: List<CatalogRow>, val updatedAtMillis: Long?)
    data class Refresh(val snapshot: Snapshot, val problem: CatalogProblem?, val detail: String?, val usedNetwork: Boolean)

    private fun rowFile(kind: CatalogRowKind) = "row-${kind.id}.json"

    private fun read(name: String): Pair<Long, String>? = try {
        val f = File(dir, name)
        if (!f.isFile) {
            null
        } else {
            val text = f.readText(Charsets.UTF_8)
            val nl = text.indexOf('\n')
            val at = if (nl > 0) text.substring(0, nl).toLongOrNull() else null
            if (at == null) null else at to text.substring(nl + 1)
        }
    } catch (e: Exception) {
        null
    }

    private fun write(name: String, body: String) {
        try {
            dir.mkdirs()
            val tmp = File(dir, "$name.tmp")
            tmp.writeText("${now()}\n$body", Charsets.UTF_8)
            val dest = File(dir, name)
            if (!tmp.renameTo(dest)) {
                dest.delete()
                tmp.renameTo(dest)
            }
        } catch (e: Exception) {
            // Sin espacio o sin permiso de escritura: el catálogo sigue funcionando en esta sesión, solo no se guarda.
        }
    }

    /** Lo que hay guardado, sin tocar la red. */
    fun cached(): Snapshot {
        val config = read(CONFIG_FILE)?.second?.let(TmdbParser::parseConfiguration)
        var newest: Long? = null
        val rows = CatalogRowKind.values().mapNotNull { kind ->
            val saved = read(rowFile(kind)) ?: return@mapNotNull null
            val items = TmdbParser.parseList(saved.second, kind.defaultKind) ?: return@mapNotNull null
            if (newest == null || saved.first > newest!!) newest = saved.first
            CatalogRow(kind, items)
        }
        return Snapshot(config, rows, newest)
    }

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    fun refresh(credential: String, force: Boolean): Refresh {
        val t = now()
        val needConfig = force || read(CONFIG_FILE)?.first?.let { t - it > CONFIG_TTL_MS || t < it } != false
        val needRows = CatalogRowKind.values().filter { kind ->
            force || read(rowFile(kind))?.first?.let { t - it > ROW_TTL_MS || t < it } != false
        }
        if (!needConfig && needRows.isEmpty()) return Refresh(cached(), null, null, false)

        if (TmdbEndpoints.kindOf(credential) == TmdbCredentialKind.INVALID) {
            return Refresh(cached(), CatalogProblem.REJECTED, "La clave guardada no tiene un formato válido de TMDB.", false)
        }

        var problem: CatalogProblem? = null
        var detail: String? = null
        fun fail(p: CatalogProblem, d: String?) {
            if (problem == null) {
                problem = p
                detail = d
            }
        }
        // Devuelve true si hay que cortar (la clave no sirve o hay límite de uso).
        fun stopOn(r: FetchResult.HttpError): Boolean = when (r.code) {
            401, 403 -> { fail(CatalogProblem.REJECTED, CatalogText.httpError(r.code)); true }
            429 -> { fail(CatalogProblem.RATE_LIMITED, CatalogText.httpError(429)); true }
            else -> { fail(CatalogProblem.SERVER, CatalogText.httpError(r.code)); false }
        }

        if (needConfig) {
            val req = TmdbEndpoints.configuration(credential)!!
            when (val r = fetch(req, MAX_JSON_BYTES)) {
                is FetchResult.Ok -> {
                    val body = String(r.bytes, Charsets.UTF_8)
                    if (TmdbParser.parseConfiguration(body) != null) write(CONFIG_FILE, body) else fail(CatalogProblem.PARSE, null)
                }
                is FetchResult.HttpError -> if (stopOn(r)) return Refresh(cached(), problem, detail, true)
                is FetchResult.NetworkError -> return Refresh(cached(), CatalogProblem.NETWORK, null, true)
            }
        }
        for (kind in needRows) {
            val req = TmdbEndpoints.list(kind, credential)!!
            when (val r = fetch(req, MAX_JSON_BYTES)) {
                is FetchResult.Ok -> {
                    val body = String(r.bytes, Charsets.UTF_8)
                    if (TmdbParser.parseList(body, kind.defaultKind) != null) write(rowFile(kind), body) else fail(CatalogProblem.PARSE, null)
                }
                is FetchResult.HttpError -> if (stopOn(r)) break
                is FetchResult.NetworkError -> {
                    fail(CatalogProblem.NETWORK, null)
                    break
                }
            }
        }
        return Refresh(cached(), problem, detail, true)
    }

    /** Prueba de las credenciales de TMDB: pide la configuración y comprueba que la respuesta sea la esperada. */
    fun testTmdb(credential: String): TestResult {
        val req = TmdbEndpoints.configuration(credential)
            ?: return TestResult(false, "El formato no corresponde a una clave v3 ni a un token v4 de TMDB.", now())
        return when (val r = fetch(req, MAX_JSON_BYTES)) {
            is FetchResult.Ok ->
                if (TmdbParser.parseConfiguration(String(r.bytes, Charsets.UTF_8)) != null) {
                    TestResult(true, "TMDB aceptó la clave.", now())
                } else {
                    TestResult(false, "TMDB respondió algo inesperado.", now())
                }
            is FetchResult.HttpError -> TestResult(false, CatalogText.httpError(r.code), now())
            is FetchResult.NetworkError -> TestResult(false, "Sin conexión con TMDB (${r.reason}).", now())
        }
    }

    /** Prueba de la clave de RPDB pidiendo un póster conocido; se comprueba que lo devuelto sea una imagen JPEG. */
    fun testRpdb(key: String): TestResult {
        if (key.isBlank()) return TestResult(false, "No hay clave de RPDB guardada.", now())
        return when (val r = fetch(RpdbEndpoints.probe(key), 3_000_000)) {
            is FetchResult.Ok ->
                if (r.bytes.size > 2 && r.bytes[0] == 0xFF.toByte() && r.bytes[1] == 0xD8.toByte()) {
                    TestResult(true, "RPDB entregó un póster con esa clave.", now())
                } else {
                    TestResult(false, "RPDB respondió algo que no es una imagen.", now())
                }
            is FetchResult.HttpError ->
                if (r.code == 401 || r.code == 403) {
                    TestResult(false, "RPDB rechazó la clave (${r.code}).", now())
                } else {
                    TestResult(false, "RPDB respondió con el código ${r.code}.", now())
                }
            is FetchResult.NetworkError -> TestResult(false, "Sin conexión con RPDB (${r.reason}).", now())
        }
    }
}
