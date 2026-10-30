package com.opentv.app.catalog

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas de lógica pura del catálogo. TODO el JSON de acá está CONSTRUIDO a mano para las pruebas (no son respuestas
 * reales de TMDB) y las claves son cadenas de relleno sin ningún valor real.
 */
class CatalogLogicTest {
    // Credenciales de relleno (formato válido, sin valor real).
    private val fakeV3 = "0123456789abcdef0123456789abcdef"
    private val fakeV4 = "eyJ" + "a".repeat(40) + "." + "b".repeat(40) + "." + "c".repeat(20)

    // ---- MiniJson ----
    @Test fun jsonParsesNestedValues() {
        val v = MiniJson.asMap(MiniJson.parse("""{"a":[1,2.5,"x\né",true,null],"b":{"c":-3}}"""))!!
        val a = MiniJson.asList(v["a"])!!
        assertEquals(1L, a[0]); assertEquals(2.5, a[1]); assertEquals("x\né", a[2]); assertEquals(true, a[3]); assertNull(a[4])
        assertEquals(-3L, MiniJson.long(MiniJson.asMap(v["b"]), "c"))
    }

    @Test fun jsonRejectsBrokenText() {
        for (bad in listOf("", "{", "[1,", """{"a" 1}""", """{"a":1,}""", "tru", """"abc""", "[1] x", """{"a":"\q"}""", "-")) {
            try { MiniJson.parse(bad); org.junit.Assert.fail("debió fallar: $bad") } catch (e: JsonException) { /* esperado */ }
        }
    }

    @Test fun jsonLimitsDepth() {
        try { MiniJson.parse("[".repeat(200) + "]".repeat(200)); org.junit.Assert.fail() } catch (e: JsonException) { /* esperado */ }
    }

    // ---- TmdbParser ----
    @Test fun parsesConfiguration() {
        val c = TmdbParser.parseConfiguration("""{"images":{"secure_base_url":"https://img.example/t/p/","poster_sizes":["w185","w342","original"],"backdrop_sizes":["w300","w780"]}}""")!!
        assertEquals("https://img.example/t/p/", c.baseUrl)
        assertEquals(listOf("w185", "w342", "original"), c.posterSizes)
        assertNull(TmdbParser.parseConfiguration("""{"images":{"secure_base_url":"http://insecure/"}}"""))
        assertNull(TmdbParser.parseConfiguration("no es json"))
    }

    @Test fun parsesMixedListAndSkipsInvalidEntries() {
        val json = """{"results":[
            {"id":1,"media_type":"movie","title":"Título Uno","overview":"Sinopsis","poster_path":"/p1.jpg","backdrop_path":"/b1.jpg","vote_average":7.84,"release_date":"2024-05-01"},
            {"id":2,"media_type":"tv","name":"Serie Dos","overview":"","poster_path":null,"backdrop_path":"","vote_average":0,"first_air_date":"2023-01-02"},
            {"id":3,"media_type":"person","name":"Alguien"},
            {"id":4,"media_type":"movie","title":"   "},
            {"media_type":"movie","title":"Sin id"},
            "basura"
        ]}"""
        val items = TmdbParser.parseList(json, null)!!
        assertEquals(2, items.size)
        assertEquals(CatalogKind.MOVIE, items[0].kind); assertEquals("2024", items[0].year); assertEquals("/p1.jpg", items[0].posterPath)
        assertEquals(CatalogKind.TV, items[1].kind); assertNull(items[1].posterPath); assertNull(items[1].backdropPath); assertNull(items[1].rating)
        assertEquals("TMDB no tiene sinopsis en español para este título.", CatalogText.overviewOrNote(items[1]))
        assertEquals("Película · 2024 · ★ 7.8", CatalogText.metaLine(items[0]))
    }

    @Test fun defaultKindAppliesWhenMediaTypeIsMissing() {
        val items = TmdbParser.parseList("""{"results":[{"id":9,"title":"X"}]}""", CatalogKind.MOVIE)!!
        assertEquals(CatalogKind.MOVIE, items.single().kind)
        assertTrue(TmdbParser.parseList("""{"results":[{"id":9,"title":"X"}]}""", null)!!.isEmpty())
    }

    @Test fun invalidListIsNullNotEmpty() {
        assertNull(TmdbParser.parseList("""{"status_message":"x"}""", null))
        assertNull(TmdbParser.parseList("<html>", null))
        assertEquals(0, TmdbParser.parseList("""{"results":[]}""", null)!!.size)
    }

