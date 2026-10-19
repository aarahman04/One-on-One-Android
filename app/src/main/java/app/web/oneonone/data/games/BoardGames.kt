package app.web.oneonone.data.games

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import kotlinx.serialization.json.*

enum class GameKind(val wire: String, val title: String, val description: String) {
    Chess("chess", "Chess", "A little strategy, a worthy opponent"),
    Pong("pong", "Table Tennis", "Keep the rally going · first to 7"),
    Connect4("connect4", "Connect 4", "Drop a disc, connect four"),
    TicTacToe("tictactoe", "Tic-Tac-Toe", "Three in a row, one good move"),
    Dots("dots", "Dots & Boxes", "Close a box, take another turn");

    fun engine(): TurnGame = when (this) {
        Chess -> ChessGame()
        Connect4 -> ConnectFourGame()
        TicTacToe -> TicTacToeGame()
        Dots -> DotsGame()
        Pong -> error("Table tennis has a continuous simulation.")
    }
}

data class GameResult(val winner: Int, val detail: String)

/** A single shared sequence for board games. Failed validation never consumes a turn/sequence. */
abstract class TurnGame {
    var turn = 1
        protected set
    var nextSeq = 0
        private set
    var result: GameResult? = null
        protected set
    fun play(player: Int, seq: Int, data: JsonObject) {
        require(result == null) { "This game has finished." }
        require(seq == nextSeq) { "Moves arrived out of order." }
        require(player == turn) { "That move was out of turn." }
        move(data)
        nextSeq++
    }
    protected abstract fun move(data: JsonObject)
}

internal fun JsonObject.integer(key: String): Int {
    val value = get(key) as? JsonPrimitive
    require(value != null && !value.isString) { "Invalid move." }
    return requireNotNull(value.intOrNull) { "Invalid move." }
}

class TicTacToeGame : TurnGame() {
    val cells = IntArray(9)
    var lastCell = -1
        private set
    var winning = IntArray(0)
        private set
    override fun move(data: JsonObject) {
        require(data.keys == setOf("cell")) { "Invalid move." }
        val cell = data.integer("cell")
        require(cell in cells.indices && cells[cell] == 0) { "That cell is occupied." }
        cells[cell] = turn
        lastCell = cell
        winning = Lines.firstOrNull { line -> line.all { cells[it] == turn } } ?: IntArray(0)
        if (winning.isNotEmpty()) result = GameResult(turn, "Three in a row")
        else if (cells.all { it != 0 }) result = GameResult(0, "A well-matched draw")
        turn = 3 - turn
    }
    companion object {
        private val Lines = arrayOf(intArrayOf(0,1,2), intArrayOf(3,4,5), intArrayOf(6,7,8),
            intArrayOf(0,3,6), intArrayOf(1,4,7), intArrayOf(2,5,8), intArrayOf(0,4,8), intArrayOf(2,4,6))
    }
}

class ConnectFourGame : TurnGame() {
    val cells = IntArray(42)
    var lastCell = -1
        private set
    var winning = IntArray(0)
        private set
    fun landing(col: Int): Int = if (col in 0..6) (5 downTo 0).firstOrNull { cells[it * 7 + col] == 0 }?.let { it * 7 + col } ?: -1 else -1
    override fun move(data: JsonObject) {
        require(data.keys == setOf("col")) { "Invalid move." }
        val col = data.integer("col")
        val cell = landing(col)
        require(cell >= 0) { "That column is full." }
        cells[cell] = turn
        lastCell = cell
        for ((dr, dc) in Directions) {
            val line = mutableListOf(cell)
            for (sign in intArrayOf(-1, 1)) {
                var r = cell / 7 + dr * sign
                var c = col + dc * sign
                while (r in 0..5 && c in 0..6 && cells[r * 7 + c] == turn) {
                    line.add(r * 7 + c); r += dr * sign; c += dc * sign
                }
            }
            if (line.size >= 4) { winning = line.toIntArray(); result = GameResult(turn, "Four connected"); break }
        }
        if (result == null && cells.all { it != 0 }) result = GameResult(0, "The board is full")
        turn = 3 - turn
    }
    companion object { private val Directions = listOf(0 to 1, 1 to 0, 1 to 1, 1 to -1) }
}

