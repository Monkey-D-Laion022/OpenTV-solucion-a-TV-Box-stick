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
        assertNull(items[0].posterPath); assertNull(items[0].backdropPath
                                                    