    @Test fun imagePathsWithQueryOrSpacesAreRejected() {
        val items = TmdbParser.parseList("""{"results":[{"id":1,"title":"A","poster_path":"/a b.jpg","backdrop_path":"/x.jpg?k=1"}]}""", CatalogKind.MOVIE)!!
        assertNull(items[0].posterPath); assertNull(items[0].backdropPath)
    }

    @Test fun capsTheListAtTwentyItems() {
        val many = (1..30).joinToString(",") { """{"id":$it,"title":"T$it"}""" }
        assertEquals(20, TmdbParser.parseList("""{"results":[$many]}""", CatalogKind.MOVIE)!!.size)
    }

    // ---- Endpoints y credenciales ----
    @Test fun classifiesCredentials() {
        assertEquals(TmdbCredentialKind.API_KEY_V3, TmdbEndpoints.kindOf(fakeV3))
        assertEquals(TmdbCredentialKind.BEARER_TOKEN_V4, TmdbEndpoints.kindOf("  $fakeV4  "))
        for (bad in listOf("", "corta", "con espacios en medio 0123456789", "x".repeat(80))) assertEquals(TmdbCredentialKind.INVALID, TmdbEndpoints.kindOf(bad))
    }

    @Test fun v4TokenGoesInTheHeaderAndNotInTheUrl() {
        val r = TmdbEndpoints.list(CatalogRowKind.MOVIES, fakeV4)!!
        assertFalse(r.url.contains(fakeV4)); assertFalse(r.url.contains("api_key"))
        assertEquals("Bearer $fakeV4", r.headers["Authorization"])
        assertTrue(r.url.startsWith("https://api.themoviedb.org/3/movie/popular?language=es-AR"))
    }

    @Test fun v3KeyGoesInTheQueryAndRequestsNeverPrintTheirKey() {
        val r = TmdbEndpoints.configuration(fakeV3)!!
        assertEquals("https://api.themoviedb.org/3/configuration?api_key=$fakeV3", r.url)
        assertFalse(r.toString().contains(fakeV3))
        assertFalse(RpdbEndpoints.probe("clave-de-prueba").toString().contains("clave-de-prueba"))
        assertNull(TmdbEndpoints.list(CatalogRowKind.SERIES, "mala"))
    }

    @Test fun rpdbUrlsUseTheDocumentedShape() {
        val movie = CatalogItem(603, CatalogKind.MOVIE, "t", "", null, null, null, null)
        val tv = movie.copy(id = 1399, kind = CatalogKind.TV)
        assertEquals("https://api.ratingposterdb.com/K/tmdb/poster-default/movie-603.jpg?fallback=true", RpdbEndpoints.poster("K", movie).url)
        assertTrue(RpdbEndpoints.poster("K", tv).url.contains("/series-1399.jpg"))
        assertEquals("https://api.ratingposterdb.com/K/imdb/poster-default/tt0111161.jpg", RpdbEndpoints.probe("K").url)
    }

    // ---- Tamaños y claves de caché ----
    @Test fun picksTheSmallestSufficientSize() {
        val sizes = listOf("w92", "w185", "w342", "w500", "original")
        assertEquals("w185", ImageSizing.pick(sizes, 150, "w500"))
        assertEquals("w342", ImageSizing.pick(sizes, 186, "w500"))
        assertEquals("w500", ImageSizing.pick(sizes, 4000, "w500")) // nada alcanza: el mayor numérico
        assertEquals("w500", ImageSizing.pick(emptyList(), 100, "w500"))
        assertEquals("https://x/t/p/w342/a.jpg", ImageSizing.url(ImageConfig("https://x/t/p/", sizes, sizes), "/a.jpg", "w342"))
    }

    @Test fun sampleSizeNeverUndershootsTheTarget() {
        assertEquals(1, ImageSizing.sampleSize(500, 400))
        assertEquals(2, ImageSizing.sampleSize(800, 400))
        assertEquals(4, ImageSizing.sampleSize(1700, 400))
        assertEquals(1, ImageSizing.sampleSize(300, 400))
        assertEquals(1, ImageSizing.sampleSize(0, 400))
        for (src in listOf(185, 342, 500, 780, 1280, 3840)) for (t in listOf(100, 300, 640)) {
            assertTrue(src / ImageSizing.sampleSize(src, t) >= minOf(src, t))
        }
    }

