package com.betterappmafia.apophenia.data

/** A window's place on a Board, in dp from the Board's top left. */
data class BoardRect(val x: Int, val y: Int, val w: Int, val h: Int) {
    val right get() = x + w
    val bottom get() = y + h
    fun overlaps(o: BoardRect) = x < o.right && o.x < right && y < o.bottom && o.y < bottom
}

/**
 * Where new windows go on a Board until the musician moves them (plan 4.3: windows are arranged freely).
 * Christopher, 25 Oct 2026: "loose squares", "not a list". [Claude] A new window is a square that takes the first
 * free spot on a staggered layout (every other column sits a third of a square lower) with a small nudge, so the
 * Board reads as squares laid down by hand rather than rows. It never covers one already placed; windows overlap
 * only if the musician drags them that way.
 */
object BoardLayout {
    const val GAP = 16
    const val MIN_W = 120
    const val MIN_H = 120
    /** Largest default square, so a wide screen gets more columns rather than huge squares. */
    const val MAX_SIDE = 200

    /** Default window side: two columns across a phone, more across anything wider. */
    fun defaultWidth(boardWidth: Int) = ((boardWidth - 3 * GAP) / 2).coerceIn(MIN_W, MAX_SIDE)

    /** The first spot of the staggered layout that overlaps none of [taken]. */
    fun nextFree(taken: List<BoardRect>, boardWidth: Int): BoardRect {
        val side = defaultWidth(boardWidth)
        val cols = ((boardWidth - GAP) / (side + GAP)).coerceAtLeast(1)
        // The nudge depends only on how many windows there are, so the same Board always lays out the same way.
        val n = taken.size
        val nx = (n * 5) % 9 - 4
        val ny = (n * 7) % 11 - 5
        var row = 0
        while (true) {
            for (c in 0 until cols) {
                val x = (GAP + c * (side + GAP) + nx).coerceIn(0, (boardWidth - side).coerceAtLeast(0))
                val y = GAP + row * (side + GAP) + (if (c % 2 == 1) side / 3 else 0) + ny
                val r = BoardRect(x, y.coerceAtLeast(0), side, side)
                if (taken.none { it.overlaps(r) }) return r
            }
            row++
        }
    }

    /**
     * A window still where builds before 0.8.2 put it by default (the two-column grid of 132 dp high cells), so it can
     * be laid out again as a square. Windows the musician moved or resized don't match and stay put.
     */
    fun isOldDefault(r: BoardRect, boardWidth: Int): Boolean {
        val w = ((boardWidth - 24) / 2).coerceAtLeast(MIN_W)
        return r.h == 132 && r.w == w && (r.x == 8 || r.x == 16 + w) && r.y >= 8 && (r.y - 8) % 140 == 0
    }

    /** A dragged or resized window kept on the Board: not past the left or top edge, not wider than the Board, not below the minimum size. */
    fun clamp(r: BoardRect, boardWidth: Int): BoardRect {
        val w = r.w.coerceIn(MIN_W, boardWidth.coerceAtLeast(MIN_W))
        val h = r.h.coerceAtLeast(MIN_H)
        val x = r.x.coerceIn(0, (boardWidth - w).coerceAtLeast(0))
        return BoardRect(x, r.y.coerceAtLeast(0), w, h)
    }
}
