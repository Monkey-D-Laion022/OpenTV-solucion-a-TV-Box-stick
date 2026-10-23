package com.opentv.app.stream

import org.junit.Assert.assertEquals
import org.junit.Test

class TvNavigationTest {

    private val sizes = listOf(5, 1)

    @Test
    fun movesHorizontallyWithinBounds() {
        var c = TvCursor(0, 0)
        c = TvNavigation.move(sizes, c, Direction.LEFT)
        assertEquals(TvCursor(0, 0), c)
        repeat(10) { c = TvNavigation.move(sizes, c, Direction.RIGHT) }
        assertEquals(TvCursor(0, 4), c)
    }

    @Test
    fun verticalMoveClampsColumn() {
        val down = TvNavigation.move(sizes, TvCursor(0, 3), Direction.DOWN)
        assertEquals(TvCursor(1, 0), down)
        assertEquals(TvCursor(1, 0), TvNavigation.move(sizes, down, Direction.DOWN))
        assertEquals(TvCursor(0, 0), TvNavigation.move(sizes, down, Direction.UP))
    }

    @Test
    fun skipsEmptyRows() {
        val withGap = listOf(3, 0, 2)
        assertEquals(TvCursor(2, 1), TvNavigation.move(withGap, TvCursor(0, 1), Direction.DOWN))
        assertEquals(TvCursor(0, 1), TvNavigation.move(withGap, TvCursor(2, 1), Direction.UP))
    }

    @Test
    fun normalizeFixesOutOfRangeAndEmptyRows() {
        assertEquals(TvCursor(0, 1), TvNavigation.normalize(listOf(2, 3), TvCursor(0, 9)).copy(col = 1))
        assertEquals(TvCursor(1, 2), TvNavigation.normalize(listOf(0, 3), TvCursor(0, 9)))
        assertEquals(TvCursor(0, 0), TvNavigation.normalize(listOf(0, 0), TvCursor(1, 4)))
        assertEquals(TvCursor(0, 0), TvNavigation.normalize(emptyList(), TvCursor(3, 3)))
    }

    @Test
    fun movingWithEverythingEmptyDoesNotCrash() {
        Direction.values().forEach {
            assertEquals(TvCursor(0, 0), TvNavigation.move(listOf(0, 0), TvCursor(0, 0), it))
        }
    }
}
