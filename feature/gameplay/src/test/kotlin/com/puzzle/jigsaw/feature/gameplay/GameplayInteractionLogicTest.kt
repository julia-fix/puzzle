package com.puzzle.jigsaw.feature.gameplay

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import com.puzzle.jigsaw.core.model.PieceCountOption
import com.puzzle.jigsaw.core.model.PuzzleImage
import com.puzzle.jigsaw.core.model.PuzzleImageStorage
import com.puzzle.jigsaw.domain.jigsaw.JigsawBoardPosition
import com.puzzle.jigsaw.domain.jigsaw.JigsawPieceLink
import com.puzzle.jigsaw.domain.jigsaw.JigsawSessionState
import com.puzzle.jigsaw.domain.jigsaw.createJigsawPieceLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameplayInteractionLogicTest {
    private val pieceCount = PieceCountOption(rows = 2, columns = 2)
    private val image = PuzzleImage(
        id = "fixture-image",
        title = "Fixture",
        previewPath = "fixture-preview.webp",
        fullImagePath = "fixture.webp",
        storage = PuzzleImageStorage.ASSET,
    )
    private val piecesById = createJigsawPieceLayout(
        rows = pieceCount.rows,
        columns = pieceCount.columns,
    ).associateBy { it.pieceId }
    private val trayLayoutPx = TrayLayoutPx(
        slotHeight = 20f,
        slotWidths = (0 until pieceCount.totalPieces).associateWith { 20f },
        offsets = (0 until pieceCount.totalPieces).associateWith { Offset.Zero },
    )

    @Test
    fun `removeFromTrayRows rebalances after removing bottom row piece`() {
        val rows = removeFromTrayRows(
            orderedPieceIds = listOf(0, 1, 2, 3, 4),
            removedPieceIds = setOf(4),
            rowCount = 2,
            topRowSize = 3,
        )

        assertEquals(listOf(0, 1), rows.top)
        assertEquals(listOf(3, 2), rows.bottom)
    }

    @Test
    fun `reorderTrayRows keeps moved block order while rebalancing`() {
        val rows = reorderTrayRows(
            orderedPieceIds = listOf(0, 1, 2, 3, 4),
            dragState = dragState(
                pieceId = 1,
                clusterPieceIds = setOf(1, 2),
                source = PieceDragSource.TRAY,
                initialPointer = Offset.Zero,
            ),
            dropTarget = TrayDropTarget(rowIndex = 1, indexInRow = 1),
            rowCount = 2,
            topRowSize = 3,
        )

        assertEquals(listOf(0, 4), rows.top)
        assertEquals(listOf(3, 1, 2), rows.bottom)
    }

    @Test
    fun `applyRemainingPieceOrder preserves hidden pieces in original order`() {
        val state = sessionState(
            placedPieceIds = setOf(3),
            boardPiecePositions = mapOf(2 to JigsawBoardPosition(x = 1.2f, y = 0.8f)),
            pieceOrder = listOf(1, 2, 0, 3),
        )

        val reordered = applyRemainingPieceOrder(
            state = state,
            reorderedRemaining = listOf(0, 1),
        )

        assertEquals(listOf(0, 1, 2, 3), reordered.pieceOrder)
    }

    @Test
    fun `resolveGameplayDragFinish moves tray piece onto board and shrinks top tray row`() {
        val result = resolveGameplayDragFinish(
            dragState = dragState(
                pieceId = 0,
                clusterPieceIds = setOf(0),
                source = PieceDragSource.TRAY,
                initialPointer = Offset(60f, 60f),
            ),
            sessionState = sessionState(pieceOrder = listOf(0, 1, 2, 3)),
            boardContentRect = Rect(0f, 0f, 200f, 200f),
            boardCellWidthPx = 100f,
            boardCellHeightPx = 100f,
            trayRowBounds = null,
            trayItemBounds = emptyMap(),
            trayLayoutPx = trayLayoutPx,
            trayRowCount = 2,
            trayTopRowSize = 2,
            trayRowSpacingPx = 4f,
            piecesById = piecesById,
        )

        val committed = result as GameplayDragFinishResult.Committed
        assertEquals(1, committed.trayTopRowSize)
        assertFalse(committed.shouldPlayPlacementFeedback)
        assertEquals(JigsawBoardPosition(x = 0.6f, y = 0.6f), committed.state.boardPiecePositions.getValue(0))
        assertEquals(listOf(1, 2, 3), committed.state.remainingPieceIds)
    }

    @Test
    fun `resolveGameplayDragFinish plays placement feedback when tray piece snaps into place`() {
        val result = resolveGameplayDragFinish(
            dragState = dragState(
                pieceId = 0,
                clusterPieceIds = setOf(0),
                source = PieceDragSource.TRAY,
                initialPointer = Offset.Zero,
            ),
            sessionState = sessionState(pieceOrder = listOf(0, 1, 2, 3)),
            boardContentRect = Rect(0f, 0f, 200f, 200f),
            boardCellWidthPx = 100f,
            boardCellHeightPx = 100f,
            trayRowBounds = null,
            trayItemBounds = emptyMap(),
            trayLayoutPx = trayLayoutPx,
            trayRowCount = 2,
            trayTopRowSize = 2,
            trayRowSpacingPx = 4f,
            piecesById = piecesById,
        )

        val committed = result as GameplayDragFinishResult.Committed
        assertEquals(1, committed.trayTopRowSize)
        assertTrue(committed.shouldPlayPlacementFeedback)
        assertTrue(0 in committed.state.placedPieceIds)
        assertFalse(0 in committed.state.boardPiecePositions)
    }

    @Test
    fun `resolveGameplayDragFinish cancels when linked board cluster is dragged back to tray`() {
        val result = resolveGameplayDragFinish(
            dragState = dragState(
                pieceId = 0,
                clusterPieceIds = setOf(0, 1),
                source = PieceDragSource.BOARD,
                initialPointer = Offset(10f, 10f),
                grabFractionX = 0.5f,
                grabFractionY = 0.5f,
                startPositions = mapOf(0 to Offset.Zero, 1 to Offset(20f, 0f)),
            ),
            sessionState = sessionState(
                boardPiecePositions = mapOf(
                    0 to JigsawBoardPosition(x = 0.6f, y = 0.6f),
                    1 to JigsawBoardPosition(x = 1.6f, y = 0.6f),
                ),
                pieceLinks = setOf(JigsawPieceLink(firstPieceId = 0, secondPieceId = 1)),
                pieceOrder = listOf(0, 1, 2, 3),
            ),
            boardContentRect = Rect(0f, 0f, 200f, 200f),
            boardCellWidthPx = 100f,
            boardCellHeightPx = 100f,
            trayRowBounds = Rect(0f, 0f, 120f, 60f),
            trayItemBounds = emptyMap(),
            trayLayoutPx = trayLayoutPx,
            trayRowCount = 2,
            trayTopRowSize = 2,
            trayRowSpacingPx = 4f,
            piecesById = piecesById,
        )

        assertEquals(GameplayDragFinishResult.Cancelled, result)
    }

    @Test
    fun `resolveGameplayDragFinish returns board piece to empty tray when empty tray area is available`() {
        val result = resolveGameplayDragFinish(
            dragState = dragState(
                pieceId = 0,
                clusterPieceIds = setOf(0),
                source = PieceDragSource.BOARD,
                initialPointer = Offset(40f, 230f),
                grabFractionX = 0.5f,
                grabFractionY = 0.5f,
                startPositions = mapOf(0 to Offset(20f, 20f)),
            ),
            sessionState = sessionState(
                boardPiecePositions = mapOf(
                    0 to JigsawBoardPosition(x = 0.6f, y = 0.6f),
                ),
                pieceOrder = listOf(0, 1, 2, 3),
            ),
            boardContentRect = Rect(0f, 0f, 200f, 200f),
            boardCellWidthPx = 100f,
            boardCellHeightPx = 100f,
            trayRowBounds = Rect(0f, 210f, 200f, 270f),
            trayItemBounds = emptyMap(),
            trayLayoutPx = trayLayoutPx,
            trayRowCount = 1,
            trayTopRowSize = 0,
            trayRowSpacingPx = 4f,
            piecesById = piecesById,
        )

        val committed = result as GameplayDragFinishResult.Committed
        assertTrue(0 in committed.state.remainingPieceIds)
        assertFalse(0 in committed.state.boardPiecePositions)
        assertEquals(committed.state.remainingPieceIds.size, committed.trayTopRowSize)
    }

    private fun sessionState(
        placedPieceIds: Set<Int> = emptySet(),
        boardPiecePositions: Map<Int, JigsawBoardPosition> = emptyMap(),
        pieceLinks: Set<JigsawPieceLink> = emptySet(),
        pieceOrder: List<Int>,
    ): JigsawSessionState = JigsawSessionState(
        image = image,
        pieceCount = pieceCount,
        placedPieceIds = placedPieceIds,
        boardPiecePositions = boardPiecePositions,
        pieceLinks = pieceLinks,
        pieceOrder = pieceOrder,
    )

    private fun dragState(
        pieceId: Int,
        clusterPieceIds: Set<Int>,
        source: PieceDragSource,
        initialPointer: Offset,
        grabFractionX: Float = 0f,
        grabFractionY: Float = 0f,
        startPositions: Map<Int, Offset> = clusterPieceIds.associateWith { Offset.Zero },
    ): PieceDragState = PieceDragState(
        pieceId = pieceId,
        clusterPieceIds = clusterPieceIds,
        source = source,
        initialPointer = initialPointer,
        grabFractionX = grabFractionX,
        grabFractionY = grabFractionY,
        selectedPieceTrayCellWidth = 20.dp,
        selectedPieceTrayCellHeight = 20.dp,
        selectedPieceBoardCellWidth = 20.dp,
        selectedPieceBoardCellHeight = 20.dp,
        selectedPieceTrayPreviewSizePx = SizePx(width = 20f, height = 20f),
        selectedPieceBoardPreviewSizePx = SizePx(width = 20f, height = 20f),
        startPositions = startPositions,
    )
}
