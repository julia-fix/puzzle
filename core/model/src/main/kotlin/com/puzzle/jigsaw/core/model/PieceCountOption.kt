package com.puzzle.jigsaw.core.model

data class PieceCountOption(
    val rows: Int,
    val columns: Int,
) {
    val totalPieces: Int = rows * columns
    val gridLabel: String = "$columns x $rows"
    val title: String = "$totalPieces pieces"
}
