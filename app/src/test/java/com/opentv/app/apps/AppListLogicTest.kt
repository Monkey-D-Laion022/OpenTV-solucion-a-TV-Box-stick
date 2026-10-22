package com.opentv.app.apps

import org.junit.Assert.assertEquals
import org.junit.Test

class AppListLogicTest {

    private fun app(label: String, pkg: String = "com.test.${label.lowercase()}") =
        InstalledApp(packageName = pkg, activityName = "$pkg.Main", label = label, screenOrientation = -1)

    private val camera = app("Cámara", "com.cam")
    private val zeta = app("Zeta", "com.zeta")
    private val alpha = app("alpha", "com.alpha")
    private val beta = app("Beta", "com.beta")
    private val all = listOf(zeta, camera, beta, alpha)

    @Test
    fun blankQueryReturnsEverything() {
        assertEquals(all, AppListLogic.filter(all, ""))
        assertEquals(all, AppListLogic.filter(all, "   "))
    }

    @Test
    fun searchIgnoresCaseAndAccents() {
        assertEquals(listOf(camera), AppListLogic.filter(all, "CAMARA"))
        assertEquals(listOf(camera), AppListLogic.filter(all, "cám"))
    }

    @Test
    fun searchAlsoMatchesPackageName() {
        assertEquals(listOf(zeta), AppListLogic.filter(all, "com.zeta"))
    }

    @Test
    fun searchWithoutMatchesIsEmpty() {
        assertEquals(emptyList<InstalledApp>(), AppListLogic.filter(all, "xyz"))
    }

    @Test
    fun sortByNameIsCaseAndAccentInsensitive() {
        assertEquals(
            listOf(alpha, beta, camera, zeta),
            AppListLogic.sort(all, SortMode.NAME_ASC, emptySet()),
        )
        assertEquals(
            listOf(zeta, camera, beta, alpha),
            AppListLogic.sort(all, SortMode.NAME_DESC, emptySet()),
        )
    }

    @Test
    fun favoritesComeFirstThenByName() {
        val sorted = AppListLogic.sort(all, SortMode.FAVORITES_FIRST, setOf("com.zeta", "com.cam"))
        assertEquals(listOf(camera, zeta, alpha, beta), sorted)
    }

    @Test
    fun applyFiltersThenSorts() {
        val result = AppListLogic.apply(all, "a", SortMode.NAME_DESC, emptySet())
        assertEquals(listOf(zeta, camera, beta, alpha), result)
    }

    @Test
    fun emptyListIsHandled() {
        assertEquals(
            emptyList<InstalledApp>(),
            AppListLogic.apply(emptyList(), "x", SortMode.FAVORITES_FIRST, setOf("a")),
        )
    }

    @Test
    fun sortModeCyclesThroughAllValues() {
        var mode = SortMode.FAVORITES_FIRST
        val seen = mutableSetOf<SortMode>()
        repeat(SortMode.values().size) {
            seen += mode
            mode = mode.next()
        }
        assertEquals(SortMode.values().toSet(), seen)
        assertEquals(SortMode.FAVORITES_FIRST, mode)
    }
}
