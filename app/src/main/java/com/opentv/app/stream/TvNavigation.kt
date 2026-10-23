package com.opentv.app.stream

enum class Direction { UP, DOWN, LEFT, RIGHT }

/** Posición del cursor en la interfaz que se ve en el TV: fila y columna dentro de la fila. */
data class TvCursor(val row: Int = 0, val col: Int = 0)

/**
 * Navegación de la interfaz del TV con el control remoto del teléfono. Lógica pura: las filas pueden
 * tener cualquier tamaño (incluso estar vacías) y el cursor nunca queda fuera de rango.
 */
object TvNavigation {

    /** Deja el cursor en una posición válida si cambió el tamaño de las filas. */
    fun normalize(rowSizes: List<Int>, cursor: TvCursor): TvCursor {
        val rows = rowSizes.indices.filter { rowSizes[it] > 0 }
        if (rows.isEmpty()) return TvCursor(0, 0)
        val row = if (cursor.row in rows) cursor.row else rows.minByOrNull { kotlin.math.abs(it - cursor.row) }!!
        return TvCursor(row, cursor.col.coerceIn(0, rowSizes[row] - 1))
    }

    fun move(rowSizes: List<Int>, cursor: TvCursor, direction: Direction): TvCursor {
        val current = normalize(rowSizes, cursor)
        return when (direction) {
            Direction.LEFT -> current.copy(col = (current.col - 1).coerceAtLeast(0))
            Direction.RIGHT -> current.copy(col = (current.col + 1).coerceAtMost(rowSizes[current.row] - 1).coerceAtLeast(0))
            Direction.UP -> verticalStep(rowSizes, current, -1)
            Direction.DOWN -> verticalStep(rowSizes, current, +1)
        }
    }

    /** Índice del primer elemento visible para que [selected] se vea en una ventana de [visible] elementos. */
    fun firstVisible(selected: Int, size: Int, visible: Int): Int =
        (selected - visible + 1).coerceAtLeast(0).coerceAtMost((size - visible).coerceAtLeast(0))

    private fun verticalStep(rowSizes: List<Int>, cursor: TvCursor, step: Int): TvCursor {
        var row = cursor.row + step
        while (row in rowSizes.indices) {
            if (rowSizes[row] > 0) return TvCursor(row, cursor.col.coerceAtMost(rowSizes[row] - 1))
            row += step
        }
        return cursor
    }
}