class DotsGame : TurnGame() {
    val horizontal = IntArray(30)
    val vertical = IntArray(30)
    val boxes = IntArray(25)
    fun score(player: Int) = boxes.count { it == player }
    override fun move(data: JsonObject) {
        require(data.keys == setOf("h", "r", "c")) { "Invalid move." }
        val h = data["h"] as? JsonPrimitive
        require(h != null && !h.isString && h.booleanOrNull != null) { "Invalid line." }
        val horizontalMove = h.boolean
        val r = data.integer("r"); val c = data.integer("c")
        require(r in 0..(if (horizontalMove) 5 else 4) && c in 0..(if (horizontalMove) 4 else 5)) { "Invalid line." }
        val lines = if (horizontalMove) horizontal else vertical
        val index = r * (if (horizontalMove) 5 else 6) + c
        require(lines[index] == 0) { "That line is already taken." }
        lines[index] = turn
        var completed = false
        for (box in boxes.indices) {
            val br = box / 5; val bc = box % 5
            if (boxes[box] == 0 && horizontal[br * 5 + bc] != 0 && horizontal[(br + 1) * 5 + bc] != 0 &&
                vertical[br * 6 + bc] != 0 && vertical[br * 6 + bc + 1] != 0) {
                boxes[box] = turn; completed = true
            }
        }
        if (boxes.all { it != 0 }) result = GameResult(if (score(1) > score(2)) 1 else 2, "${score(1)}–${score(2)} boxes")
        if (!completed) turn = 3 - turn
    }
}

class ChessGame(fen: String? = null) : TurnGame() {
    private val board = Board().apply { if (fen != null) loadFromFen(fen) }
    private val squares = Square.values()
    val pieces = CharArray(64)
    val captured = mutableListOf<Char>()
    var legal = emptyList<String>()
        private set
    var lastMove: String? = null
        private set
    var checkSquare = -1
        private set
    init { refresh() }
    private fun refresh() {
        turn = if (board.sideToMove == Side.WHITE) 1 else 2
        for (i in 0..63) pieces[i] = board.getPiece(squares[i]).fenSymbol.first()
        legal = board.legalMoves().map { it.toString() }
        checkSquare = if (board.isKingAttacked) board.getKingSquare(board.sideToMove).ordinal else -1
        result = when {
            board.isMated -> GameResult(3 - turn, "Checkmate")
            board.isStaleMate -> GameResult(0, "Stalemate")
            board.isRepetition -> GameResult(0, "Draw by repetition")
            board.halfMoveCounter >= 100 -> GameResult(0, "Draw by the 50-move rule")
            board.isInsufficientMaterial -> GameResult(0, "Insufficient material")
            else -> null
        }
    }
    override fun move(data: JsonObject) {
        require(data.keys == setOf("uci")) { "Invalid move." }
        val uci = data["uci"] as? JsonPrimitive
        require(uci != null && uci.isString) { "Invalid chess move." }
        // chesslib.doMove validates the position, not all movement rules; require generated legal membership.
        val move = board.legalMoves().firstOrNull { it.toString() == uci.content }
        require(move != null) { "That chess move is illegal." }
        val piece = board.getPiece(move.to)
        val enPassant = piece == Piece.NONE && board.getPiece(move.from).fenSymbol.equals("p", true) && move.from.file != move.to.file
        check(board.doMove(move, true))
        if (piece != Piece.NONE) captured.add(piece.fenSymbol.first())
        else if (enPassant) captured.add(if (turn == 1) 'p' else 'P')
        lastMove = uci.content
        refresh()
    }
}
