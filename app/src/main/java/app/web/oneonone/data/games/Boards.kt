package app.web.oneonone.data.games

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import kotlinx.serialization.json.*

/** Read-only views of the server's `board` JSON (contract §12). Each returns null when malformed. */

private fun JsonObject.ints(key: String, size: Int? = null): IntArray? =
    (get(key) as? JsonArray)?.map { (it as JsonPrimitive).takeUnless { p -> p.isString }!!.int }?.toIntArray()
        ?.takeIf { size == null || it.size == size }
private fun JsonObject.int(key: String) = (get(key) as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull

/** Square index: a1 = 0 … h1 = 7 … h8 = 63. */
fun squareName(square: Int) = "${'a' + square % 8}${square / 8 + 1}"
fun squareIndex(name: String): Int = (name[1] - '1') * 8 + (name[0] - 'a')

class ChessView(
    val fen: String,
    /** FEN letters: uppercase white, lowercase black, '.' empty. */
    val pieces: CharArray,
    val whiteToMove: Boolean,
    /** Legal moves for the side to move, UCI (`e7e8q`). Display only: the server validates. */
    val legal: List<String>,
    val checkSquare: Int,
    val lastFrom: Int,
    val lastTo: Int,
    val claim: String?,
    /** Every move so far in SAN, from the server (empty for a locally predicted position's new move). */
    val history: List<String> = emptyList(),
) {
    fun movesFrom(square: Int) = legal.filter { squareIndex(it.substring(0, 2)) == square }
    /** Pieces no longer on the board, by colour (promotions can make this approximate). */
    fun captured(white: Boolean): List<Char> = "QRBNP".flatMap { type ->
        val piece = if (white) type else type.lowercaseChar()
        List((Start.getValue(type) - pieces.count { it == piece }).coerceAtLeast(0)) { piece }
    }
    /** Placement + side to move: equal for a predicted position and the server's copy of it (FEN texts may differ). */
    val position: String get() = String(pieces) + if (whiteToMove) "w" else "b"

    /** Material balance in pawns from white's side (P1 N3 B3 R5 Q9). */
    fun material(): Int = pieces.sumOf { Value[it.uppercaseChar()]?.let { v -> if (it.isUpperCase()) v else -v } ?: 0 }

    /**
     * The position after one of [legal]'s moves, computed on the phone so the player's own move shows at
     * once; the server's answer replaces it (and a rejected move simply falls back to this view).
     */
    fun played(uci: String): ChessView? = runCatching {
        val b = Board().apply { loadFromFen(this@ChessView.fen) } // inside apply, a bare `fen` is the new Board's start position
        b.doMove(b.legalMoves().first { it.toString() == uci })
        build(b, squareIndex(uci.substring(0, 2)), squareIndex(uci.substring(2, 4)), null, history)
    }.getOrNull()

    companion object {
        private val Start = mapOf('Q' to 1, 'R' to 2, 'B' to 2, 'N' to 2, 'P' to 8)
        private val Value = mapOf('Q' to 9, 'R' to 5, 'B' to 3, 'N' to 3, 'P' to 1)
        private val squares = Square.values()
        private fun build(b: Board, lastFrom: Int, lastTo: Int, claim: String?, history: List<String>) = ChessView(
            fen = b.fen, pieces = CharArray(64) { i -> b.getPiece(squares[i]).fenSymbol.first() },
            whiteToMove = b.sideToMove == Side.WHITE,
            legal = b.legalMoves().map { it.toString() },
            checkSquare = if (b.isKingAttacked) b.getKingSquare(b.sideToMove).ordinal else -1,
            lastFrom = lastFrom, lastTo = lastTo, claim = claim, history = history,
        )
        fun from(board: JsonObject?): ChessView? = runCatching {
            val fen = (board!!["fen"] as JsonPrimitive).content
            val b = Board().apply { loadFromFen(fen) }
            val last = board["last"] as? JsonObject
            build(b,
                lastFrom = last?.let { squareIndex((it["from"] as JsonPrimitive).content) } ?: -1,
                lastTo = last?.let { squareIndex((it["to"] as JsonPrimitive).content) } ?: -1,
                claim = (board["claim"] as? JsonPrimitive)?.takeIf { it.isString }?.content,
                history = (board["history"] as? JsonArray)?.map { (it as JsonPrimitive).content }.orEmpty(),
            )
        }.getOrNull()
    }
}

/** A chess piece with a stable identity, so the board can slide it between squares. */
data class Sprite(val id: Int, val piece: Char, val square: Int)

/**
 * Carries piece identities across one server move: the mover slides (castling also slides the rook),
 * a captured piece disappears (en passant included) and a promoted pawn changes letter.
 * Falls back to fresh identities when the step is not exactly one move (sync, reconnect, rematch).
 */
fun advanceSprites(previous: List<Sprite>?, next: ChessView, newId: () -> Int): List<Sprite> {
    fun fresh() = next.pieces.withIndex().filter { it.value != '.' }.map { Sprite(newId(), it.value, it.index) }
    if (previous == null || next.lastFrom < 0) return fresh()
    val at = previous.associateBy { it.square }.toMutableMap()
    val mover = at.remove(next.lastFrom) ?: return fresh()
    val to = next.lastTo
    at.remove(to)
    if (mover.piece.uppercaseChar() == 'P' && next.lastFrom % 8 != to % 8 && previous.none { it.square == to }) {
        at.remove(next.lastFrom / 8 * 8 + to % 8) // en passant
    }
    if (mover.piece.uppercaseChar() == 'K' && kotlin.math.abs(to % 8 - next.lastFrom % 8) == 2) {
        val rank = to / 8 * 8
        val (rookFrom, rookTo) = if (to % 8 == 6) rank + 7 to rank + 5 else rank to rank + 3
        at.remove(rookFrom)?.let { at[rookTo] = it.copy(square = rookTo) }
    }
    at[to] = mover.copy(square = to, piece = next.pieces[to])
    val consistent = (0..63).all { sq -> (at[sq]?.piece ?: '.') == next.pieces[sq] }
    return if (consistent) at.values.sortedBy { it.id } else fresh()
}

/** Tic-tac-toe (9 cells) and Connect 4 (42 cells, row 0 at the top). */
class GridView(val cells: IntArray, val line: IntArray, val last: Int) {
    /** This board with [seat]'s mark at [cell] (shown before the server confirms it); unchanged if not empty. */
    fun played(cell: Int, seat: Int): GridView =
        if (cell in cells.indices && cells[cell] == 0) GridView(cells.copyOf().also { it[cell] = seat }, line, cell) else this
    fun landing(col: Int): Int = if (cells.size == 42 && col in 0..6) (5 downTo 0).firstOrNull { cells[it * 7 + col] == 0 }?.let { it * 7 + col } ?: -1 else -1
    companion object {
        fun from(board: JsonObject?, size: Int): GridView? = runCatching {
            val cells = board!!.ints("cells", size)!!
            require(cells.all { it in 0..2 })
            GridView(cells, board.ints("line")!!.filter { it in cells.indices }.toIntArray(), board.int("last") ?: -1)
        }.getOrNull()
    }
}

class DotsView(val horizontal: IntArray, val vertical: IntArray, val boxes: IntArray, val lastHorizontal: Boolean?, val lastIndex: Int) {
    fun score(seat: Int) = boxes.count { it == seat }
    companion object {
        fun from(board: JsonObject?): DotsView? = runCatching {
            val last = board!!["last"] as? JsonObject
            val h = (last?.get("h") as? JsonPrimitive)?.booleanOrNull
            val index = if (last == null || h == null) -1 else last.int("r")!! * (if (h) 5 else 6) + last.int("c")!!
            DotsView(board.ints("h", 30)!!, board.ints("v", 30)!!, board.ints("boxes", 25)!!, h, index)
        }.getOrNull()
    }
}

/** Reversi: 64 cells row-major from the top-left, seat 1 black. [legal] is for the side to move (display only). */
class ReversiView(val cells: IntArray, val last: Int, val flipped: IntArray, val passed: Boolean, val legal: IntArray) {
    fun count(seat: Int) = cells.count { it == seat }
    /** Flip order: discs nearer the new disc turn first, like a wave (0 = adjacent). */
    fun flipRank(cell: Int): Int = if (last < 0) 0 else maxOf(kotlin.math.abs(cell / 8 - last / 8), kotlin.math.abs(cell % 8 - last % 8)) - 1
    companion object {
        fun from(board: JsonObject?): ReversiView? = runCatching {
            val cells = board!!.ints("cells", 64)!!
            require(cells.all { it in 0..2 })
            ReversiView(cells, board.int("last")?.takeIf { it in 0..63 } ?: -1,
                (board.ints("flipped") ?: IntArray(0)).filter { it in 0..63 }.toIntArray(),
                (board["passed"] as? JsonPrimitive)?.booleanOrNull == true,
                (board.ints("legal") ?: IntArray(0)).filter { it in 0..63 && cells[it] == 0 }.toIntArray())
        }.getOrNull()
    }
}

