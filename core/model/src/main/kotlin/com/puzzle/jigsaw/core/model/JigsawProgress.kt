package com.puzzle.jigsaw.core.model

data class SavedBoardPiecePosition(
    val pieceId: Int,
    val x: Float,
    val y: Float,
)

data class SavedPieceLink(
    val firstPieceId: Int,
    val secondPieceId: Int,
)

data class JigsawProgress(
    val imageId: String,
    val pieceCount: Int,
    val placedPieceIds: Set<Int>,
    val boardPiecePositions: List<SavedBoardPiecePosition> = emptyList(),
    val pieceLinks: Set<SavedPieceLink> = emptySet(),
    val pieceOrder: List<Int> = emptyList(),
    val updatedAtEpochMillis: Long,
) {
    val completionRatio: Float =
        if (pieceCount == 0) 0f else placedPieceIds.size.toFloat() / pieceCount.toFloat()
}

data class RecentPuzzleSession(
    val imageId: String,
    val pieceCount: Int,
    val placedPieces: Int,
    val updatedAtEpochMillis: Long,
) {
    val completionRatio: Float =
        if (pieceCount == 0) 0f else placedPieces.toFloat() / pieceCount.toFloat()
}
