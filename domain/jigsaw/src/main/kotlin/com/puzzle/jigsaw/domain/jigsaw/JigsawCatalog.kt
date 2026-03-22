package com.puzzle.jigsaw.domain.jigsaw

import com.puzzle.jigsaw.core.model.PieceCountOption

object JigsawCatalog {
    fun pieceCountOptions(): List<PieceCountOption> = listOf(
        PieceCountOption(rows = 4, columns = 3),
        PieceCountOption(rows = 6, columns = 4),
        PieceCountOption(rows = 8, columns = 6),
        PieceCountOption(rows = 9, columns = 7),
        PieceCountOption(rows = 10, columns = 8),
        PieceCountOption(rows = 12, columns = 8),
        PieceCountOption(rows = 12, columns = 9),
        PieceCountOption(rows = 12, columns = 10),
    )
}
