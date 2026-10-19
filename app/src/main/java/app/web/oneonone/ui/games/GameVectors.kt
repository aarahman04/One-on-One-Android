package app.web.oneonone.ui.games

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Original geometric silhouettes, drawn for One on One; all paint comes from OneTheme. */
internal fun chessVector(piece: Char, paint: Color): ImageVector = ImageVector.Builder(
    name = "Chess ${piece.uppercaseChar()}", defaultWidth = 32.dp, defaultHeight = 32.dp,
    viewportWidth = 32f, viewportHeight = 32f,
).apply {
    path(fill = SolidColor(paint)) {
        moveTo(6f, 27f); lineTo(26f, 27f); lineTo(26f, 30f); lineTo(6f, 30f); close()
        moveTo(9f, 23f); lineTo(23f, 23f); lineTo(25f, 26f); lineTo(7f, 26f); close()
        when (piece.uppercaseChar()) {
            'P' -> {
                moveTo(12f, 22f); lineTo(14f, 14f); curveTo(7f, 11f, 10f, 3f, 16f, 3f)
                curveTo(22f, 3f, 25f, 11f, 18f, 14f); lineTo(20f, 22f); close()
            }
            'R' -> {
                moveTo(10f, 22f); lineTo(11f, 12f); lineTo(8f, 10f); lineTo(8f, 4f)
                lineTo(12f, 4f); lineTo(12f, 8f); lineTo(14f, 8f); lineTo(14f, 4f)
                lineTo(18f, 4f); lineTo(18f, 8f); lineTo(20f, 8f); lineTo(20f, 4f)
                lineTo(24f, 4f); lineTo(24f, 10f); lineTo(21f, 12f); lineTo(22f, 22f); close()
            }
            'N' -> {
                moveTo(9f, 22f); curveTo(9f, 16f, 17f, 16f, 17f, 11f)
                lineTo(11f, 14f); lineTo(7f, 11f); lineTo(14f, 4f); lineTo(14f, 1f)
                lineTo(20f, 4f); curveTo(26f, 8f, 22f, 17f, 23f, 22f); close()
            }
            'B' -> {
                moveTo(11f, 22f); lineTo(14f, 15f); curveTo(6f, 12f, 12f, 6f, 16f, 2f)
                lineTo(18f, 5f); lineTo(14f, 11f); lineTo(16f, 12f); lineTo(20f, 7f)
                curveTo(25f, 13f, 20f, 15f, 18f, 15f); lineTo(21f, 22f); close()
            }
            'Q' -> {
                moveTo(10f, 22f); lineTo(6f, 6f); lineTo(12f, 11f); lineTo(16f, 2f)
                lineTo(20f, 11f); lineTo(26f, 6f); lineTo(22f, 22f); close()
            }
            'K' -> {
                moveTo(14f, 2f); lineTo(18f, 2f); lineTo(18f, 5f); lineTo(22f, 5f)
                lineTo(22f, 8f); lineTo(18f, 8f); lineTo(18f, 11f)
                curveTo(26f, 7f, 27f, 16f, 20f, 18f); lineTo(22f, 22f)
                lineTo(10f, 22f); lineTo(12f, 18f); curveTo(5f, 16f, 6f, 7f, 14f, 11f)
                lineTo(14f, 8f); lineTo(10f, 8f); lineTo(10f, 5f); lineTo(14f, 5f); close()
            }
        }
    }
}.build()

internal fun gameVector(name: String, paint: Color): ImageVector {
    if (name == "chess") return chessVector('N', paint)
    return ImageVector.Builder(name, 32.dp, 32.dp, 32f, 32f).apply {
        path(fill = SolidColor(paint)) {
            when (name) {
                "pong" -> {
                    moveTo(3f, 9f); lineTo(6f, 9f); lineTo(6f, 23f); lineTo(3f, 23f); close()
                    moveTo(26f, 9f); lineTo(29f, 9f); lineTo(29f, 23f); lineTo(26f, 23f); close()
                    moveTo(14f, 13f); lineTo(19f, 13f); lineTo(19f, 18f); lineTo(14f, 18f); close()
                }
                "tictactoe" -> {
                    moveTo(4f, 4f); lineTo(6f, 4f); lineTo(14f, 12f); lineTo(14f, 14f)
                    lineTo(12f, 14f); lineTo(4f, 6f); close()
                    moveTo(12f, 4f); lineTo(14f, 4f); lineTo(14f, 6f); lineTo(6f, 14f)
                    lineTo(4f, 14f); lineTo(4f, 12f); close()
                    moveTo(22f, 17f); curveTo(32f, 17f, 32f, 30f, 22f, 30f)
                    curveTo(12f, 30f, 12f, 17f, 22f, 17f)
                    moveTo(22f, 20f); curveTo(16f, 20f, 16f, 27f, 22f, 27f)
                    curveTo(28f, 27f, 28f, 20f, 22f, 20f)
                }
                "connect4" -> {
                    for (r in 0..2) for (c in 0..2) {
                        val x = 4f + c * 9f; val y = 4f + r * 9f
                        moveTo(x, y); lineTo(x + 6f, y); lineTo(x + 6f, y + 6f); lineTo(x, y + 6f); close()
                    }
                }
                else -> {
                    moveTo(6f, 6f); lineTo(26f, 6f); lineTo(26f, 9f); lineTo(9f, 9f)
                    lineTo(9f, 26f); lineTo(6f, 26f); close()
                    moveTo(23f, 12f); lineTo(26f, 12f); lineTo(26f, 26f); lineTo(12f, 26f)
                    lineTo(12f, 23f); lineTo(23f, 23f); close()
                }
            }
        }
    }.build()
}

internal fun pieceName(piece: Char) = when (piece.uppercaseChar()) {
    'K' -> "king"; 'Q' -> "queen"; 'R' -> "rook"; 'B' -> "bishop"; 'N' -> "knight"; 'P' -> "pawn"; else -> "empty"
}