    @Test fun cacheKeysAreStableDistinctAndContainNoSeparators() {
        val a = ImageSizing.cacheKey("poster", "movie", "603", "w342")
        assertEquals(a, ImageSizing.cacheKey("poster", "movie", "603", "w342"))
        assertTrue(a != ImageSizing.cacheKey("poster", "movie", "603", "w500"))
        assertTrue(a.all { it in "0123456789abcdef-" } && a.length in 8..17)
    }

    // ---- RpdbPolicy ----
    @Test fun rpdbPolicyStopsOnRejectedKeyOrRepeatedFailures() {
        val p = RpdbPolicy(3)
        p.onFailure(null); p.onFailure(null); p.onFailure(null)
        assertTrue("los errores de red no apagan RPDB", p.isActive())
        p.onFailure(404); p.onFailure(404)
        assertTrue(p.isActive())
        p.onFailure(404)
        assertFalse(p.isActive()); assertNotNull(p.disabledReason)

        val q = RpdbPolicy(3)
        q.onFailure(401)
        assertFalse(q.isActive()); assertTrue(q.disabledReason!!.contains("401"))

        val ok = RpdbPolicy(2)
        ok.onSuccess(); ok.onFailure(404); ok.onFailure(404); ok.onFailure(404)
        assertTrue("con algún éxito previo no se apaga por fallos sueltos", ok.isActive())
        q.reset(); assertTrue(q.isActive())
    }

    // ---- Repositorio con descarga simulada ----
    private fun tmp(): File = java.nio.file.Files.createTempDirectory("opentv-cat").toFile()

    private val configBody = """{"images":{"secure_base_url":"https://img.example/t/p/","poster_sizes":["w342"],"backdrop_sizes":["w780"]}}"""
    private fun listBody(vararg titles: String) = """{"results":[${titles.mapIndexed { i, t -> """{"id":${i + 1},"media_type":"movie","title":"$t"}""" }.joinToString(",")}]}"""

    @Test fun refreshStoresRowsAndSecondCallUsesTheCacheWithoutNetwork() {
        var calls = 0
        var clock = 1_000L
        val repo = CatalogRepository(tmp(), { req, _ ->
            calls++
            FetchResult.Ok((if (req.url.contains("/configuration")) configBody else listBody("Uno", "Dos")).toByteArray())
        }, { clock })
        val first = repo.refresh(fakeV3, force = false)
        assertNull(first.problem); assertEquals(5, calls) // configuración + 4 filas
        assertEquals(4, first.snapshot.rows.size); assertEquals(2, first.snapshot.rows[0].items.size)
        assertNotNull(first.snapshot.config)
        clock += CatalogRepository.ROW_TTL_MS - 1
        repo.refresh(fakeV3, force = false)
        assertEquals("dentro del TTL no se pide nada", 5, calls)
        clock += 2
        repo.refresh(fakeV3, force = false)
        assertEquals("vencidas las filas (no la configuración): 4 pedidos más", 9, calls)
        repo.refresh(fakeV3, force = true)
        assertEquals(14, calls)
    }

    @Test fun networkFailureKeepsSavedDataAndReportsTheProblem() {
        var online = true
        var clock = 0L
        val repo = CatalogRepository(tmp(), { req, _ ->
            if (online) FetchResult.Ok((if (req.url.contains("/configuration")) configBody else listBody("Guardado")).toByteArray())
            else FetchResult.NetworkError("UnknownHostException")
        }, { clock })
        repo.refresh(fakeV3, false)
        online = false
        clock += CatalogRepository.ROW_TTL_MS + 1
        val r = repo.refresh(fakeV3, false)
        assertEquals(CatalogProblem.NETWORK, r.problem)
        assertEquals("Guardado", r.snapshot.rows[0].items[0].title)
    }

    @Test fun rejectedKeyStopsAtTheFirstRequestAndKeepsCache() {
        var calls = 0
        val repo = CatalogRepository(tmp(), { _, _ -> calls++; FetchResult.HttpError(401, null) }, { 5L })
        val r = repo.refresh(fakeV4, true)
        val problem = r.problem // <-- CORREGIDO PARA EVITAR SMART CAST ERROR
        assertEquals(CatalogProblem.REJECTED, problem)
        assertEquals(1, calls)
        assertTrue(r.snapshot.rows.isEmpty())
        assertTrue(problem != null && CatalogText.problem(problem, r.detail).contains("401"))
    }

