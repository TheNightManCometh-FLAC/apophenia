package com.betterappmafia.apophenia.data

/** A window's place on a Board, in dp from the Board's top left. */
data class BoardRect(val x: Int, val y: Int, val w: Int, val h: Int) {
    val right get() = x + w
    val bottom get() = y + h
    fun overlaps(o: BoardRect) = x < o.right && o.x < right && y < o.bottom && o.y < bottom
}

/**
 * Where new windows go on a Board until the musician moves them (plan 4.3: windows are arranged
 * freely). [Claude] A new window takes the first free spot on a grid of default-sized cells, left
 * to right and top to bottom, so it never covers one already placed. Windows can overlap only if
 * the musician drags them that way.
 */
object BoardLayout {
    const val GAP = 8
    const val MIN_W = 120
    const val MIN_H = 84
    const val DEFAULT_H = 132

    /** Default window width: two columns across a Board [boardWidth] dp wide. */
    fun defaultWidth(boardWidth: Int) = ((boardWidth - 3 * GAP) / 2).coerceAtLeast(MIN_W)

    /** The first cell of the default grid that overlaps none of [taken]. */
    fun nextFree(taken: List<BoardRect>, boardWidth: Int, w: Int = defaultWidth(boardWidth), h: Int = DEFAULT_H): BoardRect {
        val cols = ((boardWidth - GAP) / (w + GAP)).coerceAtLeast(1)
        var row = 0
        while (true) {
            for (c in 0 until cols) {
                val r = BoardRect(GAP + c * (w + GAP), GAP + row * (h + GAP), w, h)
                if (taken.none { it.overlaps(r) }) return r
            }
            row++
        }
    }

    /** A dragged or resized window kept on the Board: not past the left or top edge, not wider than the Board, not below the minimum size. */
    fun clamp(r: BoardRect, boardWidth: Int): BoardRect {
        val w = r.w.coerceIn(MIN_W, boardWidth.coerceAtLeast(MIN_W))
        val h = r.h.coerceAtLeast(MIN_H)
        val x = r.x.coerceIn(0, (boardWidth - w).coerceAtLeast(0))
        return BoardRect(x, r.y.coerceAtLeast(0), w, h)
    }
}
