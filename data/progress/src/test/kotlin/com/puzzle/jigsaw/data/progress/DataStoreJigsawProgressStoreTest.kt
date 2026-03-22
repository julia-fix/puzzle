package com.puzzle.jigsaw.data.progress

import com.puzzle.jigsaw.core.model.JigsawProgress
import com.puzzle.jigsaw.core.model.SavedBoardPiecePosition
import com.puzzle.jigsaw.core.model.SavedPieceLink
import org.junit.Assert.assertEquals
import org.junit.Test

class DataStoreJigsawProgressStoreTest {
    @Test
    fun `encode and decode progress payload preserves loose board state`() {
        val progress = JigsawProgress(
            imageId = "image",
            pieceCount = 24,
            placedPieceIds = setOf(0, 4, 7),
            boardPiecePositions = listOf(
                SavedBoardPiecePosition(pieceId = 3, x = 1.25f, y = 0.75f),
                SavedBoardPiecePosition(pieceId = 8, x = 2.25f, y = 1.75f),
            ),
            pieceLinks = setOf(
                SavedPieceLink(firstPieceId = 3, secondPieceId = 8),
            ),
            updatedAtEpochMillis = 99L,
        )

        val decoded = decodeProgressPayloadForTest(encodeProgressPayloadForTest(progress))

        assertEquals(progress.placedPieceIds, decoded.placedPieceIds)
        assertEquals(progress.boardPiecePositions, decoded.boardPiecePositions)
        assertEquals(progress.pieceLinks, decoded.pieceLinks)
    }

    @Test
    fun `decode progress payload supports legacy placed-only format`() {
        val decoded = decodeProgressPayloadForTest("1,4,7")

        assertEquals(setOf(1, 4, 7), decoded.placedPieceIds)
        assertEquals(emptyList<SavedBoardPiecePosition>(), decoded.boardPiecePositions)
        assertEquals(emptySet<SavedPieceLink>(), decoded.pieceLinks)
    }

    @Test
    fun `decode board positions ignores malformed entries`() {
        val decoded = decodeBoardPiecePositions("1,0.5,0.25;bad;2,1.0,1.5")

        assertEquals(
            listOf(
                SavedBoardPiecePosition(pieceId = 1, x = 0.5f, y = 0.25f),
                SavedBoardPiecePosition(pieceId = 2, x = 1.0f, y = 1.5f),
            ),
            decoded,
        )
    }
}