    @Test fun rateLimitStopsTheLoopWithoutInsisting() {
        var calls = 0
        val repo = CatalogRepository(tmp(), { req, _ ->
            calls++
            if (req.url.contains("/configuration")) FetchResult.Ok(configBody.toByteArray()) else FetchResult.HttpError(429, 30)
        }, { 5L })
        val r = repo.refresh(fakeV3, true)
        assertEquals(CatalogProblem.RATE_LIMITED, r.problem); assertEquals(2, calls)
    }

    @Test fun garbageResponseNeverOverwritesGoodCache() {
        var good = true
        var clock = 0L
        val repo = CatalogRepository(tmp(), { req, _ ->
            val body = when {
                req.url.contains("/configuration") -> configBody
                good -> listBody("Bueno")
                else -> "<html>error</html>"
            }
            FetchResult.Ok(body.toByteArray())
        }, { clock })
        repo.refresh(fakeV3, false)
        good = false
        clock += CatalogRepository.ROW_TTL_MS + 1
        val r = repo.refresh(fakeV3, false)
        assertEquals(CatalogProblem.PARSE, r.problem)
        assertEquals("Bueno", r.snapshot.rows[0].items[0].title)
    }

    @Test fun invalidCredentialFormatDoesNotTouchTheNetwork() {
        var calls = 0
        val repo = CatalogRepository(tmp(), { _, _ -> calls++; FetchResult.NetworkError("x") }, { 1L })
        assertEquals(CatalogProblem.REJECTED, repo.refresh("mala", true).problem); assertEquals(0, calls)
    }

    @Test fun credentialTests() {
        val okRepo = CatalogRepository(tmp(), { _, _ -> FetchResult.Ok(configBody.toByteArray()) }, { 9L })
        assertEquals(true, okRepo.testTmdb(fakeV3).ok)
        assertEquals(false, okRepo.testTmdb("mala").ok)
        val bad = CatalogRepository(tmp(), { _, _ -> FetchResult.HttpError(401, null) }, { 9L })
        assertTrue(bad.testTmdb(fakeV3).message.contains("401")); assertEquals(false, bad.testRpdb("k").ok)
        val jpeg = CatalogRepository(tmp(), { _, _ -> FetchResult.Ok(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3)) }, { 9L })
        assertEquals(true, jpeg.testRpdb("k").ok)
        assertEquals(false, jpeg.testRpdb(" ").ok)
        val notImage = CatalogRepository(tmp(), { _, _ -> FetchResult.Ok("{}".toByteArray()) }, { 9L })
        assertEquals(false, notImage.testRpdb("k").ok)
    }

    @Test fun clearRemovesEverything() {
        val dir = tmp()
        val repo = CatalogRepository(dir, { req, _ -> FetchResult.Ok((if (req.url.contains("/configuration")) configBody else listBody("A")).toByteArray()) }, { 1L })
        repo.refresh(fakeV3, true)
        assertTrue(repo.cached().rows.isNotEmpty())
        repo.clear()
        assertTrue(repo.cached().rows.isEmpty()); assertNull(repo.cached().config)
    }

    // ---- Caché de imágenes en disco ----
    @Test fun diskCacheRoundTripAndTrimsOldestFirst() {
        val dir = tmp()
        val cache = ImageDiskCache(dir, maxBytes = 250)
        cache.write("a", ByteArray(100) { 1 }); Thread.sleep(15)
        cache.write("b", ByteArray(100) { 2 }); Thread.sleep(15)
        cache.write("c", ByteArray(100) { 3 })
        cache.trim()
        assertNull("la más vieja se borra", cache.read("a"))
        assertEquals(100, cache.read("b")!!.size); assertEquals(100, cache.read("c")!!.size)
        assertTrue(cache.sizeBytes() <= 250)
        cache.clear(); assertEquals(0L, cache.sizeBytes())
    }

    // ---- Seguridad: ninguna clave en el código ----
    @Test fun noCredentialLikeStringsInMainSources() {
        val root = File("src/main/java")
        val hex32 = Regex("""["'][0-9a-fA-F]{32}["']""")
        val jwt = Regex("""eyJ[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{10,}""")
        val offenders = root.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .filter { hex32.containsMatchIn(it.readText()) || jwt.containsMatchIn(it.readText()) }.map { it.path }.toList()
        assertTrue("Hay algo con forma de clave en: $offenders", offenders.isEmpty())
    }
}
